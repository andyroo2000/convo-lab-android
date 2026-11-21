#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include <cstring>
#include <string>

extern "C" {
void *typing_create(const char *directory);
void typing_destroy(void *handle);
void typing_begin_operation(void *handle);
void typing_cancel_operation(void *handle);
char *typing_convert(void *handle, const char *request);
void typing_free(char *text);
}

static std::string utf8(JNIEnv *env, jbyteArray bytes) {
    const auto size = env->GetArrayLength(bytes);
    std::string value(size, '\0');
    env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(value.data()));
    return value;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cdcg_convolab_typing_NativeTyping_create(JNIEnv *env, jobject, jbyteArray path) {
    llama_log_set([](ggml_log_level level, const char *text, void *) {
        if (level == GGML_LOG_LEVEL_ERROR) __android_log_write(ANDROID_LOG_ERROR, "TypingEngine", text);
    }, nullptr);
    const auto directory = utf8(env, path);
    return reinterpret_cast<jlong>(typing_create(directory.c_str()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_cdcg_convolab_typing_NativeTyping_destroy(JNIEnv *, jobject, jlong handle) {
    if (handle) typing_destroy(reinterpret_cast<void *>(handle));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_cdcg_convolab_typing_NativeTyping_convert(JNIEnv *env, jobject, jlong handle, jbyteArray bytes) {
    if (!handle) return nullptr;
    const auto request = utf8(env, bytes);
    char *result = typing_convert(reinterpret_cast<void *>(handle), request.c_str());
    if (!result) return nullptr;
    const auto length = static_cast<jsize>(std::strlen(result));
    auto output = env->NewByteArray(length);
    if (output) env->SetByteArrayRegion(output, 0, length, reinterpret_cast<jbyte *>(result));
    typing_free(result);
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cdcg_convolab_typing_NativeTyping_beginOperation(JNIEnv *, jobject, jlong handle) {
    if (handle) typing_begin_operation(reinterpret_cast<void *>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_cdcg_convolab_typing_NativeTyping_cancelOperation(JNIEnv *, jobject, jlong handle) {
    if (handle) typing_cancel_operation(reinterpret_cast<void *>(handle));
}
