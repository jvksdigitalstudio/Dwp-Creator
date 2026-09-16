package com.jvk.dwpcreator.ui.theme

import androidx.compose.ui.graphics.Color

// Base surfaces
val BgDark = Color(0xFF0D0221)
val SurfacePurple = Color(0xFF1A0B33)
val SurfacePurpleAlt = Color(0xFF230F45)

// Accents
val NeonPurple = Color(0xFFB026FF)
val NeonMagenta = Color(0xFFFF2AD4)
val NeonCyan = Color(0xFF00F0FF)
val NeonGreen = Color(0xFF39FF88)

// Octave row colors (rotate across 8 octaves, referenced from the old UI)
val OctaveColors = listOf(
    NeonGreen,
    NeonCyan,
    Color(0xFFFFC145), // amber
    NeonMagenta,
    Color(0xFF7CFC00), // lime
    Color(0xFF45D9FF), // sky
    Color(0xFFFF6B6B), // coral
    NeonPurple
)

// Piano keys
val WhiteKeyBg = Color(0xFFF0EAF8)
val WhiteKeyText = Color(0xFF3A2A50)
val BlackKeyBg = Color(0xFF101018)
val BlackKeyText = Color(0xFFD8C8EE)

val TextDim = Color(0xFFB9A8D6)
