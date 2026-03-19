package com.memosnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightBgCard,
    background = LightBgPrimary,
    onBackground = LightTextPrimary,
    surface = LightBgCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBgSecondary,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightDivider,
    secondary = LightTextSecondary,
    onSecondary = LightBgCard,
    tertiary = LightTextMuted,
    error = DangerLight,
    onError = LightBgCard
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBgCard,
    background = DarkBgPrimary,
    onBackground = DarkTextPrimary,
    surface = DarkBgCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBgSecondary,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkDivider,
    secondary = DarkTextSecondary,
    onSecondary = DarkBgCard,
    tertiary = DarkTextMuted,
    error = DangerDark,
    onError = DarkBgCard
)

@Composable
fun MemosNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
