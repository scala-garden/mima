package com.typesafe.tools.mima.core

import java.io.IOException
import java.nio.file.Files

import ClassfileConstants._

final class ClassfileParser private (in: BufferReader, pool: ConstantPool) {
  import ClassfileParser._

  private def parseClass(clazz: ClassInfo): Unit = {
    val flags = in.nextChar
    clazz._flags = flags
    in.skip(2) // external name index

    clazz._superClass = parseSuperClass(clazz, flags)
    clazz._interfaces = parseInterfaces()
    clazz._fields = parseMembers[FieldInfo](clazz, (_, _) => ())
    clazz._methods = parseMembers[MethodInfo](clazz, (n, d) => clazz._privateInBytecode += ((n, paramsCount(d))))
    parseClassAttributes(clazz)
  }

  private def parseSuperClass(clazz: ClassInfo, flags: Int): ClassInfo = {
    if (isAnnotation(flags)) {
      in.skip(2)
      clazz.owner.definitions.AnnotationClass
    } else pool.getSuperClass(in.nextChar)
  }

  private def parseInterfaces(): List[ClassInfo] = {
    List.fill(in.nextChar)(pool.getSuperClass(in.nextChar))
  }

  private def parseMembers[A <: MemberInfo: MkMember](clazz: ClassInfo, notePrivate: (String, String) => Unit)
      : Members[A] = {
    val members = for {
      _ <- 0.until(in.nextChar).iterator
      flags = in.nextChar
      _ = if (isPrivate(flags)) {
        notePrivate(pool.getName(in.nextChar), pool.getName(in.nextChar)); parseAttributes(_ => ())
      }
      if !isPrivate(flags)
    } yield parseMember[A](clazz, flags)
    new Members(members.toList)
  }

  /** How many parameters a method descriptor names, without building any types. */
  private def paramsCount(descriptor: String): Int = {
    var i = 1
    var n = 0
    while (descriptor.charAt(i) != ')') {
      while (descriptor.charAt(i) == '[') i += 1
      if (descriptor.charAt(i) == 'L') i = descriptor.indexOf(';', i)
      i += 1
      n += 1
    }
    n
  }

  private def parseMember[A <: MemberInfo: MkMember](clazz: ClassInfo, flags: Int): A = {
    val name       = pool.getName(in.nextChar)
    val descriptor = pool.getExternalName(in.nextChar)
    val member     = implicitly[MkMember[A]].make(clazz, name, flags, descriptor)
    parseMemberAttributes(member)
    member
  }

  private def parseClassAttributes(clazz: ClassInfo) = {
    var isScala           = false
    var runtimeAnnotStart = -1
    parseAttributes {
      case RuntimeAnnotationATTR => runtimeAnnotStart = in.bp
      case ScalaSignatureATTR    => isScala = true; clazz._isScala = true
      case ScalaATTR             => clazz._isScala = true
      case EnclosingMethodATTR   => clazz._isLocalClass = true
      case InnerClassesATTR      => clazz._innerClasses = parseInnerClasses(clazz)
      case SignatureATTR         => clazz._signature = Signature(pool.getName(in.nextChar))
      case TASTYATTR             => clazz._isScala = true; parseTasty(clazz)
      case _                     =>
    }
    if (isScala)
      in.atIndex(runtimeAnnotStart)(parsePickle(clazz))
  }

  private def parseMemberAttributes(member: MemberInfo) = {
    parseAttributes {
      case DeprecatedATTR => member._isDeprecated = true
      case SignatureATTR  => member._signature = Signature(pool.getName(in.nextChar))
      case _              =>
    }
  }

  private def parseAttributes(processAttr: String => Unit) = {
    for (_ <- 0 until in.nextChar) {
      val attrIdx = in.nextChar
      val attrLen = in.nextInt
      val attrEnd = in.bp + attrLen
      processAttr(pool.getName(attrIdx))
      in.bp = attrEnd
    }
  }

  private def parseInnerClasses(c: ClassInfo) = {
    val innerClasses = for {
      _ <- 0 until in.nextChar
      (innerIndex, outerIndex, innerNameIndex) = (in.nextChar, in.nextChar, in.nextChar)
      _ = in.skip(2) // inner class flags
      if innerIndex != 0 && outerIndex != 0 && innerNameIndex != 0
      if pool.getClassName(outerIndex) == c.bytecodeName
    } yield pool.getClassName(innerIndex)
    if (innerClasses.contains(c.bytecodeName))
      c._isTopLevel = false // an inner class lists itself in InnerClasses
    innerClasses
  }

  private def parsePickle(clazz: ClassInfo) = {
    def parseScalaSigBytes() = {
      in.acceptByte(STRING_TAG, s" for ${clazz.description}")
      pool.getBytes(in.nextChar)
    }

    def parseScalaLongSigBytes() = {
      in.acceptByte(ARRAY_TAG, s" for ${clazz.description}")
      val entries = for (_ <- 0 until in.nextChar) yield {
        in.acceptByte(STRING_TAG, s" for ${clazz.description}")
        in.nextChar.toInt
      }
      pool.getBytes(entries.toList)
    }

    def checkScalaSigAnnotArg() = {
      in.acceptChar(1, s" (ScalaSignature's arguments)")
      val name = pool.getName(in.nextChar)
      assert(name == "bytes", s"ScalaSignature argument has name $name")
    }

    def skipAnnotArg(): Unit = in.nextByte match {
      case BOOL_TAG | BYTE_TAG    => in.skip(2)
      case CHAR_TAG | SHORT_TAG   => in.skip(2)
      case INT_TAG | LONG_TAG     => in.skip(2)
      case FLOAT_TAG | DOUBLE_TAG => in.skip(2)
      case STRING_TAG | CLASS_TAG => in.skip(2)
      case ENUM_TAG               => in.skip(4)
      case ARRAY_TAG              => for (_ <- 0 until in.nextChar) skipAnnotArg()
      case ANNOTATION_TAG         => in.skip(2) /* type */; skipAnnotArgs()
    }

    def skipAnnotArgs() = for (_ <- 0 until in.nextChar) { in.skip(2); skipAnnotArg() }

    val numAnnots = in.nextChar
    var i         = 0
    var bytes     = new Array[Byte](0)
    while (i < numAnnots && bytes.length == 0) {
      pool.getExternalName(in.nextChar) match {
        case ScalaSignatureAnnot     => checkScalaSigAnnotArg(); bytes = parseScalaSigBytes()
        case ScalaLongSignatureAnnot => checkScalaSigAnnotArg(); bytes = parseScalaLongSigBytes()
        case _                       => skipAnnotArgs()
      }
      i += 1
    }
    MimaUnpickler.unpickleClass(new PickleBuffer(bytes), clazz, in.file.path)
  }

  private def parseTasty(clazz: ClassInfo) = {
    // TODO: sanity check UUIDs
    val tpath = pool.file.jpath.resolveSibling(pool.file.name.stripSuffix(".class") + ".tasty")
    val bytes = Files.readAllBytes(tpath)
    TastyUnpickler.unpickleClass(new TastyReader(bytes), clazz, tpath.toString)
  }

  private final val ScalaSignatureAnnot     = "Lscala.reflect.ScalaSignature;"
  private final val ScalaLongSignatureAnnot = "Lscala.reflect.ScalaLongSignature;"

  private final val DeprecatedATTR        = "Deprecated"
  private final val EnclosingMethodATTR   = "EnclosingMethod"
  private final val InnerClassesATTR      = "InnerClasses"
  private final val RuntimeAnnotationATTR = "RuntimeVisibleAnnotations"
  // every classfile scalac emits carries one of these: the top-level class of a compilation
  // unit holds the pickle, the rest just the marker
  private final val ScalaATTR          = "Scala"
  private final val ScalaSignatureATTR = "ScalaSig"
  private final val SignatureATTR      = "Signature"
  private final val TASTYATTR          = "TASTY"
}

object ClassfileParser {
  private[core] def parseInPlace(clazz: ClassInfo, file: AbsFile): Unit = {
    val in = new BufferReader(file)
    parseHeader(in, file)
    val pool   = ConstantPool.parseNew(clazz.owner.definitions, in)
    val parser = new ClassfileParser(in, pool)
    parser.parseClass(clazz)
  }

  def isPublic(flags: Int)     = 0 != (flags & JAVA_ACC_PUBLIC)
  def isPrivate(flags: Int)    = 0 != (flags & JAVA_ACC_PRIVATE)
  def isProtected(flags: Int)  = 0 != (flags & JAVA_ACC_PROTECTED)
  def isStatic(flags: Int)     = 0 != (flags & JAVA_ACC_STATIC)
  def isFinal(flags: Int)      = 0 != (flags & JAVA_ACC_FINAL)
  def isBridge(flags: Int)     = 0 != (flags & JAVA_ACC_BRIDGE)
  def isInterface(flags: Int)  = 0 != (flags & JAVA_ACC_INTERFACE)
  def isDeferred(flags: Int)   = 0 != (flags & JAVA_ACC_ABSTRACT)
  def isSynthetic(flags: Int)  = 0 != (flags & JAVA_ACC_SYNTHETIC)
  def isAnnotation(flags: Int) = 0 != (flags & JAVA_ACC_ANNOTATION)

  private def parseHeader(in: BufferReader, file: AbsFile) = {
    val magic = in.nextInt
    if (magic != JAVA_MAGIC)
      throw new IOException(
        s"class file '$file' has wrong magic number 0x${magic.toHexString}, " +
          s"should be 0x${JAVA_MAGIC.toHexString}")
    in.skip(2) // minorVersion
    in.skip(2) // majorVersion
  }

  private trait MkMember[A] {
    def make(owner: ClassInfo, bytecodeName: String, flags: Int, descriptor: String): A
  }

  private implicit def mkFieldInfo: MkMember[FieldInfo]   = new FieldInfo(_, _, _, _)
  private implicit def mkMethodInfo: MkMember[MethodInfo] = new MethodInfo(_, _, _, _)
}
