// scalafmt: { maxColumn = 150 }
package com.typesafe.tools.mima.core

import scala.reflect.NameTransformer

import com.typesafe.tools.mima.core.util.log.ConsoleLogging

private[core] object ClassInfo {
  def formatClassName(str: String) = NameTransformer.decode(str).replace('$', '#')

  /** We assume there can be only one java.lang.Object class,
   *  and that comes from the configuration class path.
   */
  lazy val ObjectClass = new Definitions(ClassPath.base).ObjectClass
}

/** A placeholder class info for a class that is not found on the classpath or in a given package. */
private[core] sealed class SyntheticClassInfo(owner: PackageInfo, val bytecodeName: String) extends ClassInfo(owner) {
  final protected def afterLoading[A](x: => A): A = x

  override lazy val superClassChain     = List(ClassInfo.ObjectClass)
  final override lazy val allTraits     = Set.empty[ClassInfo]
  final override lazy val allInterfaces = Set.empty[ClassInfo]

  override def canEqual(other: Any) = other.isInstanceOf[SyntheticClassInfo]
}

private[core] object NoClass extends SyntheticClassInfo(NoPackageInfo, "<noclass>") {
  override lazy val superClassChain = Nil

  override def canEqual(other: Any) = other.isInstanceOf[NoClass.type]
}

/** A class for which we have the classfile. */
private[core] final class ConcreteClassInfo(owner: PackageInfo, val file: AbsFile) extends ClassInfo(owner) {
  def bytecodeName                  = file.name.stripSuffix(".class")
  override def canEqual(other: Any) = other.isInstanceOf[ConcreteClassInfo]

  private var loaded: Boolean = false

  protected def afterLoading[A](x: => A) = {
    if (!loaded) {
      loaded = true
      ConsoleLogging.verbose(s"parsing $file")
      ClassfileParser.parseInPlace(this, file)
    }
    x
  }
}

private[mima] sealed abstract class ClassInfo(val owner: PackageInfo) extends InfoLike with Equals {
  import ClassInfo._

  final var _innerClasses: Seq[String]    = Nil
  final var _isLocalClass: Boolean        = false
  final var _isTopLevel: Boolean          = true
  final var _superClass: ClassInfo        = NoClass
  final var _interfaces: List[ClassInfo]  = Nil
  final var _fields: Members[FieldInfo]   = NoMembers
  final var _methods: Members[MethodInfo] = NoMembers
  final var _flags: Int                   = 0
  final var _signature: Signature         = Signature.none
  final var _aliases: List[String]        = Nil
  final var _scopedPrivate: Boolean       = false
  final var _sealed: Boolean              = false
  final var _annotations: List[AnnotInfo] = Nil
  final var _implClass: ClassInfo         = NoClass
  final var _moduleClass: ClassInfo       = NoClass
  final var _module: ClassInfo            = NoClass

  protected def afterLoading[A](x: => A): A

  final def forceLoad: this.type         = afterLoading(this)
  final def innerClasses: Seq[String]    = afterLoading(_innerClasses)
  final def isLocalClass: Boolean        = afterLoading(_isLocalClass)
  final def isTopLevel: Boolean          = afterLoading(_isTopLevel)
  final def superClass: ClassInfo        = afterLoading(_superClass)
  final def interfaces: List[ClassInfo]  = afterLoading(_interfaces)
  final def fields: Members[FieldInfo]   = afterLoading(_fields)
  final def methods: Members[MethodInfo] = afterLoading(_methods)
  final def flags: Int                   = afterLoading(_flags)
  final def signature: Signature         = afterLoading(_signature)
  final def aliases: List[String]        = afterLoading(_aliases)
  // the private[foo] mark is only in the pickle, in one of the two classfiles of this class or of an enclosing object
  final def isScopedPrivate: Boolean     = { loadOuterChainModules(); afterLoading(_scopedPrivate) }
  final def isSealed: Boolean            = afterLoading(_sealed)
  final def annotations: List[AnnotInfo] = afterLoading(_annotations)
  final def implClass: ClassInfo         = { owner.setImplClasses; _implClass } // returns NoClass if this is not a trait
  final def moduleClass: ClassInfo       = { owner.setModules; if (_moduleClass == NoClass || _moduleClass == null) this else _moduleClass }
  final def module: ClassInfo            = { owner.setModules; if (_module == NoClass || _module == null) this else _module }

  final def isTrait: Boolean          = implClass ne NoClass               // trait with some concrete methods or fields
  final def isModuleClass: Boolean    = bytecodeName.endsWith("$")         // super scuffed
  final def isImplClass: Boolean      = bytecodeName.endsWith("$class")
  final def isInterface: Boolean      = ClassfileParser.isInterface(flags) // java interface or trait w/o impl methods
  final def isClass: Boolean          = !isTrait && !isInterface           // class, object or trait's impl class
  final def scopedPrivateSuff: String = if (isScopedPrivate) "[..]" else ""
  final def accessModifier: String    = if (isProtected) s"protected$scopedPrivateSuff" else if (isPrivate) s"private$scopedPrivateSuff" else ""
  final def declarationPrefix: String = if (isModuleClass) "object" else if (isTrait) "trait" else if (isInterface) "interface" else "class"
  final lazy val fullName: String     = if (owner.isRoot) bytecodeName else s"${owner.fullName}.$bytecodeName"
  final def formattedFullName: String = formatClassName(if (isModuleClass) fullName.init else fullName)
  final def description: String       = s"$declarationPrefix $formattedFullName"
  final def classString: String       = s"$accessModifier $description".trim

  private var _outerChainModulesLoaded: Boolean = this == NoClass
  @scala.annotation.tailrec
  private def loadOuterChainModules(): Unit =
    if (!_outerChainModulesLoaded) { _outerChainModulesLoaded = true; module.forceLoad; moduleClass.forceLoad; outer.loadOuterChainModules() }

  def outerChain: Iterator[ClassInfo] = Iterator.iterate(this)(_.outer).takeWhile(_ != NoClass)

  /** Nothing outside this library can extend it. */
  private[mima] def isClosed: Boolean = isFinal || isSealed || !isExternallyAccessible

  /** No client can extend this class, and none can extend its subtypes.
   *
   *  This tests isSealed, not isClosed. A final class has no deferred methods, so
   *  MethodChecker.checkNew reports nothing about it. mima also never builds
   *  PackageInfo.subtypes for a library that seals nothing. */
  private[mima] def isClosedHierarchy: Boolean = isSealed &&
    owner.root.subtypes.getOrElse(this, Set.empty).forall(_.isClosed)

  private[mima] def isDirectlyAccessible: Boolean = isPublic && !isScopedPrivate

  private[mima] lazy val isExternallyAccessible: Boolean = isDirectlyAccessible && (outer == NoClass || outer.isExternallyAccessible)

  /** Whether mima checks this class: a client outside the scope can name it, or holds
   *  one all the same because a public method returns it. */
  private[mima] def isChecked: Boolean =
    isExternallyAccessible || outerChain.exists(owner.root.escapedClasses)

  lazy val outer: ClassInfo = {
    val idx = bytecodeName.stripSuffix("$").lastIndexOf('$')
    if (idx != -1) {
      val outerName = bytecodeName.substring(0, idx)
      owner.classes.getOrElse(outerName, owner.classes.getOrElse(s"$outerName$$", NoClass))
    } else NoClass
  }

  /** Nearest superclass first, so that a lookup walking it finds an override
   *  before the method it overrides. */
  lazy val superClassChain: List[ClassInfo] = {
    if (this == ClassInfo.ObjectClass) Nil
    else superClass :: superClass.superClassChain
  }

  lazy val superClasses: Set[ClassInfo] = superClassChain.toSet

  private def thisAndSuperClasses = Iterator.single(this) ++ superClassChain.iterator

  final def lookupClassFields(field: FieldInfo): Iterator[FieldInfo] =
    thisAndSuperClasses.flatMap(_.fields.get(field.bytecodeName))

  final def lookupClassMethods(method: MethodInfo): Iterator[MethodInfo] = {
    val name = method.bytecodeName
    if (name == MemberInfo.ConstructorName) methods.get(name) // constructors are not inherited
    else if (method.isStatic) methods.get(name)               // static methods are not inherited
    else thisAndSuperClasses.flatMap(_.methods.get(name))
  }

  private def lookupInterfaceMethods(method: MethodInfo): Iterator[MethodInfo] =
    if (method.isStatic) Iterator.empty // static methods are not inherited
    else allInterfaces.iterator.flatMap(_.methods.get(method.bytecodeName))

  final def lookupMethods(method: MethodInfo): Iterator[MethodInfo] =
    lookupClassMethods(method) ++ lookupInterfaceMethods(method)

  final def lookupConcreteTraitMethods(method: MethodInfo): Iterator[MethodInfo] =
    allTraits.iterator.flatMap(_.concreteMethods).filter(_.bytecodeName == method.bytecodeName)

  /** The concrete methods of this trait. */
  final lazy val concreteMethods: List[MethodInfo] = {
    if (isTrait) methods.value.filter(m => hasStaticImpl(m) || m.isConcrete)
    else methods.value.filter(_.isConcrete)
  }

  /** The subset of concrete methods of this trait that are abstract at the JVM level.
    * This corresponds to the pre-Scala-2.12 trait encoding where all `concreteMethods`
    * are `emulatedConcreteMethods`. In 2.12 most concrete trait methods are translated
    * to concrete interface methods.
    */
  final lazy val emulatedConcreteMethods: List[MethodInfo] = concreteMethods.filter(_.isDeferred)

  /** The deferred methods of this trait. */
  final lazy val deferredMethods: List[MethodInfo] = {
    val concreteMethods = this.concreteMethods.toSet
    methods.value.filterNot(concreteMethods)
  }

  /** All deferred methods of this type as seen in the bytecode. */
  final def deferredMethodsInBytecode: List[MethodInfo] = if (isTrait) methods.value else deferredMethods

  /** The inherited traits in the linearization of this class or trait,
   *  except any traits inherited by its superclass.
   *  Traits appear in linearization order of this class or trait.
   */
  final lazy val directTraits: List[ClassInfo] = {
    val superClassTraits = superClass.allTraits

    // All traits in the transitive, reflexive inheritance closure of the given trait.
    def traitClosure(t: ClassInfo): List[ClassInfo] = {
      if (superClassTraits.contains(t)) Nil
      // traits with only abstract methods are presented as interfaces,
      // but nonetheless they should still be collected
      else if (t.isInterface) parentsClosure(t) :+ t
      else parentsClosure(t)
    }

    def parentsClosure(c: ClassInfo) = c.interfaces.flatMap(traitClosure).distinct

    parentsClosure(this)
  }

  /** All traits inherited directly or indirectly by this class. */
  lazy val allTraits: Set[ClassInfo] = {
    if (this == ClassInfo.ObjectClass) Set.empty
    else superClass.allTraits ++ directTraits
  }

  /** All interfaces inherited directly or indirectly by this class. */
  lazy val allInterfaces: Set[ClassInfo] = {
    if (this == ClassInfo.ObjectClass) Set.empty
    else superClass.allInterfaces ++ interfaces ++ interfaces.flatMap(_.allInterfaces)
  }

  /** Does this class's impl class have a static implementation of the given method `m`? */
  final def hasStaticImpl(m: MethodInfo): Boolean = {
    implClass match {
      case _: SyntheticClassInfo   => false
      case impl: ConcreteClassInfo =>
        assert(impl.isImplClass, impl)
        impl.methods.get(m.bytecodeName).exists { im =>
          val isig = im.descriptor
          val tsig = m.descriptor
          assert(isig(0) == '(' && isig(1) == 'L' && tsig(0) == '(', s"isig=[$isig] tsig=[$tsig]")
          hasMatchingSig(isig, tsig)
        }
    }
  }

  /** Does the given method have a static mixin forwarder? */
  final def hasMixinForwarder(m: MethodInfo): Boolean = {
    methods.get(m.bytecodeName + "$").exists { fm =>
      val fsig = fm.descriptor
      val tsig = m.descriptor
      assert(fsig(0) == '(' && tsig(0) == '(', s"fsig=[$fsig] tsig=[$tsig]")
      hasMatchingSig(fsig, tsig)
    }
  }

  // Does `sig` correspond to `tsig` if seen as the signature of the static
  // implementation method or the mixin forwarder method of a trait method with signature `tsig`?
  private def hasMatchingSig(sig: String, tsig: String): Boolean = {
    val ilen = sig.length
    val tlen = tsig.length
    var i    = 2
    while (sig(i) != ';')
      i += 1
    i += 1
    var j = 1
    while (i < ilen && j < tlen && sig(i) == tsig(j)) {
      i += 1
      j += 1
    }
    i == ilen && j == tlen
  }

  def canEqual(other: Any)              = other.isInstanceOf[ClassInfo]
  final override def equals(other: Any) = other match {
    case that: ClassInfo => that.canEqual(this) && fullName == that.fullName
    case _               => false
  }
  final override def hashCode = fullName.hashCode
  final override def toString = s"class $bytecodeName"
}
