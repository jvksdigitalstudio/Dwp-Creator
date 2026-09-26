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

// Piano keys -- a plain, realistic keyboard: white keys are white, black keys
// are black, and neither carries any per-octave accent color as a border or
// fill (an earlier version outlined every key with the color of its octave,
// which made the strip look like a color chart instead of a piano). The only
// per-octave color that appears on a key is the small foot-mark under a "C"
// key (see PianoKeyBadge) -- everything else here stays fixed regardless of
// octave.
val WhiteKeyBg = Color(0xFFF0EAF8)
val WhiteKeyText = Color(0xFF3A2A50)
val WhiteKeyBorder = Color(0xFFBDB2D0)
val WhiteKeyPressedBg = Color(0xFFD6B9FF)

val BlackKeyBg = Color(0xFF101018)
val BlackKeyText = Color(0xFFD8C8EE)
val BlackKeyBorder = Color(0xFF3A3A4A)
val BlackKeyPressedBg = Color(0xFF6A2BC2)
val BlackKeyPressedText = Color(0xFFFFFFFF)

// Piano keys -- degradado "premium": cada tecla se pinta con un barniz
// vertical (más clara arriba, más oscura abajo) en vez de un color plano,
// más una veta de brillo cerca del borde superior. Esto es lo que le da el
// aspecto de tecla física vista en ángulo/lateral en vez de una etiqueta
// plana -- luz "de arriba" cayendo sobre una superficie con relieve, el
// mismo lenguaje visual que un sintetizador/teclado real fotografiado desde
// el frente. Los tonos "pressed" siguen el mismo patrón para que la
// pulsación no rompa la sensación de volumen.
val WhiteKeyGradientTop = Color(0xFFFFFFFF)
val WhiteKeyGradientBottom = Color(0xFFDCD2EC)
val WhiteKeySheen = Color(0x59FFFFFF) // brillo superior, blanco al 35%

val BlackKeyGradientTop = Color(0xFF26242F)
val BlackKeyGradientBottom = Color(0xFF000000)
val BlackKeySheen = Color(0x33FFFFFF) // brillo superior, blanco al 20% -- más sutil que en la tecla blanca

val WhiteKeyPressedGradientTop = Color(0xFFEEDBFF)
val WhiteKeyPressedGradientBottom = Color(0xFFC49EFA)

val BlackKeyPressedGradientTop = Color(0xFF8A3EE8)
val BlackKeyPressedGradientBottom = Color(0xFF4A1590)

val TextDim = Color(0xFFB9A8D6)

/**
 * Texto de una tecla pulsada cuando se pinta con el color de su octava (ver
 * KDoc de [com.jvk.dwpcreator.ui.components.PianoKeyBadge], sección
 * "pulsación coloreada por octava"). [OctaveColors] son, en su mayoría,
 * tonos neón muy claros/saturados (verde, cian, ámbar, lima, cielo...),
 * así que por defecto se usa texto oscuro sobre ellos; para el puñado de
 * tonos de esa lista que sí son oscuros (p. ej. el púrpura), se elige
 * [ActiveAccentTextLight] en su lugar según la luminancia relativa real del
 * color -- no por color fijo, para que la elección siga siendo correcta si
 * la paleta de [OctaveColors] cambia más adelante.
 */
val ActiveAccentTextDark = Color(0xFF140B24)
val ActiveAccentTextLight = Color(0xFFFFFFFF)
