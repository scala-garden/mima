# MiMa

MiMa (for "Migration Manager") is a tool for identifying [binary incompatibilities][] in Scala libraries.

[binary incompatibilities]: https://docs.scala-lang.org/overviews/core/binary-compatibility-for-library-authors.html

It's pronounced _MEE-ma_.

## What it is?

MiMa can report binary modifications that may
cause the JVM to throw a `java.lang.LinkageError` (or one of its subtypes,
like `AbstractMethodError`) at runtime. Linkage errors are usually the
consequence of modifications in classes/members signature.

MiMa compares all classfiles of two released libraries and reports all source
of incompatibilities that may lead to a linkage error. MiMa provides you, the
library maintainer, with a tool that can greatly automate and simplify the
process of ensuring the release-to-release binary compatibility of your
libraries.

A key aspect of MiMa to be aware of is that it only looks for *syntactic binary
incompatibilities*. The semantic binary incompatibilities (such as adding or
removing a method invocation) are not considered. This is a pragmatic approach
as it is up to you, the library maintainer, to make sure that no semantic
changes have occurred between two binary compatible releases. If a semantic
change occurred, then you should make sure to provide this information as part
of the new release's change list.

In addition, it is worth mentioning that *binary compatibility does not imply
source compatibility*, i.e., some of the changes that are considered compatible
at the bytecode level may still break a codebase that depends on it.
Interestingly, this is not an issue intrinsic to the Scala language. In the
Java language binary compatibility does not imply source compatibility as well.
MiMa focuses on binary compatibility and currently provides no insight into
source compatibility.

### See also: TASTy-MiMa

For Scala 3, in addition to binary compatible, TASTy compatibility becomes
increasingly important. Another tool,
[TASTy-MiMa](https://github.com/scalacenter/tasty-mima), is designed to
automatically check TASTy compatibility in much the same way that MiMa checks
binary compatibility.

## Usage

### SBT

MiMa checks Scala 2.12, 2.13 and 3 artifacts. Its sbt plugin supports sbt 1.x and
sbt 2.0.7 or newer.

To use it add the following to your `project/plugins.sbt` file:

```
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "<version>")
```

Add the following to your `build.sbt` file:

```
mimaPreviousArtifacts := Set("com.example" %% "my-library" % "<version>")
```

and run `mimaReportBinaryIssues` to see something like the following:

```
[info] Found 4 potential binary incompatibilities
[error]  * method rollbackTransactionResource()resource.Resource in object resource.Resource does not have a   correspondent in new version
[error]  * method now()scala.util.continuations.ControlContext in trait resource.ManagedResourceOperations does not    have a correspondent in old version
[error]  * abstract method now()scala.util.continuations.ControlContext in trait resource.ManagedResource does not have a correspondent in old version
[error]  * method rollbackTransactionResource()resource.Resource in trait resource.MediumPriorityResourceImplicits does not have a correspondent in new version
[error] {file:/home/jsuereth/project/personal/scala-arm/}scala-arm/*:mima-report-binary-issues: Binary compatibility check failed!
[error] Total time: 15 s, completed May 18, 2012 11:32:29 AM
```

### Mill

A MiMa plugin for Mill is maintained at [lolgab/mill-mima](https://github.com/lolgab/mill-mima).

To use it add the following to your `build.sc`:

```scala
import $ivy.`com.github.lolgab::mill-mima::x.y.z`
import com.github.lolgab.mill.mima._
```

Please check [this page](https://github.com/lolgab/mill-mima) for further information.

### CLI

You can use MiMa using its command-line interface - it's the most straightforward way to compare two jars and see some human-readable descriptions of the issues.

You can launch it with Coursier:

```bash
cs launch com.typesafe:mima-cli_3:latest.release -- old.jar new.jar
```

Or create a reusable script:

```bash
cs bootstrap com.typesafe:mima-cli_3:latest.release --output mima
./mima old.jar new.jar
```

Here are the usage instructions:

```
Usage:

mima [OPTIONS] oldfile newfile

  oldfile: Old (or, previous) files - a JAR or a directory containing classfiles
  newfile: New (or, current) files - a JAR or a directory containing classfiles

Options:
  -cp CLASSPATH:
     Specify Java classpath, separated by ':'

  -v, --verbose:
     Show a human-readable description of each problem

  -f, --forward-only:
    Show only forward-binary-compatibility problems

  -b, --backward-only:
    Show only backward-binary-compatibility problems

  -g, --include-generics:
    Include generic signature problems, which may not directly cause bincompat
    problems and are hidden by default. Has no effect if using --forward-only.

  -j, --bytecode-names:
    Show bytecode names of fields and methods, rather than human-readable names
```


## Filtering binary incompatibilities

When MiMa reports a binary incompatibility that you consider acceptable, such as a change in an internal package,
you need to use the `mimaBinaryIssueFilters` setting to filter it out and get `mimaReportBinaryIssues` to
pass, like so:

```scala
import com.typesafe.tools.mima.core._

mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[MissingClassProblem]("com.example.mylibrary.internal.Foo"),
)
```

You may also use wildcards in the package and/or the top `Problem` parent type for such situations:

```scala
mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[Problem]("com.example.mylibrary.internal.*"),
)
```

### IncompatibleSignatureProblem

Most MiMa checks (`DirectMissingMethod`, `IncompatibleResultType`,
`IncompatibleMethType`, etc) are against the "method descriptor", which
is the "raw" type signature, without any information about generic parameters.

The `IncompatibleSignature` check compares the `Signature`, which includes the
full signature including generic parameters. This can catch real
incompatibilities, but also sometimes triggers for a change in generics that
would not in fact cause problems at run time. Notably, it will warn when
updating your project to scala 2.12.9+ or 2.13.1+,
see [this issue](https://github.com/scala-garden/mima/issues/423) for details.

The same setting also enables `IncompatibleClassSignature`, which compares the
`Signature` of a class rather than of a method. A client compiled against

```scala
class Base[T](val value: T)
class Public extends Base[String]("hi")
```

reads `value` as `Object` and casts it to `String`. Changing the parent to
`Base[Integer]` leaves every descriptor untouched, so nothing else in MiMa
notices, and the client gets a `ClassCastException`.

Only the type arguments a class passes to a parent it still has are compared.
Gaining a parent changes the class signature too, but no client can have been
compiled against it, so that alone is not reported.

You can opt-in to these checks by setting:

```scala
import com.typesafe.tools.mima.plugin.MimaKeys._

ThisBuild / mimaReportSignatureProblems := true
```

### Qualified private definitions

`private[foo]` is a Scala rule, not a JVM one. In bytecode these definitions are
public, so a client can end up depending on one even though it cannot name it.

MiMa ignores a qualified-private **member**, such as `private[foo] def`: nothing
outside `foo` can call it. Narrowing a public member to `private[foo]` is reported,
though, as `InaccessibleMethodProblem`: an already-compiled caller keeps linking, but
the member has left the API, and MiMa stops watching it from here on, so a later
removal would go unreported.

`protected[foo]` is not qualified private: a subclass anywhere can still reach it, so
MiMa treats it like plain `protected` and keeps checking it.

A nested `private class` is emitted ACC_PUBLIC too, and MiMa reads the same rules from
the pickle: nothing outside the enclosing class can name it, so it is ignored, and
narrowing a public nested class to `private` is reported the same way as narrowing it
to `private[foo]`.

A qualified-private **class** is checked only when it escapes, e.g., a public method
returns it, a public field holds it, or a public class extends it:

```scala
package foo
private[foo] class C { def bar(x: Int) = x }
object Lib { def go: C = new C }
```

`C` escapes through `Lib.go`, so a client can write `Lib.go.bar(1)`, and changing
`bar` breaks it. MiMa reports changes to `C` and to its public members. A
qualified-private class that never reaches a public signature is ignored.

Losing that last escape route is reported, as `ClassBecomesUnreachableProblem`:
dropping `Lib.go`, or narrowing a public class to `private[foo]` in the first place,
takes the class out of MiMa's sight, so nothing that happens to it afterwards can be
reported. Make the class public, or filter the problem if it really is internal from
here on.

A class that becomes `private[foo]` while it still escapes is not reported, because
MiMa carries on checking it just the same. An escape is a leak to close, not a
substitute for making the class public.

Escape detection reads the bytecode and the pickle, so it sees a class that leaks
only through a type alias or the bound of an abstract type member, neither of which
the classfile mentions:

```scala
object t {
  private[t] class C
  type K = C  // no mention of K in the classfile
  type L <: C // nor of L
}
```

To make MiMa treat qualified private declarations as private, including the
public members of a qualified private class, filter on
`Problem.isExternallyAccessible`:

```scala
import com.typesafe.tools.mima.core._

mimaBinaryIssueFilters += { (p: Problem) => p.isExternallyAccessible }
```

This drops the escaping classes too, so it can hide a real break: in the example
above it would silence the report about `C.bar`.

### Sealing

MiMa reports a change that would break a client implementing one of your types (a new
abstract method, a newly inherited one) only while a client can still write that
implementation. Once a type is sealed and every one of its subtypes is closed
(`final`, `sealed`, or no longer nameable from outside), nobody new can, so those
checks stop.

Clients that implemented it while it was open still exist, though, so the version
that closes the hierarchy is reported, as `HierarchyBecomesClosedProblem`. Sealing a
trait does it; so does making the last open subclass `final` or `private[foo]`. It is
the last version at which MiMa can protect those clients.

### Annotation-based exclusions

The `mimaExcludeAnnotations` setting can be used to tell MiMa to
ignore classes, objects, and methods that have a particular
annotation.  Such an annotation might typically have "experimental" or
"internal" in the name.

The setting is a `Seq[String]` containing fully qualified annotation
names.

Example:

```scala
mimaExcludeAnnotations += "scala.annotation.experimental"
```

The annotation is read from the version being checked against, so the release that
adds it is still checked in full; every release after it skips the annotated
definition entirely, with no report.

Caveat: `mimaExcludeAnnotations` is only implemented on Scala 3.

## Setting different mimaPreviousArtifacts

From time to time you may need to set `mimaPreviousArtifacts` according to some conditions.  For
instance, if you have already ported your project to Scala 2.13 and set it up for cross-building to Scala 2.13,
but still haven't cut a release, you may want to define `mimaPreviousArtifacts` according to the Scala version,
with something like:

```scala
mimaPreviousArtifacts := {
  if (CrossVersion.partialVersion(scalaVersion.value) == Some((2, 13)))
    Set.empty
  else
    Set("com.example" %% "my-library" % "1.2.3")
}
```

or with sbt's semantic version selectors:

```scala
import sbt.librarymanagement.{ SemanticSelector, VersionNumber }

mimaPreviousArtifacts := {
  if (VersionNumber(scalaVersion.value).matchesSemVer(SemanticSelector(">=2.13")))
    Set.empty
  else
    Set("com.example" %% "my-library" % "1.2.3")
}
```

## Make mimaReportBinaryIssues not fail

The setting `mimaFailOnNoPrevious` defaults to `true` and will make
`mimaReportBinaryIssues` fail if `mimaPreviousArtifacts` hasn't been set.

To make `mimaReportBinaryIssues` not fail you may want to do one of the following:

* set `mimaPreviousArtifacts` on all the projects that should be checking their binary compatibility
* avoid calling `mimaPreviousArtifacts` when binary compatibility checking isn't needed
* set `mimaFailOnNoPrevious := false` on specific projects that want to opt-out (alternatively `disablePlugins(MimaPlugin)`)
* set `ThisBuild / mimaFailOnNoPrevious := false`, which disables it build-wide, effectively reverting back to the previous behaviour

## Setting mimaPreviousArtifacts when name contains a "."

To refer to the project name in `mimaPreviousArtifacts`, use `moduleName` rather
than `name`, like

```scala
mimaPreviousArtifacts := Set(organization.value %% moduleName.value % "0.1.0")
```

Unlike `name`, `moduleName` escapes characters like `.`, and is the name
actually used by `publish` and `publishLocal` to publish your project. It's
also the value your users should use when adding your project to their
dependencies.
