package com.minijarvis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisBlue = Color(0xFF2962FF)
private val JarvisTeal = Color(0xFF00BFA5)

private val DarkColors = darkColorScheme(
    primary = JarvisBlue,
    secondary = JarvisTeal,
    background = Color(0xFF0E0F13),
    surface = Color(0xFF181A20)
)

private val LightColors = lightColorScheme(
    primary = JarvisBlue,
    secondary = JarvisTeal
)

@Composable
fun MiniJarvisTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
