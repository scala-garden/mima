// scalafmt: { maxColumn = 150, align.preset = some }
package com.typesafe.tools.mima.lib.analyze.method

import com.typesafe.tools.mima.core._

private[analyze] object MethodChecker {
  def check(oldclazz: ClassInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): List[Problem] =
    checkExisting(oldclazz, newclazz, excludeAnnots) ::: checkNew(oldclazz, newclazz, excludeAnnots)

  /** Analyze incompatibilities that may derive from changes in existing methods. */
  private def checkExisting(oldclazz: ClassInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    for (oldmeth <- oldclazz.methods.value; problem <- checkExisting1(oldmeth, newclazz, excludeAnnots)) yield problem
  }

  /** Analyze incompatibilities that may derive from new methods in `newclazz`. */
  private def checkNew(oldclazz: ClassInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    // these problems break a client that implements oldclazz, and nobody outside can
    if (oldclazz.isClosedHierarchy) return Nil
    val problems1 = if (newclazz.isClass) Nil else checkEmulatedConcreteMethodsProblems(oldclazz, newclazz)
    val problems2 = checkDeferredMethodsProblems(oldclazz, newclazz, excludeAnnots)
    val problems3 = checkInheritedNewAbstractMethodProblems(oldclazz, newclazz, excludeAnnots)
    problems1 ::: problems2 ::: problems3
  }

  private def checkExisting1(oldmeth: MethodInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): Option[Problem] = {
    if (oldmeth.nonAccessible || excludeAnnots.exists(oldmeth.annotations.contains))
      None
    else if (newclazz.isClass) {
      if (oldmeth.isDeferred)
        checkExisting1Impl(oldmeth, newclazz, _.lookupMethods(oldmeth))
      else
        checkExisting1Impl(oldmeth, newclazz, _.lookupClassMethods(oldmeth))
    } else {
      if (oldmeth.owner.hasStaticImpl(oldmeth))
        checkStaticImplMethod(oldmeth, newclazz)
      else if (oldmeth.owner.hasMixinForwarder(oldmeth))
        checkStaticMixinForwarderMethod(oldmeth, newclazz)
      else
        checkExisting1Impl(oldmeth, newclazz, _.lookupMethods(oldmeth))
    }
  }

  private def checkExisting1Impl(oldmeth: MethodInfo, newclazz: ClassInfo, methsLookup: ClassInfo => Iterator[MethodInfo]): Option[Problem] = {
    val newmeths = methsLookup(newclazz).filter(oldmeth.paramsCount == _.paramsCount).toList
    newmeths.find(newmeth => hasMatchingDescriptorAndSignature(oldmeth, newmeth)) match {
      case Some(newmeth) => checkExisting1v1(oldmeth, newmeth)
      case None          => Some(missingOrIncompatible(oldmeth, newmeths, methsLookup))
    }
  }

  private def hasMatchingDescriptorAndSignature(oldmeth: MethodInfo, newmeth: MethodInfo): Boolean =
    oldmeth.descriptor == newmeth.descriptor &&
      oldmeth.signature.matches(newmeth.signature, newmeth.bytecodeName == MemberInfo.ConstructorName)

  private def checkExisting1v1(oldmeth: MethodInfo, newmeth: MethodInfo) = {
    if (newmeth.isLessVisibleThan(oldmeth))
      Some(InaccessibleMethodProblem(newmeth))
    else if (oldmeth.nonFinal && newmeth.isFinal && oldmeth.owner.nonFinal)
      Some(FinalMethodProblem(newmeth))
    else if (oldmeth.isConcrete && newmeth.isDeferred)
      Some(DirectAbstractMethodProblem(newmeth))
    else if (oldmeth.isStatic && !newmeth.isStatic)
      Some(StaticVirtualMemberProblem(oldmeth))
    else if (!oldmeth.isStatic && newmeth.isStatic)
      Some(VirtualStaticMemberProblem(oldmeth))
    else
      None
  }

  private def checkStaticImplMethod(oldmeth: MethodInfo, newclazz: ClassInfo) = {
    if (newclazz.hasStaticImpl(oldmeth)) {
      None // then it's ok, the method it is still there
    } else {
      // if a concrete method exists on some inherited trait, then we
      // report the missing method but we can upgrade the bytecode for
      // this specific issue
      if (newclazz.allTraits.exists(_.hasStaticImpl(oldmeth))) {
        Some(UpdateForwarderBodyProblem(oldmeth))
      } else {
        // otherwise we check all the concrete trait methods and report
        // the missing or incompatible method.
        val methsLookup = (_: ClassInfo).lookupConcreteTraitMethods(oldmeth)
        Some(missingOrIncompatible(oldmeth, methsLookup(newclazz).toList, methsLookup))
      }
    }
  }

  private def checkStaticMixinForwarderMethod(oldmeth: MethodInfo, newclazz: ClassInfo) = {
    if (newclazz.hasMixinForwarder(oldmeth)) {
      None // then it's ok, the method it is still there
    } else {
      if (newclazz.allTraits.exists(_.hasMixinForwarder(oldmeth))) {
        Some(NewMixinForwarderProblem(oldmeth))
      } else {
        val methsLookup = (_: ClassInfo).lookupConcreteTraitMethods(oldmeth)
        Some(missingOrIncompatible(oldmeth, methsLookup(newclazz).toList, methsLookup))
      }
    }
  }

  private def missingOrIncompatible(oldmeth: MethodInfo, newmeths: List[MethodInfo], methsLookup: ClassInfo => Iterator[MethodInfo]) = {
    val newmethAndBridges = newmeths.filter(oldmeth.matchesType(_)) // _the_ corresponding new method + its bridges
    newmethAndBridges.find(_.tpe.resultType == oldmeth.tpe.resultType) match {
      case Some(newmeth) => IncompatibleSignatureProblem(oldmeth, newmeth)
      case None          =>
        val oldmethsDescriptors = methsLookup(oldmeth.owner).map(_.descriptor).toSet
        if (newmeths.forall(newmeth => oldmethsDescriptors.contains(newmeth.descriptor)))
          DirectMissingMethodProblem(oldmeth)
        else {
          newmethAndBridges match {
            case Nil          => IncompatibleMethTypeProblem(oldmeth, uniques(newmeths))
            case newmeth :: _ => IncompatibleResultTypeProblem(oldmeth, newmeth)
          }
        }
    }
  }

  private def uniques(methods: Iterable[MethodInfo]): List[MethodInfo] =
    methods.groupBy(_.parametersDesc).values.collect { case method :: _ => method }.toList

  private def checkEmulatedConcreteMethodsProblems(oldclazz: ClassInfo, newclazz: ClassInfo): List[Problem] = {
    for {
      newmeth <- newclazz.emulatedConcreteMethods.iterator
      if !newmeth.isScopedPrivate // nothing outside the scope can implement it
      if !oldclazz.hasStaticImpl(newmeth)
      problem <- {
        if (oldclazz.lookupMethods(newmeth).exists(_.descriptor == newmeth.descriptor)) {
          // a static implementation for the same method existed already, therefore
          // classes that mixed-in the trait already have a forwarder to the implementation
          // class. Mind that, despite no binary incompatibility arises, program's
          // semantic may be severely affected.
          None
        } else {
          // this means that the method is brand new
          // and therefore the implementation has to be injected
          Some(ReversedMissingMethodProblem(newmeth))
        }
      }
    } yield problem
  }.toList

  private def checkDeferredMethodsProblems(oldclazz: ClassInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    for {
      newmeth <- newclazz.deferredMethods.iterator
      if !newmeth.isScopedPrivate // nothing outside the scope can implement it
      if !excludeAnnots.exists(newmeth.annotations.contains)
      problem <- oldclazz.lookupMethods(newmeth).find(_.descriptor == newmeth.descriptor) match {
        case None                                                    => Some(ReversedMissingMethodProblem(newmeth))
        case Some(oldmeth) if newclazz.isClass && oldmeth.isConcrete => Some(ReversedAbstractMethodProblem(newmeth))
        case Some(_)                                                 => None
      }
    } yield problem
  }.toList

  private def checkInheritedNewAbstractMethodProblems(oldclazz: ClassInfo, newclazz: ClassInfo, excludeAnnots: List[AnnotInfo]): List[Problem] = {
    def allInheritedTypes(clazz: ClassInfo) = clazz.superClasses ++ clazz.allInterfaces
    val diffInheritedTypes = allInheritedTypes(newclazz).diff(allInheritedTypes(oldclazz))

    def noInheritedMatchingMethod(clazz: ClassInfo, meth: MethodInfo)(p: MemberInfo => Boolean) = {
      !clazz.lookupMethods(meth).filter(_.matchesType(meth)).exists(m => m.owner != meth.owner && p(m))
    }

    for {
      newInheritedType <- diffInheritedTypes.iterator
      // if `newInheritedType` is a trait, then the trait's concrete methods should be counted as deferred methods
      newDeferredMethod <- newInheritedType.deferredMethodsInBytecode
      if !excludeAnnots.exists(newDeferredMethod.annotations.contains)
      // checks that the newDeferredMethod did not already exist in one of the oldclazz supertypes
      if noInheritedMatchingMethod(oldclazz, newDeferredMethod)(_ => true) &&
        // checks that no concrete implementation of the newDeferredMethod is provided by one of the newclazz supertypes
        noInheritedMatchingMethod(newclazz, newDeferredMethod)(_.isConcrete)
    } yield {
      // report a binary incompatibility as there is a new inherited abstract method, which can lead to a AbstractErrorMethod at runtime
      val newmeth = new MethodInfo(newclazz, newDeferredMethod.bytecodeName, newDeferredMethod.flags, newDeferredMethod.descriptor)
      InheritedNewAbstractMethodProblem(newDeferredMethod, newmeth)
    }
  }.toList
}
