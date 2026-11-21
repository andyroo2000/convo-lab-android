package com.cdcg.convolab.typing

// OnDeviceEngine owns the handle and serializes all native calls and disposal.
internal object NativeTyping {
    init { System.loadLibrary("typing_jni") }
    external fun create(path: ByteArray): Long
    external fun backend(): String
    external fun convert(handle: Long, request: ByteArray): ByteArray?
    external fun beginOperation(handle: Long)
    external fun cancelOperation(handle: Long)
    external fun destroy(handle: Long)
}
