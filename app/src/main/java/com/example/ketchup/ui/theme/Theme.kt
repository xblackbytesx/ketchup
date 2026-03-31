package com.example.ketchup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsOled = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = KetchupRed,
    onPrimary = Color.White,
    primaryContainer = KetchupRedDim,
    onPrimaryContainer = Color.White,
    secondary = KetchupRedLight,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderLight,
    error = ErrorRed,
    onError = Color.White,
)

private val OLEDColorScheme = DarkColorScheme.copy(
    background = OLEDBackground,
    surface = OLEDSurface,
    surfaceVariant = OLEDSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = KetchupRed,
    onPrimary = Color.White,
    primaryContainer = KetchupRedLight,
    onPrimaryContainer = Color.White,
    secondary = KetchupRedDim,
    onSecondary = Color.White,
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun KetchupTheme(
    theme: String = "dark",
    content: @Composable () -> Unit,
) {
    val isOled = theme == "oled"
    val colorScheme = when (theme) {
        "light" -> LightColorScheme
        "oled" -> OLEDColorScheme
        else -> DarkColorScheme // "dark" or "system" — Ketchup defaults dark
    }

    CompositionLocalProvider(LocalIsOled provides isOled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KetchupTypography,
            content = content,
        )
    }
}
