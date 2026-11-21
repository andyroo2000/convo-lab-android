package com.cdcg.convolab.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
    surface = JournalStyle.Paper,
    surfaceContainer = JournalStyle.Paper,
    surfaceContainerHigh = JournalStyle.Paper,
    onSurfaceVariant = JournalStyle.Ink,
    outline = JournalStyle.Border,
    onSurface = Color(0xFF11335C),
)

@Composable
fun ConvoLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConvoLabColors, typography = Typography(
        bodyLarge = JournalStyle.Body, bodyMedium = JournalStyle.Body,
        titleLarge = TextStyle(fontFamily = JournalBrand.Serif, fontSize = 26.sp),
        titleMedium = TextStyle(fontFamily = JournalBrand.Serif, fontSize = 23.sp),
        labelLarge = JournalStyle.Body,
    ), content = content)
}
