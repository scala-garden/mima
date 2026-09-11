package com.typesafe.tools.mima.core

import scala.reflect.NameTransformer

private[core] abstract class InfoLike {
  /** The name as found in the bytecode. */
  def bytecodeName: String

  protected def flags: Int

  /** The name as found in the original Scala source. */
  final def decodedName: String = NameTransformer.decode(bytecodeName)

  // what the bytecode says; the pickle can disagree, see ClassInfo.isScopedPrivate
  final def isBytecodePublic: Boolean    = ClassfileParser.isPublic(flags)
  final def isBytecodePrivate: Boolean   = ClassfileParser.isPrivate(flags)
  final def isBytecodeProtected: Boolean = ClassfileParser.isProtected(flags)
  final def isBytecodeStatic: Boolean    = ClassfileParser.isStatic(flags)
  final def isBytecodeFinal: Boolean     = ClassfileParser.isFinal(flags)
  final def isBytecodeBridge: Boolean    = ClassfileParser.isBridge(flags)
  final def isBytecodeDeferred: Boolean  = ClassfileParser.isDeferred(flags)
  final def isBytecodeSynthetic: Boolean = ClassfileParser.isSynthetic(flags)

  final def isBytecodeLessVisibleThan(that: InfoLike) = {
    (!isBytecodePublic && that.isBytecodePublic) || (isBytecodePrivate && that.isBytecodeProtected)
  }
}
