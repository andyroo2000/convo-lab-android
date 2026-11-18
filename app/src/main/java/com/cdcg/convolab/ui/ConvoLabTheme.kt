package com.cdcg.convolab.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ConvoLabColors = lightColorScheme(
    primary = Color(0xFF11335C),
    onPrimary = Color.White,
    secondary = Color(0xFF1AB2D1),
    background = Color(0xFFFBF5E0),
    onBackground = Color(0xFF11335C),
    surface = Color(0xFFFBF5E0),
    onSurface = Color(0xFF11335C),
)

@Composable
fun ConvoLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConvoLabColors, content = content)
}
