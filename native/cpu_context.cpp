#include "llama.h"
#include "ggml-cpu.h"
#include <algorithm>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <unistd.h>

extern "C" llama_context *__real_llama_init_from_model(llama_model *, llama_context_params);
extern "C" void __real_llama_free(llama_context *);

namespace {
using Pool = std::unique_ptr<ggml_threadpool, decltype(&ggml_threadpool_free)>;
std::mutex ownership;
std::unordered_map<llama_context *, Pool> pools;

Pool make_pool(int threads) {
    auto backend = ggml_backend_reg_by_name("CPU");
    if (!backend) return Pool(nullptr, nullptr);
    auto create = reinterpret_cast<decltype(&ggml_threadpool_new)>(
        ggml_backend_reg_get_proc_address(backend, "ggml_threadpool_new"));
    auto destroy = reinterpret_cast<decltype(&ggml_threadpool_free)>(
        ggml_backend_reg_get_proc_address(backend, "ggml_threadpool_free"));
    if (!create || !destroy) return Pool(nullptr, nullptr);
    // Allow the diagnostic worker sweep without oversubscribing small devices.
    // Graphs use the context's actual count; unused workers sleep between edits.
    auto capacity = std::max(threads, static_cast<int>(std::min(8L, sysconf(_SC_NPROCESSORS_ONLN))));
    auto parameters = ggml_threadpool_params_default(capacity);
    parameters.poll = 0;
    return Pool(create(&parameters), destroy);
}

Pool take_pool(llama_context *context) {
    std::lock_guard<std::mutex> lock(ownership);
    auto found = pools.find(context);
    if (found == pools.end()) return Pool(nullptr, nullptr);
    auto pool = std::move(found->second);
    pools.erase(found);
    return pool;
}
}

extern "C" llama_context *__wrap_llama_init_from_model(llama_model *model, llama_context_params parameters) {
    auto context = __real_llama_init_from_model(model, parameters);
    if (!context) return nullptr;
    auto pool = make_pool(std::max(parameters.n_threads, parameters.n_threads_batch));
    if (pool) {
        llama_attach_threadpool(context, pool.get(), pool.get());
        std::lock_guard<std::mutex> lock(ownership);
        pools.emplace(context, std::move(pool));
    }
    return context;
}

extern "C" void __wrap_llama_free(llama_context *context) {
    if (!context) return;
    auto pool = take_pool(context);
    llama_detach_threadpool(context);
    __real_llama_free(context);
    // Pool destruction joins its workers after the context has stopped using it.
}
