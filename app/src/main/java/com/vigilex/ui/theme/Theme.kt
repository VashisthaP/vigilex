package com.vigilex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VigileXColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = NavyDark,
    background = NavyDark,
    surface = NavyMid,
    onSurface = Color.White,
    onBackground = Color.White,
    error = ErrorRed,
    outline = Color.White.copy(alpha = 0.2f)
)

@Composable
fun VigileXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VigileXColorScheme,
        typography = VigileXTypography,
        content = content
    )
}
