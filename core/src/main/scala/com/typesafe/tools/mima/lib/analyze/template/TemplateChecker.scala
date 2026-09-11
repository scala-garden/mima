// scalafmt: { align.preset = none  }
package com.typesafe.tools.mima.lib.analyze.template

import com.typesafe.tools.mima.core._

private[analyze] object TemplateChecker {
  def check(oldclazz: ClassInfo, newclazz: ClassInfo): Option[Problem] = {
    if (oldclazz.isTraitOrInterface != newclazz.isTraitOrInterface)
      Some(IncompatibleTemplateDefProblem(oldclazz, newclazz))
    else if (newclazz.isBytecodeLessVisibleThan(oldclazz))
      Some(InaccessibleClassProblem(newclazz))
    else if (!oldclazz.isBytecodeDeferred && newclazz.isBytecodeDeferred)
      Some(AbstractClassProblem(oldclazz))
    else if (!oldclazz.isBytecodeFinal && newclazz.isBytecodeFinal)
      Some(FinalClassProblem(oldclazz))
    else if (newclazz.superClasses.contains(newclazz))
      Some(CyclicTypeReferenceProblem(newclazz))
    else {
      val missingSuperClasses = oldclazz.superClasses.diff(newclazz.superClasses)
      val missingInterfaces = oldclazz.allInterfaces.diff(newclazz.allInterfaces)
      if (missingSuperClasses.nonEmpty)
        Some(MissingTypesProblem(newclazz, missingSuperClasses))
      else if (missingInterfaces.nonEmpty)
        Some(MissingTypesProblem(newclazz, missingInterfaces))
      else if (hasIncompatibleParentTypeArgs(oldclazz, newclazz))
        Some(IncompatibleClassSignatureProblem(oldclazz, newclazz))
      else
        None
    }
  }

  private def hasIncompatibleParentTypeArgs(oldclazz: ClassInfo, newclazz: ClassInfo): Boolean = {
    val newArgs = newclazz.signature.parentTypeArgs
    oldclazz.signature.parentTypeArgs.exists { case (parent, args) => newArgs.get(parent).exists(_ != args) }
  }
}
