package com.jvk.dwpcreator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DwpColorScheme = darkColorScheme(
    primary = NeonPurple,
    secondary = NeonCyan,
    tertiary = NeonGreen,
    background = BgDark,
    surface = SurfacePurple,
    onPrimary = BgDark,
    onSecondary = BgDark,
    onBackground = TextDim,
    onSurface = TextDim
)

@Composable
fun DwpCreatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DwpColorScheme,
        typography = DwpTypography,
        content = content
    )
}
