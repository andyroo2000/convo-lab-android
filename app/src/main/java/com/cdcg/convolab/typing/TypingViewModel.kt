package com.cdcg.convolab.typing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TypingViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = OnDeviceEngine(application)
    val notebook = MutableStateFlow("")
    val session = TypingSession(engine, viewModelScope)
    private val mutableReport = MutableStateFlow("")
    val report = mutableReport.asStateFlow()
    private val mutableProgress = MutableStateFlow<String?>(null)
    val progress = mutableProgress.asStateFlow()

    init { session.prepare() }

    fun benchmark() {
        if (mutableProgress.value != null || !session.state.value.ready) return
        session.edit("")
        viewModelScope.launch {
            mutableProgress.value = "Preparing benchmark…"
            try {
                mutableReport.value = TypingBenchmark(getApplication(), engine).run(session.state.value.preloadMs) { done, total ->
                    mutableProgress.value = "Benchmark $done / $total"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableReport.value = "Benchmark failed. Reopen the playground and try again."
            } finally {
                mutableProgress.value = null
            }
        }
    }

    override fun onCleared() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { engine.close() }
    }
}
