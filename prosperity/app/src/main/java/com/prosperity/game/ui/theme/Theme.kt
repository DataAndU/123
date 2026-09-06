package com.prosperity.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ProsperityGreen = Color(0xFF1B5E20)
val ProsperityGold = Color(0xFFD4A017)
val PositiveGreen = Color(0xFF2E7D32)
val NegativeRed = Color(0xFFC62828)

private val DarkColors = darkColorScheme(
    primary = ProsperityGreen,
    secondary = ProsperityGold,
    background = Color(0xFF0D1210),
    surface = Color(0xFF152119)
)

private val LightColors = lightColorScheme(
    primary = ProsperityGreen,
    secondary = ProsperityGold
)

@Composable
fun ProsperityTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
