package com.cdcg.convolab.typing

internal object NativeTyping {
    init { System.loadLibrary("typing_jni") }
    external fun create(path: ByteArray): Long
    external fun convert(handle: Long, request: ByteArray): ByteArray?
    external fun destroy(handle: Long)
}
