package com.typesafe.tools.mima.lib

import com.typesafe.tools.mima.core._, ProblemFilters.exclude
import org.junit.experimental.categories.Category

import IntegrationTests._

class Slow extends munit.Tag("Slow")

@Category(Array(classOf[Slow]))
class IntegrationTestSuite extends munit.FunSuite {
  test("scala-library-2-12")(testIntegration("org.scala-lang", "scala-library", "2.12.0", "2.12.10")(
    problemFilters = List(
      // all
      exclude[Problem]("scala.concurrent.impl.*"),
      exclude[Problem]("scala.sys.process.*Impl*"),

      // 2.12.2
      // dropped private[immutable] method on private[immutable] VectorPointer
      // ... which is extended by VectorBuilder & Vector, which are final
      // ... and also extended by VectorIterator, which isn't... :-/
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorPointer.debug"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorBuilder.debug"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.debug"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorIterator.debug"),
      // dropped private[mutable] & private[collection] methods on object OpenHashMap & object HashTable
      exclude[DirectMissingMethodProblem]("scala.collection.mutable.OpenHashMap.nextPositivePowerOfTwo"),
      exclude[DirectMissingMethodProblem]("scala.collection.mutable.HashTable.powerOfTwo"),
      // -def #::(hd: A): Stream[A]
      // +def #::[B >: A](hd: B): Stream[B]
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Stream#ConsWrapper.#::"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Stream#ConsWrapper.#:::"),

      // 2.12.3
      // Part of scala/scala#5805: "Change type parameters to the [A1, B1] convention", such as:
      // -def getOrElse[AA >: A](or: => AA): AA
      // +def getOrElse[A1 >: A](or: => A1): A1
      // -def flatMap[BB >: B, X](f: A => Either[X, BB]): Either[X, BB]
      // +def flatMap[A1, B1 >: B](f: A => Either[A1, B1]): Either[A1, B1]
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.getOrElse"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.flatMap"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.filter"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.flatMap"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.getOrElse"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#LeftProjection.map"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#RightProjection.filter"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#RightProjection.flatMap"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#RightProjection.getOrElse"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either#RightProjection.map"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.cond"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.contains"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.filterOrElse"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.flatMap"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.getOrElse"),
      exclude[IncompatibleSignatureProblem]("scala.util.Either.map"),

      // 2.12.5
      // Now returns Iterator[List[Nothing]] instead of a Iterator[Traversable[Nothing]]
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.tails"),

      // 2.12.7
      // new signature is missing...??
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.andThen"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.seq"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.thisCollection"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.toSeq"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.view"),

      // 2.12.8
      // Before there were 2 Symbol.apply forwarder methods:
      //   one which is String -> Symbol, which is for Symbol#apply
      //   one which is Object -> Object, which is the Function1 bridge method
      exclude[DirectMissingMethodProblem]("scala.Symbol.apply"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.List.empty"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.andThen"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.drop"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.dropRight"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.dropWhile"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.head"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.reverse"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.reversed"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.seq"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.slice"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.tail"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.take"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.takeRight"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.takeWhile"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.thisCollection"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toCollection"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toSeq"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.view"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Queue.empty"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Stream.empty"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Stream.fill"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Stream.iterate"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Stream.range"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Stream.tabulate"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.empty"),
      exclude[DirectMissingMethodProblem]("scala.collection.mutable.LinkedList.empty"),
      exclude[DirectMissingMethodProblem]("scala.sys.process.Process.Future"),
      exclude[DirectMissingMethodProblem]("scala.sys.process.Process.Spawn"),
      exclude[DirectMissingMethodProblem]("scala.util.Properties.propFilename"),
      exclude[DirectMissingMethodProblem]("scala.util.Properties.scalaProps"),
      // 2.12.7 -> 2.12.8
      exclude[DirectMissingMethodProblem]("scala.None.get"),
      exclude[DirectMissingMethodProblem]("scala.ScalaReflectionException.apply"),
      exclude[DirectMissingMethodProblem]("scala.UninitializedFieldError.apply"),
      exclude[DirectMissingMethodProblem]("scala.collection.GenMap.empty"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.apply"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.groupBy"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.isDefinedAt"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toIterable"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toMap"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toSet"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Nil.toTraversable"),
      exclude[DirectMissingMethodProblem]("scala.collection.parallel.CompositeThrowable.apply"),
      exclude[DirectMissingMethodProblem]("scala.collection.script.Index.apply"),
      exclude[DirectMissingMethodProblem]("scala.text.DocCons.apply"),
      exclude[DirectMissingMethodProblem]("scala.text.DocGroup.apply"),
      exclude[DirectMissingMethodProblem]("scala.text.DocNest.apply"),
      exclude[DirectMissingMethodProblem]("scala.text.DocText.apply"),
      // more forwarders overloading confusing, now in the signatures
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.++:"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.:+"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.combinations"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.contains"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.copyToBuffer"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.fold"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.genericBuilder"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.groupBy"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.grouped"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.indexOf"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.indexOfSlice"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.inits"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.lastIndexOf"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.lastIndexOfSlice"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.orElse"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.padTo"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.partition"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.patch"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.permutations"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.product"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduce"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduceLeft"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduceLeftOption"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduceOption"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduceRight"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reduceRightOption"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.reverseMap"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.sameElements"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.scan"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.scanLeft"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.scanRight"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.sliding"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.sum"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.toBuffer"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.toSet"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.union"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.unzip"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.unzip3"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.updated"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.withFilter"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.zip"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.zipAll"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.zipWithIndex"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Nil.zipWithIndex"),

      // 2.12.9
      // type param renames:
      //
      // - method max(scala.math.Ordering)java.lang.Object in class scala.collection.immutable.TreeSet
      //   has a different signature in new version, where it is
      //   <A1:Ljava/lang/Object;>(Lscala/math/Ordering<TA1;>;)TA; rather than
      //   <B:Ljava/lang/Object;>(Lscala/math/Ordering<TB;>;)TA;
      //
      // - method min(scala.math.Ordering)java.lang.Object in class scala.collection.immutable.TreeSet
      //   has a different signature in new version, where it is
      //   <A1:Ljava/lang/Object;>(Lscala/math/Ordering<TA1;>;)TA; rather than
      //   <B:Ljava/lang/Object;>(Lscala/math/Ordering<TB;>;)TA;
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.TreeSet.max"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.TreeSet.min"),
      // more specific exceptions:
      // - method result(scala.concurrent.Awaitable,scala.concurrent.duration.Duration)java.lang.Object
      //   in class scala.concurrent.Await has a different signature in new version, where it is
      //   <T:Ljava/lang/Object;>(Lscala/concurrent/Awaitable<TT;>;Lscala/concurrent/duration/Duration;)TT;^Ljava/util/concurrent/TimeoutException;^Ljava/lang/InterruptedException; rather than
      //   <T:Ljava/lang/Object;>(Lscala/concurrent/Awaitable<TT;>;Lscala/concurrent/duration/Duration;)TT;^Ljava/lang/Exception;
      exclude[IncompatibleSignatureProblem]("scala.concurrent.Await.result"),
      exclude[IncompatibleSignatureProblem]("scala.concurrent.Awaitable.result"),

      // Both are `sealed abstract class` in 2.12.0 and in 2.12.10, but 2.12.0 ships them with
      // no pickle at all, only the empty `Scala` marker attribute, so mima cannot see the
      // `sealed` there and takes the hierarchy to have closed in between.
      exclude[HierarchyBecomesClosedProblem]("scala.runtime.Nothing$"),
      exclude[HierarchyBecomesClosedProblem]("scala.runtime.Null$"),
    ),
  ).get)

  test("scala-library-2-13")(testIntegration("org.scala-lang", "scala-library", "2.13.0", "2.13.1")(
    expected = List(
      "in new version, classes mixing scala.collection.MapView need be recompiled to wire to the new static mixin forwarder method all super calls to method className()java.lang.String",
    ),
    problemFilters = List(
      // all
      exclude[Problem]("scala.concurrent.impl.*"),
      exclude[Problem]("scala.sys.process.*Impl*"),

      // 2.13.1
      // More specific exceptions:
      // - method result(scala.concurrent.Awaitable,scala.concurrent.duration.Duration)java.lang.Object
      //   in class scala.concurrent.Await has a different signature in new version, where it is
      //   <T:Ljava/lang/Object;>(Lscala/concurrent/Awaitable<TT;>;Lscala/concurrent/duration/Duration;)TT;^Ljava/util/concurrent/TimeoutException;^Ljava/lang/InterruptedException; rather than
      //   <T:Ljava/lang/Object;>(Lscala/concurrent/Awaitable<TT;>;Lscala/concurrent/duration/Duration;)TT;^Ljava/lang/Exception;
      exclude[IncompatibleSignatureProblem]("scala.concurrent.Await.result"),
      exclude[IncompatibleSignatureProblem]("scala.concurrent.Awaitable.result"),

      // scala/scala#8300
      exclude[FinalClassProblem]("scala.collection.ArrayOps$GroupedIterator"),
      exclude[DirectAbstractMethodProblem]("scala.collection.immutable.ArraySeq.stepper"),
      exclude[ReversedAbstractMethodProblem]("scala.collection.immutable.ArraySeq.stepper"),
      exclude[DirectAbstractMethodProblem]("scala.collection.mutable.ArraySeq.stepper"),
      exclude[ReversedAbstractMethodProblem]("scala.collection.mutable.ArraySeq.stepper"),

      // scala/scala#8367
      exclude[FinalMethodProblem]("scala.collection.immutable.Stream.find"),

      // scala/scala#8410
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.gotoPosWritable1"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.gotoPosWritable1$default$4"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.nullSlotAndCopy"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.Vector.nullSlotAndCopy$default$3"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorBuilder.gotoPosWritable1"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorBuilder.gotoPosWritable1$default$4"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorBuilder.nullSlotAndCopy"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorBuilder.nullSlotAndCopy$default$3"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorIterator.gotoPosWritable1"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorIterator.gotoPosWritable1$default$4"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorIterator.nullSlotAndCopy"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorIterator.nullSlotAndCopy$default$3"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorPointer.gotoPosWritable1"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorPointer.gotoPosWritable1$default$4"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorPointer.nullSlotAndCopy"),
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.VectorPointer.nullSlotAndCopy$default$3"),

      // 2.13.2
      exclude[Problem]("scala.collection.immutable.MapKeyValueTupleHashIterator*"), // private class
      exclude[DirectMissingMethodProblem]("scala.collection.immutable.RedBlackTree.countInRange"),
      exclude[IncompatibleSignatureProblem]("scala.collection.immutable.Vector*.copyRange"),
      exclude[IncompatibleSignatureProblem]("scala.runtime.Tuple*Zipped#Ops.*"),
    ),
  ).get)

  test("scala-reflect-2-12")(testIntegration("org.scala-lang", "scala-reflect", "2.12.1", "2.12.10")(
    problemFilters = List(
      exclude[Problem]("scala.reflect.internal.*"),

      // 2.12.1
      // JavaMirrors is private[scala]
      // JavaUniverse is private[scala]
      // SymbolLoaders is private[reflect]
      exclude[Problem]("scala.reflect.runtime.JavaMirrors*"),
      exclude[Problem]("scala.reflect.runtime.JavaUniverse*"),
      exclude[Problem]("scala.reflect.runtime.SymbolLoaders*"),

      // 2.12.2
      // SynchronizedOps is private[reflect]
      exclude[Problem]("scala.reflect.runtime.SynchronizedOps*"),

      // 2.12.4
      // IOStats is private[io]
      exclude[Problem]("scala.reflect.io.IOStats*"),
      // SynchronizedSymbols is private[reflect]
      exclude[Problem]("scala.reflect.runtime.SynchronizedSymbols*"),

      // 2.12.2-4 -> 2.12.5
      // FileZipArchive#LeakyEntry is private[this]
      exclude[Problem]("scala.reflect.io.FileZipArchive#LeakyEntry*"),

      // 2.12.7
      // Related to bridge method static forwarder overloads, see below.
      exclude[IncompatibleSignatureProblem]("scala.reflect.io.NoAbstractFile.seq"),
      exclude[IncompatibleSignatureProblem]("scala.reflect.io.NoAbstractFile.view"),

      // 2.12.8
      // Loss of the bridge method static forwarder overloads
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.seq"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.view"),

      // 2.12.7 -> 2.12.8-9(+?)
      // Loss of the bridge method static forwarder overloads
      exclude[DirectMissingMethodProblem]("scala.reflect.io.FileOperationException.apply"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.groupBy"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.toIterable"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.toMap"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.toSeq"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.toSet"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.NoAbstractFile.toTraversable"),
      exclude[DirectMissingMethodProblem]("scala.reflect.macros.ParseException.apply"),
      exclude[DirectMissingMethodProblem]("scala.reflect.macros.ReificationException.apply"),
      exclude[DirectMissingMethodProblem]("scala.reflect.macros.TypecheckException.apply"),
      exclude[DirectMissingMethodProblem]("scala.reflect.macros.UnexpectedReificationException.apply"),

      // 2.12.9
      // ZipArchive is "considered experimental"
      exclude[ReversedMissingMethodProblem]("scala.reflect.io.ZipArchive.close"),

      // 2.12.10
      // ZipArchive is "considered experimental"
      // switches from scala.collection.mutable.Map/scala.collection.mutable.HashMap to java.util.Map
      exclude[IncompatibleResultTypeProblem]("scala.reflect.io.FileZipArchive.allDirs"),
      exclude[IncompatibleMethTypeProblem]("scala.reflect.io.ZipArchive.getDir"),

      // 2.12.11
      exclude[ReversedMissingMethodProblem]("scala.reflect.runtime.SynchronizedTypes.scala$reflect$runtime$SynchronizedTypes$$super$defineNormalized"),
    ),
  ))

  test("scala-reflect-2-13")(testIntegration("org.scala-lang", "scala-reflect", "2.13.0", "2.13.1")(
    problemFilters = List(
      exclude[Problem]("scala.reflect.internal.*"),

      // 2.13.1
      // ZipArchive is "considered experimental"
      // switches from scala.collection.mutable.Map/scala.collection.mutable.HashMap to java.util.Map
      // drops a method & introduces a new class
      exclude[IncompatibleResultTypeProblem]("scala.reflect.io.FileZipArchive.allDirs"),
      exclude[DirectMissingMethodProblem]("scala.reflect.io.FileZipArchive.allDirsByDottedName"),
      exclude[IncompatibleMethTypeProblem]("scala.reflect.io.ZipArchive.getDir"),
      exclude[ReversedMissingMethodProblem]("scala.reflect.runtime.*"),
    ),
  ))

  test("java-9-module-info") {
    // jaxb-api 2.3.0 introduced a module-info.class
    // which caused MiMa to blow up when parsing the class file
    // https://github.com/scala-garden/mima/issues/206
    // this test checks it against 2.3.0-b170201.1204 (a beta release of 2.3.0?)
    // to assert MiMa doesn't blow up
    testIntegration("javax.xml.bind", "jaxb-api", "2.3.0", "2.3.0-b170201-1204")()
  }

  test("scala3-library")(testIntegration("org.scala-lang", "scala3-library_3", "3.0.0", "3.0.1")(
    excludeAnnots = List("scala.annotation.experimental"),
  ))
}
