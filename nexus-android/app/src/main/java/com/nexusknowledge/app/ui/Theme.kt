package com.nexusknowledge.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mismos colores que la app web: tinta azul, papel y subrayador amarillo.
val Ink = Color(0xFF17233B)
val Paper = Color(0xFFF7F9FC)
val Highlighter = Color(0xFFFFE45C)
val Night = Color(0xFF111A2C)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    secondary = Color(0xFF3A4A6B), onSecondary = Color.White,
    tertiary = Highlighter, onTertiary = Ink,
    secondaryContainer = Highlighter, onSecondaryContainer = Ink,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
)

private val Dark = darkColorScheme(
    primary = Highlighter, onPrimary = Ink,
    secondary = Color(0xFFB8C4DD), onSecondary = Ink,
    tertiary = Highlighter, onTertiary = Ink,
    secondaryContainer = Color(0xFF3A4A6B), onSecondaryContainer = Color.White,
    background = Night, onBackground = Color(0xFFE8ECF4),
    surface = Night, onSurface = Color(0xFFE8ECF4),
)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
