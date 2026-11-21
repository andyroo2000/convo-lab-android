#include "ggml-backend.h"
#include <android/log.h>
#include <mutex>
#if defined(__aarch64__)
#include <asm/hwcap.h>
#include <sys/auxv.h>
#endif

namespace {
std::once_flag initialization;
const char *selected = "unavailable";
bool available = false;

bool supports_dot_product() {
#if defined(__aarch64__)
    return (getauxval(AT_HWCAP) & HWCAP_ASIMDDP) != 0;
#else
    return false;
#endif
}

bool load_backend(const char *library, const char *name) {
    if (!ggml_backend_load(library)) return false;
    selected = name;
    available = true;
    __android_log_print(ANDROID_LOG_INFO, "TypingEngine", "CPU backend: %s", selected);
    return true;
}

void initialize() {
    // Check hardware before loading any code compiled with optional instructions.
    if (supports_dot_product()) {
        if (load_backend("libggml-cpu-dotprod.so", "arm64-dotprod")) return;
        __android_log_write(ANDROID_LOG_WARN, "TypingEngine", "Dot-product library could not load; trying generic CPU");
    } else {
        __android_log_write(ANDROID_LOG_INFO, "TypingEngine", "CPU has no ARM dot-product support; using generic CPU");
    }
    if (!load_backend("libggml-cpu.so", "generic")) {
        __android_log_write(ANDROID_LOG_ERROR, "TypingEngine", "No CPU backend could load");
    }
}
}

extern "C" bool typing_load_cpu_backend() {
    std::call_once(initialization, initialize);
    return available;
}

extern "C" const char *typing_cpu_backend() {
    typing_load_cpu_backend();
    return selected;
}
