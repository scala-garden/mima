package com.typesafe.tools.mima.core

import scala.annotation.{ nowarn, tailrec }
import scala.collection.mutable

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

final private[mima] class DefinitionsTargetPackageInfo(root: PackageInfo)
    extends SyntheticPackageInfo(root, "<root>") {
  // Needed to fetch classes located in the root (empty package).
  override lazy val classes = root.classes
}

/** Package information, including available classes and packages, and what is accessible. */
sealed abstract class PackageInfo {
  def name: String
  def owner: PackageInfo
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
  final lazy val escapedClasses: collection.Set[ClassInfo] = {
    val escaped = mutable.Set.empty[ClassInfo]
    val queue   = mutable.Queue.empty[ClassInfo]

    def enqueue(clazz: ClassInfo): Unit =
      if (clazz != NoClass && !clazz.isExternallyAccessible && escaped.add(clazz)) queue.enqueue(clazz)
    def enqueueAll(classes: Iterator[ClassInfo]): Unit =
      classes.foreach(enqueue)

    def exposes(clazz: ClassInfo): Unit = {
      (clazz.methods.value.iterator ++ clazz.fields.value.iterator).foreach { m =>
        if (!m.nonAccessible) {
          enqueueAll(m.tpe.classes)
          enqueueAll(m.signature.classNames.map(clazz.owner.definitions.fromName))
        }
      }
      enqueueAll(clazz.signature.classNames.map(clazz.owner.definitions.fromName))
      enqueueAll(clazz.aliases.iterator.map(clazz.owner.definitions.fromAliasName))
      enqueue(clazz.superClass)
      enqueueAll(clazz.interfaces.iterator)
      clazz.innerClasses.foreach(clazz.owner.classes.get(_).foreach(enqueue))
    }

    classesInTree.foreach { c => if (c.isExternallyAccessible) exposes(c) }
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
