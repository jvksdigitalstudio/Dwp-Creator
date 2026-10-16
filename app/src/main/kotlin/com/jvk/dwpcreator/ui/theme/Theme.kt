package com.jvk.dwpcreator.ui.theme

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

/**
 * DWP Creator uses a single, fixed dark/neon color scheme by design -- there is
 * no light variant to switch to. (A `darkTheme` parameter previously sat here
 * unused, left over from the default Compose project template; it was removed
 * rather than wired up, since there is no second scheme for it to select.)
 */
@Composable
fun DwpCreatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DwpColorScheme,
        typography = DwpTypography,
        content = content
    )
}
