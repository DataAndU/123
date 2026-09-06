package com.gemmaassistant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GemmaViolet = Color(0xFF2962FF)
private val GemmaTeal = Color(0xFF00BFA5)

private val DarkColors = darkColorScheme(
    primary = GemmaViolet,
    secondary = GemmaTeal,
    background = Color(0xFF0E0F13),
    surface = Color(0xFF181A20)
)

private val LightColors = lightColorScheme(
    primary = GemmaViolet,
    secondary = GemmaTeal
)

@Composable
fun GemmaAssistantTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
