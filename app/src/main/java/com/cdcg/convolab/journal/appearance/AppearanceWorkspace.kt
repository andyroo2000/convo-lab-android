package com.cdcg.convolab.journal.appearance

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val LocalAppearanceStore = compositionLocalOf<AppearanceStore?> { null }

@Composable
fun AppearanceWorkspace(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { AppearanceStore(context.getSharedPreferences("journal-appearance", Context.MODE_PRIVATE)) }
    val typography by store.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppearanceStore provides store, LocalJournalTypography provides typography, content = content)
}
