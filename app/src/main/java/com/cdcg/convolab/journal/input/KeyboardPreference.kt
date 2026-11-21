package com.cdcg.convolab.journal.input

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JournalKeyboard { Choose, BuiltIn, Device }
class KeyboardPreference(private val preferences: SharedPreferences) {
    private val mutable = MutableStateFlow(restore())
    val state = mutable.asStateFlow()
    fun change(mode: JournalKeyboard) {
        preferences.edit { putString("mode", mode.name) }
        mutable.value = mode
    }
    private fun restore(): JournalKeyboard = try {
        JournalKeyboard.entries.firstOrNull { it.name == preferences.getString("mode", null) } ?: JournalKeyboard.Choose
    } catch (_: ClassCastException) { JournalKeyboard.Choose }
}
