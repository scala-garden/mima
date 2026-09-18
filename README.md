# MiMa

MiMa (for "Migration Manager") is a tool for identifying [binary incompatibilities][] in Scala libraries.

[binary incompatibilities]: https://docs.scala-lang.org/overviews/core/binary-compatibility-for-library-authors.html

It's pronounced _MEE-ma_.

## What it is

MiMa compares the classfiles of two versions of a library. It reports changes that make
code compiled against the old version fail with the new one, with a `LinkageError` such as
`NoSuchMethodError` or `AbstractMethodError`.

MiMa does not check behaviour: a method that keeps its signature but does something else
is not reported. It does not check source compatibility either: a binary compatible
change can still stop client code from compiling.

### Scala clients, not Java clients

MiMa checks your API as Scala code sees it. Some changes break only Java clients, and
MiMa does not report them:

- `private[foo]` definitions and nested `private` classes are public in bytecode. Java
  code can use them, MiMa ignores them. See
  [Qualified private definitions](#qualified-private-definitions).
- Java code can implement a sealed type. MiMa does not report new abstract methods in a
  sealed hierarchy. See [Sealing](#sealing).
- Java calls an object's methods through static forwarders in the companion class. MiMa
  checks the object's methods, not the forwarders. A forwarder can disappear while the
  method stays.
- A class gets a public copy of each `private[foo]` method of its traits. MiMa ignores
  these.
- MiMa ignores methods with a `$` in their name, except extension methods, default
  argument getters and `$init$`. Scala code cannot see them.
- Generic signatures, which javac uses, are only compared on request. See
  [IncompatibleSignatureProblem](#incompatiblesignatureproblem).

### See also: TASTy-MiMa

Scala 3 compiles against TASTy, which holds more than the bytecode: exact Scala types,
and the bodies of `inline` methods.
[TASTy-MiMa](https://github.com/scalacenter/tasty-mima) checks TASTy compatibility the way
MiMa checks bytecode. Use both for a Scala 3 library.

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
[error] my-library: Failed binary compatibility check against com.example:my-library_3:1.0.0! Found 2 potential problems
[error]  * method close()Unit in class com.example.Resource does not have a correspondent in current version
[error]  * abstract method reset()Unit in trait com.example.Pool is present only in current version
[error] To accept the incompatible changes above, add the lines below to mimaBinaryIssueFilters, or to src/main/mima-filters/<version>.backwards.excludes.
[error]    ProblemFilters.exclude[DirectMissingMethodProblem]("com.example.Resource.close()Unit"),
[error]    ProblemFilters.exclude[ReversedMissingMethodProblem]("com.example.Pool.reset()Unit"),
```

Each problem comes with a filter to accept it, see
[Filtering binary incompatibilities](#filtering-binary-incompatibilities).

MiMa checks backward compatibility, that code compiled against the previous version keeps
working. `mimaCheckDirection := "forward"` checks the other direction, `"both"` checks both.

### Mill

A MiMa plugin for Mill is maintained at [lolgab/mill-mima](https://github.com/lolgab/mill-mima).

To use it add the following to your `build.sc`:

```scala
import $ivy.`com.github.lolgab::mill-mima::x.y.z`
import com.github.lolgab.mill.mima._
```

Please check [this page](https://github.com/lolgab/mill-mima) for further information.

### CLI

The command-line interface compares two jars directly. Launch it with Coursier:

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

  -s, --suggestions:
    Print the lines to add to a build to accept the problems

Exit code: 0 if no problems were found, 1 if there were, 2 for a usage error.
```


## Filtering binary incompatibilities

A problem you accept, such as a change in an internal package, goes in
`mimaBinaryIssueFilters`:

```scala
import com.typesafe.tools.mima.core._

mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[MissingClassProblem]("com.example.mylibrary.internal.Foo"),
)
```

`*` stands for any part of a name, and `Problem` for any kind of problem:

```scala
mimaBinaryIssueFilters += ProblemFilters.exclude[Problem]("com.example.mylibrary.internal.*")
```

Names are the ones the report prints, signature included, so a filter covers one method:

```scala
ProblemFilters.exclude[DirectMissingMethodProblem]("com.example.mylibrary.Foo.bar(Int)Int")
```

Leaving the signature out covers every overload of `bar`.

### Filter files

Filters can live in `src/main/mima-filters` instead of the build, one `exclude` per line,
`#` or `//` for comments. A file is named after the release its filters are for:
`1.1.0.backwards.excludes` applies while `mimaPreviousArtifacts` names 1.1.0 or anything
before it, and is ignored once it moves past. `forwards` and `both` name the other
directions, and `1.1.x` stands for every patch release of 1.1.

A line pasted from the report, trailing comma and all, is accepted.

### IncompatibleSignatureProblem

Most MiMa checks compare erased types. `def names: List[String]` and
`def names: List[Int]` look the same to them, yet a client compiled against the first
casts each element to `String` and fails with a `ClassCastException`.

The compiler also writes a Java generic signature, the classfile's `Signature` attribute,
which maps the Scala type as far as Java generics can express it. MiMa compares it with

```scala
ThisBuild / mimaReportSignatureProblems := true
```

or `-g` on the command line. It reports differences as `IncompatibleSignatureProblem`,
and changed type arguments of a parent class as `IncompatibleClassSignatureProblem`:

```scala
class Base[T](val value: T)
class Public extends Base[String]("hi") // changed to Base[Integer]
```

The check is off by default because the mapping is imperfect:

- It misses changes. A primitive type argument is written as `Object`, so `List[Int]` to
  `List[Long]` looks unchanged, though a client that unboxes the element fails.
- It reports changes that break nothing. Only a client that casts the value fails, and
  some `Signature` changes are not type changes: Scala 2.12.9 and 2.13.1 changed how
  value classes are written, see [#423](https://github.com/scala-garden/mima/issues/423).

For Scala 3, [TASTy-MiMa](#see-also-tasty-mima) compares the Scala types themselves.

### Qualified private definitions

`private[foo]` is a Scala rule, not a JVM one. In bytecode these definitions are
public, so a client can end up depending on one even though it cannot name it.

MiMa ignores a qualified-private **member**, such as `private[foo] def`: no Scala code
outside `foo` can call it. Narrowing a public member to `private[foo]` is reported,
though, as `MethodNoLongerCheckedProblem`: an already-compiled caller keeps linking, but
the member has left the API, and MiMa stops watching it from here on, so a later
removal would go unreported.

`protected[foo]` is not qualified private: a subclass anywhere can still reach it, so
MiMa treats it like plain `protected` and keeps checking it.

A private member marked [`@publicInBinary`](https://docs.scala-lang.org/sips/binary-api.html)
(Scala 3.4+) is the exception: an `inline` method outside the scope calls it directly
once inlined, so MiMa checks it like a public member. Narrowing a public member to
`@publicInBinary private[foo]` keeps it in the binary API and is not reported; dropping
the annotation is.

A nested `private class` is emitted ACC_PUBLIC too, and MiMa reads the same rules from
the pickle: no Scala code outside the enclosing class can name it, so it is ignored, and
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
`bar` breaks it. MiMa reports changes to `C` and to its public members, and every report
says how the class escapes, since the class alone does not show it:

```
method bar(Int)Int in private[..] class foo.C does not have a correspondent in new version (foo.C escapes through foo.Lib.go)
```

A qualified-private class that never reaches a public signature is ignored.

Losing that last escape route is reported, as `ClassNoLongerCheckedProblem`:
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
(`final`, `sealed`, or no longer nameable from outside), no new Scala code can, so
those checks stop. Java code still can, since javac ignores `sealed`.

Clients that implemented it while it was open still exist, though, so the version
that closes the hierarchy is reported, as `HierarchyNoLongerCheckedProblem`: it is the
last one at which MiMa can tell you that an abstract method you add would break them.
Sealing a trait closes a hierarchy; so does making its last open subclass `final` or
`private[foo]`.

### Keeping a definition checked

Narrowing a public definition to `private[foo]`, or closing a hierarchy, is binary compatible: the
bytecode stays public. But MiMa reports anyway, as `MethodNoLongerCheckedProblem`,
`ClassNoLongerCheckedProblem` or `HierarchyNoLongerCheckedProblem`, because from that version on
it stops watching the definition, while code compiled against an earlier release still calls it.
A later removal would go unreported.

Filtering such a report would be counter-productive. It silences the notice, the definition
stays out of sight, and once `mimaPreviousArtifacts` moves to a new version, the filter is dead.
`mimaBinaryApi` solves this problem:

```scala
mimaBinaryApi += BinaryApi.keep[MethodNoLongerCheckedProblem]("foo.C.bar(Int)Int")
```

MiMa now checks `bar` as it checks a public method, and the message about narrowing is silenced.
The `keep` entry names the kind of problem MiMa continues checking:

| entry | keeps |
| --- | --- |
| `keep[ClassNoLongerCheckedProblem]("foo.C")` | the type checked, with its members and its accessible nested classes |
| `keep[MethodNoLongerCheckedProblem]("foo.C.bar(Int)Int")` | the method checked |
| `keep[HierarchyNoLongerCheckedProblem]("foo.T")` | an abstract method added to the type later reported |

Names work as in filters, and `*` stands for any part of a name. An object has two classes,
`O` and `O$`, so keeping it takes two entries; keeping a method of an object takes one. Entries
can also go in `src/main/mima-filters/binary-api`, one per line, `#` or `//` for comments.

A method entry includes the signature, so it keeps one method. Without it, the entry keeps every
overload of that name, including any that was never part of the API, and removing one of those is
then reported.

A `keep` entry that is unused is reported as having no effect. It can either be a typo, or that the
named definition is public again, or gone.

On Scala 3.4+,
[`@publicInBinary`](https://docs.scala-lang.org/sips/binary-api.html) says the same about a method
at the definition site, and MiMa honours it.

### Annotation-based exclusions

`mimaExcludeAnnotations` makes MiMa ignore classes, objects, methods and vals that carry
one of the given annotations, such as an "experimental" or "internal" marker:

```scala
mimaExcludeAnnotations += "scala.annotation.experimental"
```

The annotation is read from the version being checked against, so the release that
adds it is still checked in full; every release after it skips the annotated
definition entirely, with no report.

On Scala 2, this works for classes and objects only, not for methods and vals.

## More on mimaPreviousArtifacts

It can depend on other settings. For example, a cross-built project with no Scala 3
release yet has nothing to compare its Scala 3 build against:

```scala
mimaPreviousArtifacts := {
  if (scalaBinaryVersion.value == "3") Set.empty
  else Set("com.example" %% "my-library" % "1.2.3")
}
```

To name the project itself, use `moduleName`, not `name`: it escapes characters like `.`,
and is the name `publish` uses, so it is also what your users depend on.

```scala
mimaPreviousArtifacts := Set(organization.value %% moduleName.value % "0.1.0")
```

## Not failing the build

`mimaReportBinaryIssues` fails on a problem, and also when `mimaPreviousArtifacts` is
empty, so that a project does not go unchecked by accident. To report problems without
failing, set `mimaFailOnProblem := false`. For a project with nothing to compare against,
set `mimaFailOnNoPrevious := false`, or `disablePlugins(MimaPlugin)`. Setting it on
`ThisBuild` does that for the whole build.
