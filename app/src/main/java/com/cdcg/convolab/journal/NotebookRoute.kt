package com.cdcg.convolab.journal

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cdcg.convolab.auth.Account
import com.cdcg.convolab.auth.SessionController

@Composable
fun NotebookRoute(account: Account, sessions: SessionController, menu: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val owner = remember(account.id) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model = remember(owner) { ViewModelProvider(owner, viewModelFactory {
        initializer { NotebookViewModel(context, sessions) }
    })[NotebookViewModel::class.java] }
    val controller by model.controller.collectAsStateWithLifecycle()
    val current = controller
    if (current == null) CircularProgressIndicator() else {
        val state by current.state.collectAsStateWithLifecycle()
        FlushOnStop(current)
        NotebookScreen(state, current, menu)
    }
}

@Composable
private fun FlushOnStop(controller: NotebookController) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
