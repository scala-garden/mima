package com.typesafe.tools.mima.lib.analyze

import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.util.log.Logging
import com.typesafe.tools.mima.lib.analyze.field.FieldChecker
import com.typesafe.tools.mima.lib.analyze.method.MethodChecker
import com.typesafe.tools.mima.lib.analyze.template.TemplateChecker

object Analyzer {
  def analyze(oldpkg: PackageInfo, newpkg: PackageInfo, log: Logging, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    for {
      oldclazz <- oldpkg.accessibleClasses.toList.sortBy(_.bytecodeName)
      _ = log.verbose(s"analyzing $oldclazz")
      _ = oldclazz.forceLoad
      if !excludeAnnots.exists { annot =>
        oldclazz.outerChain.exists { cls =>
          cls.annotations.contains(annot) ||
          cls.companionClass.annotations.contains(annot) ||
          cls.moduleClass.annotations.contains(annot)
        }
      }
      problem <- newpkg.classes.get(oldclazz.bytecodeName) match {
        case Some(newclazz) =>
          // accessibleClasses is the set mima iterates: a class dropping out of it never gets
          // looked at again, so this is the last version that can report anything about it. One
          // that keeps escaping is still checked, so nothing to report; one the bytecode itself
          // makes inaccessible is TemplateChecker's InaccessibleClassProblem. The ref is oldclazz,
          // the only one a filter on Problem.isExternallyAccessible keeps.
          val noLongerChecked =
            if (newpkg.accessibleClasses(newclazz) || newclazz.isBytecodeLessVisibleThan(oldclazz)) Nil
            else List(ClassBecomesUnreachableProblem(oldclazz, newclazz))
          noLongerChecked ::: analyze(oldclazz, newclazz, log, excludeAnnots)
        case None => List(MissingClassProblem(oldclazz))
      }
    } yield {
      log.debug(s"problem found: ${problem.description("new")}")
      problem
    }
  }

  def analyze(oldclazz: ClassInfo, newclazz: ClassInfo, log: Logging, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    if (!oldclazz.isChecked) Nil
    else {
      TemplateChecker.check(oldclazz, newclazz) match {
        case p @ Some(_: IncompatibleTemplateDefProblem | _: CyclicTypeReferenceProblem) =>
          // these implies major incompatibility, does not make sense to continue
          p.toList

        case maybeProblem =>
          maybeProblem.toList :::
            FieldChecker.check(oldclazz, newclazz) :::
            MethodChecker.check(oldclazz, newclazz, excludeAnnots)
      }
    }
  }
}
