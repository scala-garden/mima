// scalafmt: { maxColumn = 280 }
package com.typesafe.tools.mima.core

trait ProblemRef {
  /** The name to use to filter out the problem. */
  def matchName: Option[String] = None

  /** The code snippet to use to filter out the problem. */
  def howToFilter: Option[String] =
    matchName.map(name => s"""ProblemFilters.exclude[${getClass.getSimpleName}]("$name")""")
}

trait TemplateRef extends ProblemRef

trait MemberRef extends ProblemRef

sealed abstract class Problem extends ProblemRef {
  final override def matchName: Some[String] = this match {
    case p: TemplateProblem => Some(p.ref.fullName)
    case p: MemberProblem   => Some(p.ref.fullName)
  }

  /** Whether a client outside the library can name what this problem is about.
   *
   *  It is false for a definition that only Scala keeps private, such as
   *  private[foo], and a filter can drop those problems by asking this.
   */
  final def isExternallyAccessible: Boolean = this match {
    case p: TemplateProblem => p.ref.isExternallyAccessible
    case p: MemberProblem   => p.ref.isExternallyAccessible
  }

  /** 'affectedVersion' is "current" for bincompat, "other" or "previous" for forward-compat. */
  final def description: String => String = getDescription(_)

  private def getDescription(affectedVersion: String): String = this match {
    case MissingClassProblem(oldclazz)                    => s"${oldclazz.classString} does not have a correspondent in $affectedVersion version"
    case IncompatibleTemplateDefProblem(ref, newclazz)    => s"declaration of ${ref.description} is ${newclazz.description} in $affectedVersion version; changing ${ref.declarationPrefix} to ${newclazz.declarationPrefix} breaks client code"
    case InaccessibleClassProblem(ref)                    => s"${ref.classString} is inaccessible in $affectedVersion version, it must be public."
    case ClassBecomesUnreachableProblem(_, newclazz)      => s"${newclazz.classString} is unreachable in $affectedVersion version, it must be public."
    case HierarchyBecomesClosedProblem(ref)               => s"${ref.classString} was extensible; in $affectedVersion version it is sealed and all its subtypes are closed, hiding later changes that would break an existing implementation"
    case AbstractClassProblem(ref)                        => s"${ref.classString} was concrete; is declared abstract in $affectedVersion version"
    case FinalClassProblem(ref)                           => s"${ref.classString} is declared final in $affectedVersion version"
    case CyclicTypeReferenceProblem(ref)                  => s"the type hierarchy of ${ref.description} is different in $affectedVersion version. Type ${ref.bytecodeName} appears to be a subtype of itself"
    case MissingTypesProblem(ref, missing)                => s"the type hierarchy of ${ref.description} is different in $affectedVersion version. Missing types ${missing.map(_.fullName).mkString("{", ",", "}")}"
    case IncompatibleClassSignatureProblem(ref, newclazz) => s"${ref.classString} has a different generic signature in $affectedVersion version, where it is ${newclazz.signature} rather than ${ref.signature}. See https://github.com/scala-garden/mima#incompatiblesignatureproblem"

    case MissingFieldProblem(ref)                         => s"${ref.memberString} does not have a correspondent in $affectedVersion version"
    case InaccessibleFieldProblem(ref)                    => s"${ref.memberString} is inaccessible in $affectedVersion version, it must be public."
    case InaccessibleMethodProblem(ref)                   => s"${ref.memberString} is inaccessible in $affectedVersion version, it must be public."
    case IncompatibleFieldTypeProblem(ref, newfld)        => s"${ref.memberString}'s type is different in $affectedVersion version, where it is: ${newfld.tpe} rather than: ${ref.tpe}"
    case IncompatibleMethTypeProblem(ref, newmeth :: Nil) => s"${ref.memberString}'s type is different in $affectedVersion version, where it is ${newmeth.tpe} instead of ${ref.tpe}"
    case IncompatibleMethTypeProblem(ref, newmeths)       => s"${ref.memberString} in $affectedVersion version does not have a correspondent with same parameter signature among ${newmeths.map(_.tpe).mkString(", ")}"
    case StaticVirtualMemberProblem(ref)                  => s"${ref.memberString} is non-static in $affectedVersion version"
    case VirtualStaticMemberProblem(ref)                  => s"non-static ${ref.memberString} is static in $affectedVersion version"
    case DirectMissingMethodProblem(ref)                  => s"${ref.memberString} does not have a correspondent in $affectedVersion version"
    case ReversedMissingMethodProblem(ref)                => s"${ref.memberString} is present only in $affectedVersion version"
    case FinalMethodProblem(ref)                          => s"${ref.methodString} is declared final in $affectedVersion version"
    case IncompatibleResultTypeProblem(ref, newmeth)      => s"${ref.methodString} has a different result type in $affectedVersion version, where it is ${newmeth.tpe.resultType} rather than ${ref.tpe.resultType}"
    case IncompatibleSignatureProblem(ref, newmeth)       => s"${ref.methodString} has a different generic signature in $affectedVersion version, where it is ${newmeth.signature} rather than ${ref.signature}. See https://github.com/scala-garden/mima#incompatiblesignatureproblem"
    case DirectAbstractMethodProblem(ref)                 => s"${ref.methodString} does not have a correspondent in $affectedVersion version"
    case ReversedAbstractMethodProblem(ref)               => s"in $affectedVersion version there is ${ref.methodString}, which does not have a correspondent"
    case UpdateForwarderBodyProblem(ref)                  => s"in $affectedVersion version, classes mixing ${ref.owner.fullName} needs to update body of ${ref.shortMethodString}"
    case NewMixinForwarderProblem(ref)                    => s"in $affectedVersion version, classes mixing ${ref.owner.fullName} need be recompiled to wire to the new static mixin forwarder method all super calls to ${ref.shortMethodString}"
    case InheritedNewAbstractMethodProblem(absmeth, ref)  => s"${absmeth.methodString} is inherited by class ${ref.owner.bytecodeName} in $affectedVersion version."
  }
}

// Template problems
sealed abstract class TemplateProblem(val ref: ClassInfo)                                    extends Problem with TemplateRef
final case class MissingClassProblem(oldclazz: ClassInfo)                                    extends TemplateProblem(oldclazz)
final case class IncompatibleTemplateDefProblem(oldclazz: ClassInfo, newclazz: ClassInfo)    extends TemplateProblem(oldclazz)
final case class InaccessibleClassProblem(newclazz: ClassInfo)                               extends TemplateProblem(newclazz)
final case class ClassBecomesUnreachableProblem(oldclazz: ClassInfo, newclazz: ClassInfo)    extends TemplateProblem(oldclazz)
final case class HierarchyBecomesClosedProblem(oldclazz: ClassInfo)                          extends TemplateProblem(oldclazz)
final case class AbstractClassProblem(oldclazz: ClassInfo)                                   extends TemplateProblem(oldclazz)
final case class FinalClassProblem(oldclazz: ClassInfo)                                      extends TemplateProblem(oldclazz)
final case class CyclicTypeReferenceProblem(clazz: ClassInfo)                                extends TemplateProblem(clazz)
final case class MissingTypesProblem(newclazz: ClassInfo, missing: Iterable[ClassInfo])      extends TemplateProblem(newclazz)
final case class IncompatibleClassSignatureProblem(oldclazz: ClassInfo, newclazz: ClassInfo) extends TemplateProblem(oldclazz)

// Member problems
sealed abstract class MemberProblem(val ref: MemberInfo) extends Problem with MemberRef

/// Field problems
final case class MissingFieldProblem(oldfld: FieldInfo)                             extends MemberProblem(oldfld)
final case class InaccessibleFieldProblem(newfld: FieldInfo)                        extends MemberProblem(newfld)
final case class IncompatibleFieldTypeProblem(oldfld: FieldInfo, newfld: FieldInfo) extends MemberProblem(oldfld)

/// Member-generic problems
final case class StaticVirtualMemberProblem(oldmemb: MemberInfo) extends AbstractMethodProblem(oldmemb)
final case class VirtualStaticMemberProblem(oldmemb: MemberInfo) extends AbstractMethodProblem(oldmemb)

/// Method problems
sealed abstract class MissingMethodProblem(meth: MethodInfo)                                  extends MemberProblem(meth)
final case class DirectMissingMethodProblem(meth: MethodInfo)                                 extends MissingMethodProblem(meth)
final case class ReversedMissingMethodProblem(meth: MethodInfo)                               extends MissingMethodProblem(meth)
final case class InaccessibleMethodProblem(newmeth: MethodInfo)                               extends MemberProblem(newmeth)
final case class IncompatibleMethTypeProblem(oldmeth: MethodInfo, newmeths: List[MethodInfo]) extends MemberProblem(oldmeth)
final case class IncompatibleResultTypeProblem(oldmeth: MethodInfo, newmeth: MethodInfo)      extends MemberProblem(oldmeth)
final case class IncompatibleSignatureProblem(oldmeth: MethodInfo, newmeth: MethodInfo)       extends MemberProblem(oldmeth)
final case class FinalMethodProblem(newmeth: MethodInfo)                                      extends MemberProblem(newmeth)
sealed abstract class AbstractMethodProblem(memb: MemberInfo)                                 extends MemberProblem(memb)
final case class DirectAbstractMethodProblem(newmeth: MethodInfo)                             extends AbstractMethodProblem(newmeth)
final case class ReversedAbstractMethodProblem(newmeth: MethodInfo)                           extends MemberProblem(newmeth)
// no longer reported: only the 2.11 impl class encoding produced it
final case class UpdateForwarderBodyProblem(oldmeth: MethodInfo)                             extends MemberProblem(oldmeth)
final case class NewMixinForwarderProblem(oldmeth: MethodInfo)                               extends MemberProblem(oldmeth)
final case class InheritedNewAbstractMethodProblem(absmeth: MethodInfo, newmeth: MethodInfo) extends AbstractMethodProblem(newmeth)
