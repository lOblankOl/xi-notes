package com.xinotes.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val XiDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    secondary = AccentSecondary,
    background = BackgroundMain,
    surface = BackgroundPanel,
    onPrimary = BackgroundMain,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun XiNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XiDarkColorScheme,
        content = content
    )
}
