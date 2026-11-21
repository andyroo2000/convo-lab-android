#include "ggml-backend.h"
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

void initialize() {
    // Check hardware before loading any code compiled with optional instructions.
    if (supports_dot_product() && ggml_backend_load("libggml-cpu-dotprod.so")) {
        selected = "arm64-dotprod";
        available = true;
    } else if (ggml_backend_load("libggml-cpu.so")) {
        selected = "generic";
        available = true;
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
