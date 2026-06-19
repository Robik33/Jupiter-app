package com.marketia.jupiter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JupiterColorScheme = darkColorScheme(
    primary = JupiterCyan,
    secondary = JupiterGreen,
    tertiary = JupiterPurple,
    background = JupiterBlack,
    surface = JupiterSurface,
    onPrimary = JupiterBlack,
    onSecondary = JupiterBlack,
    onBackground = JupiterWhite,
    onSurface = JupiterWhite,
)

@Composable
fun JupiterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JupiterColorScheme,
        typography = Typography,
        content = content
    )
}
