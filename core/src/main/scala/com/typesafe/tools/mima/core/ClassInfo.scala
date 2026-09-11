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

  final var _innerClasses: Seq[String]             = Nil
  final var _isLocalClass: Boolean                 = false
  final var _isTopLevel: Boolean                   = true
  final var _superClass: ClassInfo                 = NoClass
  final var _interfaces: List[ClassInfo]           = Nil
  final var _fields: Members[FieldInfo]            = NoMembers
  final var _methods: Members[MethodInfo]          = NoMembers
  final var _flags: Int                            = 0
  final var _signature: Signature                  = Signature.none
  final var _aliases: List[String]                 = Nil
  final var _scopedPrivate: Boolean                = false
  final var _private: Boolean                      = false
  final var _isScala: Boolean                      = false
  final var _sealed: Boolean                       = false
  final var _annotations: List[AnnotInfo]          = Nil
  final var _privateInBytecode: Set[(String, Int)] = Set.empty
  final var _moduleClass: ClassInfo                = NoClass
  final var _companionClass: ClassInfo             = NoClass

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
  // `private[p] class C`, and a nested `private class C`, are ACC_PUBLIC in bytecode
  // so this information is read from the pickle, which can sit in a companion or enclosing classfile, so force them
  final def isScopedPrivate: Boolean     = { loadOuterChainModules(); afterLoading(_scopedPrivate) }
  final def isPrivate: Boolean           = { loadOuterChainModules(); afterLoading(_private) }
  final def isSealed: Boolean            = afterLoading(_sealed)
  final def isScala: Boolean             = afterLoading(_isScala)
  final def annotations: List[AnnotInfo] = afterLoading(_annotations)
  /** Name and parameter count of each method the bytecode keeps private. mima drops those,
   *  so for such a pair `methods` holds fewer than the class declares. */
  final def privateInBytecode: Set[(String, Int)] = afterLoading(_privateInBytecode)
  /** For a plain C, the C$ holding the members of `object C`; NoClass if there is no such object. */
  // null while NoClass itself is under construction, since these initialise to it
  final def moduleClass: ClassInfo = { owner.linkModuleClasses; if (_moduleClass == null) NoClass else _moduleClass }
  /** For a C$, the plain C beside it, which holds the static forwarders; NoClass if there is none. */
  final def companionClass: ClassInfo = { owner.linkModuleClasses; if (_companionClass == null) NoClass else _companionClass }

  final def isModuleClass: Boolean      = bytecodeName.endsWith("$")         // super scuffed
  final def isTraitOrInterface: Boolean = ClassfileParser.isInterface(flags) // java interface or trait
  final def isClass: Boolean            = !isTraitOrInterface                // class or object
  // a class is only ever private in the pickle: the bytecode has no bit for it
  final def accessModifier: String =
    if (isScopedPrivate) "private[..]" else if (isPrivate) "private" else ""
  final def declarationPrefix: String =
    if (isModuleClass) "object" else if (!isTraitOrInterface) "class" else if (isScala) "trait" else "interface"
  final lazy val fullName: String     = if (owner.isRoot) bytecodeName else s"${owner.fullName}.$bytecodeName"
  final def formattedFullName: String = formatClassName(if (isModuleClass) fullName.init else fullName)
  final def description: String       = s"$declarationPrefix $formattedFullName"
  final def classString: String       = s"$accessModifier $description".trim

  private var _outerChainModulesLoaded: Boolean = this == NoClass
  @scala.annotation.tailrec
  private def loadOuterChainModules(): Unit =
    if (!_outerChainModulesLoaded) {
      // the private[p] mark can come from the pickle of any of the three, whichever carries it
      _outerChainModulesLoaded = true
      forceLoad
      companionClass.forceLoad
      moduleClass.forceLoad
      outer.loadOuterChainModules()
    }

  def outerChain: Iterator[ClassInfo] = Iterator.iterate(this)(_.outer).takeWhile(_ != NoClass)

  /** Nothing outside this library can extend it. */
  private[mima] def isClosed: Boolean = isBytecodeFinal || isSealed || !isExternallyAccessible

  /** No client can extend this class, and none can extend its subtypes.
   *
   *  This tests isSealed, not isClosed. A final class has no deferred methods, so
   *  MethodChecker.checkNew reports nothing about it. mima also never builds
   *  PackageInfo.subtypes for a library that seals nothing. */
  private[mima] def isClosedHierarchy: Boolean = isSealed &&
    owner.root.subtypes.getOrElse(this, Set.empty).forall(_.isClosed)

  private[mima] def isDirectlyAccessible: Boolean = isBytecodePublic && !isScopedPrivate && !isPrivate

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
    else if (method.isBytecodeStatic) methods.get(name)       // static methods are not inherited
    else thisAndSuperClasses.flatMap(_.methods.get(name))
  }

  private def lookupInterfaceMethods(method: MethodInfo): Iterator[MethodInfo] =
    if (method.isBytecodeStatic) Iterator.empty // static methods are not inherited
    else allInterfaces.iterator.flatMap(_.methods.get(method.bytecodeName))

  final def lookupMethods(method: MethodInfo): Iterator[MethodInfo] =
    lookupClassMethods(method) ++ lookupInterfaceMethods(method)

  /** The default methods a class inherits: since Java 8 an invokevirtual resolves through the
   *  superinterfaces, so these stand in for a method the class does not declare itself. */
  final def lookupConcreteInterfaceMethods(method: MethodInfo): Iterator[MethodInfo] =
    if (method.isBytecodeStatic) Iterator.empty // static interface methods are not inherited
    else allInterfaces.iterator.flatMap(_.concreteMethods).filter(_.bytecodeName == method.bytecodeName)

  final def lookupConcreteTraitMethods(method: MethodInfo): Iterator[MethodInfo] =
    allTraits.iterator.flatMap(_.concreteMethods).filter(_.bytecodeName == method.bytecodeName)

  final lazy val concreteMethods: List[MethodInfo] = methods.value.filter(!_.isBytecodeDeferred)

  /** The deferred methods of this type. */
  final lazy val deferredMethods: List[MethodInfo] = {
    val concreteMethods = this.concreteMethods.toSet
    methods.value.filterNot(concreteMethods)
  }

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
      else if (t.isTraitOrInterface) parentsClosure(t) :+ t
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

  /** Does the given method have a static mixin forwarder? */
  final def hasMixinForwarder(m: MethodInfo): Boolean = {
    methods.get(m.bytecodeName + "$").exists { fm =>
      val fsig = fm.descriptor
      val tsig = m.descriptor
      assert(fsig(0) == '(' && tsig(0) == '(', s"fsig=[$fsig] tsig=[$tsig]")
      hasMatchingSig(fsig, tsig)
    }
  }

  // Does `sig` correspond to `tsig` if seen as the signature of the mixin
  // forwarder method of a trait method with signature `tsig`?
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
