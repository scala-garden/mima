package com.typesafe.tools.mima.core

object MemberInfo {
  val ConstructorName      = "<init>"
  val ClassInitializerName = "<clinit>"
}

sealed abstract class MemberInfo(val owner: ClassInfo, val bytecodeName: String, val flags: Int, val descriptor: String)
    extends InfoLike {
  final var isDeprecated: Boolean  = false
  final var signature: Signature   = Signature.none // Includes generics. 'descriptor' is the erased version.
  final var scopedPrivate: Boolean = false
  final var classPrivate: Boolean  = false

  /** The class's pickle declares no method of this name, so the compiler generated it. */
  final var absentFromPickle: Boolean = false

  def nonAccessible: Boolean

  final def fullName: String          = s"${owner.formattedFullName}.$decodedName"
  final def abstractPrefix            = if (isDeferred && !owner.isTrait) "abstract " else ""
  final def scopedPrivatePrefix       = "private[..] "
  final def classPrivatePrefix        = "private "
  final def staticPrefix: String      = if (isStatic) "static " else ""
  final def tpe: Type                 = owner.owner.definitions.fromDescriptor(descriptor)
  final def hasSyntheticName: Boolean = decodedName.contains('$')

  final def memberString: String = this match {
    case info: FieldInfo  => info.fieldString
    case info: MethodInfo => info.methodString
  }

  private[mima] def isExternallyAccessible: Boolean = !scopedPrivate && owner.isExternallyAccessible
}

private[mima] final class FieldInfo(owner: ClassInfo, bytecodeName: String, flags: Int, descriptor: String)
    extends MemberInfo(owner, bytecodeName, flags, descriptor) {
  /** The static field dotty emits per nested object, dropped in 3.10 by scala/scala3#26937.
   *  Nothing reads it: a nested object is reached as `Foo$Bar$.MODULE$`. */
  private[mima] def isNestedObjectField: Boolean = // descriptors are dotted, not slashed
    isStatic && owner.isModuleClass && descriptor == s"L${owner.fullName}$bytecodeName$$;"
  def nonAccessible: Boolean = !isPublic || isSynthetic || hasSyntheticName || isNestedObjectField
  def fieldString: String    = s"${staticPrefix}field $decodedName in ${owner.classString}"
  override def toString      = s"field $bytecodeName: $descriptor"
}

private[mima] final class MethodInfo(owner: ClassInfo, bytecodeName: String, flags: Int, descriptor: String)
    extends MemberInfo(owner, bytecodeName, flags, descriptor) {
  final var _annotations: List[AnnotInfo] = Nil
  final def annotations: List[AnnotInfo]  = _annotations

  def methodString: String      = s"$shortMethodString in ${owner.classString}"
  def shortMethodString: String = {
    val prefix = if (hasSyntheticName) if (isExtensionMethod) "extension " else "synthetic " else ""

    val deprecated = if (isDeprecated) "deprecated " else ""

    val privatePrefix = if (isScopedPrivate) {
      scopedPrivatePrefix
    } else if (isClassPrivate) {
      classPrivatePrefix
    } else {
      ""
    }

    s"${privatePrefix}${abstractPrefix}$prefix${deprecated}${staticPrefix}method $decodedName$tpe"
  }

  lazy val paramsCount: Int = {
    tpe match {
      case MethodType(paramTypes, _) => paramTypes.length

      case _ => throw new MatchError(s"Failed to get method params, member had type $tpe, not MethodType.")
    }
  }

  assert(descriptor.charAt(0) == '(')
  def parametersDesc: String                  = descriptor.substring(1, descriptor.indexOf(")"))
  def matchesType(other: MethodInfo): Boolean = parametersDesc == other.parametersDesc

  private def isDefaultGetter: Boolean    = decodedName.contains("$default$")
  private def isTraitInit: Boolean        = decodedName == "$init$"
  private def isClassInitializer: Boolean = bytecodeName == MemberInfo.ClassInitializerName
  private def isExtensionMethod: Boolean  = {
    var i = decodedName.length - 1
    while (i >= 0 && Character.isDigit(decodedName.charAt(i)))
      i -= 1
    decodedName.substring(0, i + 1).endsWith("$extension")
  }

  /** A mixin forwarder scalac copies into a class drops the trait method's access:
   *  the bytecode says public where the source says `private[p]`. */
  private def isScopedPrivateMixinForwarder: Boolean =
    absentFromPickle && !owner.isTrait && owner.allTraits.exists {
      _.methods.get(bytecodeName).exists(m => m.descriptor == descriptor && m.isScopedPrivate)
    }
  def nonAccessible: Boolean = {
    !isPublic || isScopedPrivate || isClassPrivate || isSynthetic || isClassInitializer ||
    isScopedPrivateMixinForwarder ||
    (hasSyntheticName && !(isExtensionMethod || isDefaultGetter || isTraitInit))
  }
  def isScopedPrivate: Boolean = scopedPrivate

  def isClassPrivate: Boolean = classPrivate

  override def toString = s"def $bytecodeName: $descriptor"
}
