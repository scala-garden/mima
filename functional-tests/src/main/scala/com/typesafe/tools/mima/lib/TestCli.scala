// scalafmt: { maxColumn = 120, align.preset = some }
package com.typesafe.tools.mima.lib

import javax.tools._

import scala.annotation.tailrec
import scala.reflect.io.Directory
import scala.util.Try
import scala.util.{ Properties => StdLibProps }

object TestCli {
  // Keep in sync with build.sbt
  val scala211 = "2.11.12"
  val scala212 = "2.12.21"
  val scala213 = "3.9.0"
  val scala3 = "3.3.8"
  val hostScalaVersion = StdLibProps.scalaPropOrNone("maven.version.number").get
  val allScalaVersions = List(scala211, scala212, scala213, scala3)
  val testsDir = Directory("functional-tests/src/test")

  def argsToTests(args: List[String], runTestCase: TestCase => Try[Unit]): Tests =
    testCasesToTests(fromArgs(args), runTestCase)

  def testCasesToTests(testCases: List[TestCase], runTestCase: TestCase => Try[Unit]): Tests =
    Tests(testCases.map(testCaseToTest1(_, runTestCase)))

  def testCaseToTest1(tc: TestCase, runTestCase: TestCase => Try[Unit]): Test1 =
    Test(s"${tc.scalaBinaryVersion} / ${tc.name}", runTestCase(tc))

  def fromArgs(args: List[String]): List[TestCase] = fromConf(readArgs(args, Conf(Nil, Nil)))

  @tailrec def postProcessConf(conf: Conf): Conf = conf match {
    case Conf(Nil, _) => postProcessConf(conf.copy(scalaVersions = List(hostScalaVersion)))
    case Conf(_, Nil) => postProcessConf(conf.copy(dirs = allTestDirs().ensuring(_.nonEmpty)))
    case _            => conf
  }

  def fromConf(conf: Conf): List[TestCase] = {
    val conf1 = postProcessConf(conf)
    val javaCompiler = getJavaCompiler
    for {
      sc <- conf1.scalaVersions.map(new ScalaCompiler(_))
      dir <- conf1.dirs
    } yield new TestCase(dir, sc, javaCompiler)
  }

  def getJavaCompiler = ToolProvider.getSystemJavaCompiler

  def allTestDirs() = testsDir.dirs.filter(_.files.exists { f =>
    f.name == Backwards.oracleFile || f.name == Forwards.oracleFile
  }).toList.sortBy(_.path)

  final case class Conf(scalaVersions: List[String], dirs: List[Directory])

  @tailrec private def readArgs(args: List[String], conf: Conf): Conf = args match {
    case "-3" :: xs                    => readArgs(xs, conf.copy(scalaVersions = conf.scalaVersions :+ scala3))
    case "-2.13" :: xs                 => readArgs(xs, conf.copy(scalaVersions = conf.scalaVersions :+ scala213))
    case "-2.12" :: xs                 => readArgs(xs, conf.copy(scalaVersions = conf.scalaVersions :+ scala212))
    case "-2.11" :: xs                 => readArgs(xs, conf.copy(scalaVersions = conf.scalaVersions :+ scala211))
    case "--scala-version" :: sv :: xs => readArgs(xs, conf.copy(scalaVersions = conf.scalaVersions :+ sv))
    case "--cross" :: xs               => readArgs(xs, conf.copy(scalaVersions = allScalaVersions))
    case s :: xs                       => readArgs(xs, conf.copy(dirs = testDirs(s) ::: conf.dirs))
    case Nil                           => conf
  }

  /** All the directories in the tests directory whose name contain the given string `s`. */
  def testDirs(s: String): List[Directory] = {
    testsDir.dirs.filter(_.name.contains(s)).toList.sortBy(_.path) match {
      case Nil  => sys.error(s"No such directory: ${testsDir / s}")
      case dirs => dirs
    }
  }

  if (System.out != Console.out) {
    System.out.println(" System.out identity ##: " + System.identityHashCode(System.out))
    System.out.println("Console.out identity ##: " + System.identityHashCode(scala.Console.out))
    System.out.println("cwd: " + new java.io.File("").getAbsoluteFile)
  }
}
