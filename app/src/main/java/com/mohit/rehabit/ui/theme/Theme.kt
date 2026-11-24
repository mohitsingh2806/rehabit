package com.mohit.rehabit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ForestGreen = Color(0xFF2E7D32)
val Coral = Color(0xFFFF7043)

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Coral,
    // Dark text on coral: white on #FF7043 is below 3:1 contrast.
    onSecondary = Color(0xFF3B0A00),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF380D00),
    tertiary = Color(0xFF8D5A00),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2C1700),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD88F),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFFFB59D),
    onSecondary = Color(0xFF5C1900),
    secondaryContainer = Color(0xFF7A2E14),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFFFB951),
    tertiaryContainer = Color(0xFF6A4300),
    onTertiaryContainer = Color(0xFFFFDDB3),
)

@Composable
fun RehabitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
