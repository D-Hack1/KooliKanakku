package com.koolikanaku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val KooliKanakuLightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightTint,
    onPrimaryContainer = LightAccentInk,
    secondary = LightAccentInk,
    onSecondary = LightOnAccent,
    tertiary = LightGreen,
    onTertiary = LightOnAccent,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightTint,
    onSurfaceVariant = LightText2,
    outline = LightBorder,
    outlineVariant = LightDivider,
    error = LightSeverity,
    onError = LightOnAccent
)

private val KooliKanakuDarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkTint,
    onPrimaryContainer = DarkAccentInk,
    secondary = DarkAccentInk,
    onSecondary = DarkOnAccent,
    tertiary = DarkGreen,
    onTertiary = DarkOnAccent,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkTint,
    onSurfaceVariant = DarkText2,
    outline = DarkBorder,
    outlineVariant = DarkDivider,
    error = DarkSeverity,
    onError = DarkOnAccent
)

@Composable
fun KooliKanakuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KooliKanakuDarkColors else KooliKanakuLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KooliKanakuTypography,
        content = content
    )
}
