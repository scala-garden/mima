package com.typesafe.tools.mima.core

import scala.annotation.{ nowarn, tailrec }
import scala.collection.mutable

/** A way out of the library for a class no client can name: `via` exposes it, `relation` says how,
 *  and `label` names what to print. `via` itself may only be reachable through another one.
 */
private[mima] final case class EscapeRoute(relation: String, label: String, via: ClassInfo)

sealed class SyntheticPackageInfo(val owner: PackageInfo, val name: String) extends PackageInfo {
  def definitions   = owner.definitions
  lazy val packages = mutable.Map.empty[String, PackageInfo]
  lazy val classes  = Map.empty[String, ClassInfo]
}

@nowarn("msg=under -Xsource:3, inferred") // return types are a bit different between 2 and 3 but it's fine afaics
object NoPackageInfo extends PackageInfo {
  val name        = "<no package>"
  val owner       = this
  def definitions = sys.error("Called definitions on NoPackageInfo")
  val packages    = mutable.Map.empty[String, PackageInfo]
  val classes     = Map.empty[String, ClassInfo]
}

sealed class ConcretePackageInfo(val owner: PackageInfo, cp: ClassPath, pkg: String, defs: Definitions)
    extends PackageInfo {
  def name        = pkg.split('.').last
  def definitions = defs

  lazy val packages: mutable.Map[String, PackageInfo] =
    // this way of building the map cross-compiles on 2.12 and 2.13 without
    // needing to bring in scala-collection-compat
    mutable.Map() ++
      cp.packages(pkg).map { p =>
        p.stripPrefix(s"$pkg.") -> new ConcretePackageInfo(this, cp, p, defs)
      }

  lazy val classes =
    cp.classes(pkg).map { f =>
      val c = new ConcreteClassInfo(this, f)
      c.bytecodeName -> c
    }.toMap
}

final private[core] class DefinitionsPackageInfo(defs: Definitions)
    extends ConcretePackageInfo(NoPackageInfo, defs.classPath, ClassPath.RootPackage, defs)

final private[mima] class DefinitionsTargetPackageInfo(root: PackageInfo, cp: ClassPath,
    override val binaryApi: BinaryApiSpec = BinaryApiSpec.empty)
    extends SyntheticPackageInfo(root, "<root>") {
  // `root` covers the full classpath. Only classes from the compared artifact belong to the target.
  override lazy val classes = cp.classes(ClassPath.RootPackage).map { f =>
    val c = new ConcreteClassInfo(this, f)
    c.bytecodeName -> c
  }.toMap
}

/** Package information, including available classes and packages, and what is accessible. */
sealed abstract class PackageInfo {
  def name: String
  def owner: PackageInfo
  def binaryApi: BinaryApiSpec = BinaryApiSpec.empty
  def definitions: Definitions
  def packages: mutable.Map[String, PackageInfo]
  def classes: Map[String, ClassInfo]

  final def fullName: String = {
    if (isRoot) "<root>"
    else if (owner.isRoot) name
    else s"${owner.fullName}.$name"
  }

  final def isRoot: Boolean = this match {
    case NoPackageInfo                   => true
    case _: DefinitionsPackageInfo       => true
    case _: DefinitionsTargetPackageInfo => true
    case _: ConcretePackageInfo          => false
    case _: SyntheticPackageInfo         => false
  }

  @tailrec
  final def root: PackageInfo = if (isRoot) this else owner.root

  private def classesInTree: Iterator[ClassInfo] =
    classes.valuesIterator ++ packages.valuesIterator.flatMap(_.classesInTree)

  /** What extends a class, directly or through others, for every class in this tree. */
  final lazy val subtypes: collection.Map[ClassInfo, collection.Set[ClassInfo]] = {
    val res = new mutable.HashMap[ClassInfo, mutable.HashSet[ClassInfo]]
    classesInTree.foreach { clazz =>
      (clazz.superClasses.iterator ++ clazz.allInterfaces.iterator).foreach { parent =>
        res.getOrElseUpdate(parent, new mutable.HashSet[ClassInfo]) += clazz
      }
    }
    res
  }

  /** The qualified private classes a client outside their scope can still reach.
   *
   *  A public method returns one, a public field holds one, a public class extends one.
   *  The client never names the type and calls the public members all the same, and it
   *  reaches the public members and nested classes of that class in turn, so this is a
   *  fixed point.
   */
  final def escapedClasses: collection.Set[ClassInfo] = escapeRoutes.keySet

  /** The classes no client can name but every client can reach, each with the way out it takes:
   *  a public signature, a parent, an alias, or an outer class a client can name. */
  final lazy val escapeRoutes: collection.Map[ClassInfo, EscapeRoute] = {
    // Signatures resolve against the full classpath. Map them back to the compared artifact
    // before asking about accessibility, which loads the classfile and its Scala metadata.
    val targetClasses = classesInTree.toList
    val targetByName  = targetClasses.iterator.map(c => c.fullName -> c).toMap
    val escaped       = mutable.Map.empty[ClassInfo, EscapeRoute]
    val queue         = mutable.Queue.empty[ClassInfo]

    def enqueue(clazz: ClassInfo, route: => EscapeRoute): Unit = if (clazz != NoClass) {
      targetByName.get(clazz.fullName).foreach { target =>
        if (!target.isExternallyAccessible && !escaped.contains(target)) {
          escaped(target) = route
          queue.enqueue(target)
        }
      }
    }
    def enqueueAll(classes: Iterator[ClassInfo], route: => EscapeRoute): Unit =
      classes.foreach(enqueue(_, route))

    def exposes(clazz: ClassInfo): Unit = {
      (clazz.methods.value.iterator ++ clazz.fields.value.iterator).foreach { m =>
        if (!m.nonAccessible) {
          enqueueAll(m.tpe.classes, EscapeRoute("through", m.fullName, clazz))
          enqueueAll(
            m.signature.classNames.map(clazz.owner.definitions.fromName),
            EscapeRoute("through", m.fullName, clazz))
        }
      }
      // parents first: a parent with type arguments is in the signature too, and reads better as one
      enqueue(clazz.superClass, EscapeRoute("as a parent of", clazz.description, clazz))
      enqueueAll(clazz.interfaces.iterator, EscapeRoute("as a parent of", clazz.description, clazz))
      enqueueAll(
        clazz.signature.classNames.map(clazz.owner.definitions.fromName),
        EscapeRoute("through the signature of", clazz.description, clazz))
      enqueueAll(
        clazz.aliases.iterator.map(clazz.owner.definitions.fromAliasName),
        EscapeRoute("as an alias in", clazz.description, clazz))
      // a client reaches a nested class through its outer only if it can name the nested one
      clazz.innerClasses.foreach(clazz.owner.classes.get(_).filter(_.isDirectlyAccessible)
        .foreach(enqueue(_, EscapeRoute("as a nested class of", clazz.description, clazz))))
    }

    targetClasses.foreach { c => if (c.isExternallyAccessible) exposes(c) }
    while (queue.nonEmpty) exposes(queue.dequeue())

    escaped
  }

  final lazy val accessibleClasses: Set[ClassInfo] = {
    val found = Set.newBuilder[ClassInfo]

    val allAccessibleClasses = classes.valuesIterator.filter { clazz =>
      clazz.isChecked && !clazz.isLocalClass && !clazz.isBytecodeSynthetic
    }.toSet

    @tailrec def loop(isReachable: ClassInfo => Boolean): Set[ClassInfo] = {
      val reachableClasses = allAccessibleClasses.filter(isReachable)
      if (reachableClasses.isEmpty) found.result()
      else {
        found ++= reachableClasses
        loop(clazz => reachableClasses.exists(_.innerClasses.contains(clazz.bytecodeName)))
      }
    }

    loop(clazz => clazz.isTopLevel && !clazz.decodedName.contains("$$"))
  }

  // TODO: Foo contains pickle, so if parse Foo$ before should be able to set this then
  final lazy val linkModuleClasses: Unit = {
    for {
      (name, moduleClass) <- classes.iterator
      if moduleClass.isModuleClass
      companion <- classes.get(name.init)
    } {
      moduleClass._companionClass = companion
      companion._moduleClass = moduleClass
    }
  }

  final override def toString = s"package $fullName"
}
