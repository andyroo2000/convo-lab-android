package com.cdcg.convolab.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cdcg.convolab.auth.AuthInput
import com.cdcg.convolab.ui.JournalBrand
import com.cdcg.convolab.ui.JournalStyle

@Composable
internal fun SettingsField(input: AuthInput, value: String, onEdit: (String) -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(input.label)
        BasicTextField(value, onEdit, Modifier.settingsField(input.label), enabled = enabled, singleLine = true, interactionSource = interaction,
            textStyle = JournalStyle.Body.copy(color = JournalBrand.Ink), cursorBrush = SolidColor(JournalBrand.Blue),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = input.keyboard),
            decorationBox = { FieldSurface(focused) { it() } })
    }
}

@Composable
internal fun SettingsPasswordField(label: String, state: TextFieldState, enabled: Boolean, placeholder: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!placeholder) Text(label)
        BasicSecureTextField(state, Modifier.settingsField(label), enabled = enabled, interactionSource = interaction,
            textStyle = JournalStyle.Body.copy(color = JournalBrand.Ink), cursorBrush = SolidColor(JournalBrand.Blue),
            decorator = { inner -> FieldSurface(focused) {
                if (placeholder && state.text.isEmpty()) Text(label, color = JournalBrand.Ink.copy(alpha = .5f))
                inner()
            } })
    }
}

private fun Modifier.settingsField(label: String) = fillMaxWidth().testTag("auth-$label").semantics { contentDescription = label }

@Composable
private fun FieldSurface(focused: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .7f), shape).border(if (focused) 2.dp else 1.dp, if (focused) JournalStyle.Green else Color(0xFFD1D1D1), shape)
        .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { content() }
}
