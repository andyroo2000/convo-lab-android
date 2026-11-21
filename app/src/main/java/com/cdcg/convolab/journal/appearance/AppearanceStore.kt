package com.cdcg.convolab.journal.appearance

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-local presentation preferences only; no account credentials or writing. */
class AppearanceStore(private val preferences: SharedPreferences) {
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    fun change(value: JournalTypography) {
        val next = JournalTypography.restored(value.font.name, value.size)
        mutableState.value = next
        preferences.edit { putString("font", next.font.name); putInt("size", next.size) }
    }
    private fun read(): JournalTypography = try {
        JournalTypography.restored(preferences.getString("font", null), preferences.getInt("size", 20))
    } catch (_: ClassCastException) { JournalTypography() }
}
