#include "llama.h"

extern "C" llama_model *__real_llama_model_load_from_file(const char *, llama_model_params);

// b4846's NONE split validates a GPU index even when n_gpu_layers is zero.
// Keep upstream Swift untouched; CPU loading must use the LAYER split instead.
extern "C" llama_model *__wrap_llama_model_load_from_file(const char *path, llama_model_params params) {
    params.n_gpu_layers = 0;
    params.split_mode = LLAMA_SPLIT_MODE_LAYER;
    return __real_llama_model_load_from_file(path, params);
}
