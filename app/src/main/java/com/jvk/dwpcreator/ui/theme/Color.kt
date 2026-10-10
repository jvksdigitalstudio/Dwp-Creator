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
// fill mientras está en reposo (ver más abajo el estado "activo"/pulsada, que
// sí se pinta con el color de su octava). El único color por octava que
// aparece sobre una tecla en reposo es la banda lateral del Do (ver
// PianoKeyBadge) -- todo lo demás en reposo es fijo, independiente de la
// octava.
//
// No hay colores "planos" de fondo aquí a propósito: cada tecla se pinta
// SIEMPRE con un degradado vertical (ver el bloque de abajo), nunca con un
// único color sólido -- ese es el aspecto "premium" de superficie física que
// pidió el usuario, no un capricho estético incidental.
val WhiteKeyText = Color(0xFF3A2A50)
val WhiteKeyBorder = Color(0xFFBDB2D0)

val BlackKeyText = Color(0xFFD8C8EE)
val BlackKeyBorder = Color(0xFF3A3A4A)
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

/**
 * Separador fino entre cada [com.jvk.dwpcreator.ui.components.SampleRow],
 * de extremo a extremo (todo el ancho de la pantalla, no solo el área de
 * contenido con padding) -- pedido explícito del usuario con capturas
 * anotadas a mano. Blanco a baja opacidad, no blanco sólido: sobre el fondo
 * morado oscuro de la lista, una línea blanca al 100% resultaría demasiado
 * gruesa/dura a la vista para una lista de 48+ filas seguidas -- a esta
 * opacidad se ve nítidamente blanca sin competir con el texto ni con el
 * degradado de las teclas.
 */
val SampleRowDivider = Color(0x29FFFFFF) // blanco al ~16%

/**
 * Separador vertical fino entre el **nombre** de la muestra y la franja de
 * mini-mezclador (Mute/Solo/Pan/Volumen) de [SampleRow][com.jvk.dwpcreator.ui.components.SampleRow]
 * -- pedido explícito del usuario con una captura anotada a mano: una línea
 * blanca vertical que separe visualmente el texto del nombre de los
 * controles, para que no se perciban como un solo bloque pegado. Blanco a
 * opacidad media (más visible que [SampleRowDivider], que es horizontal y
 * corre por 48+ filas seguidas y por eso necesita ser más discreto): esta
 * línea es corta (solo el alto de la fila) y necesita notarse de un
 * vistazo como el límite de columna que es.
 */
val NameMixerColumnDivider = Color(0x4DFFFFFF) // blanco al ~30%

/**
 * Colores de la franja de mini-mezclador por muestra (Mute/Solo/Pan/Volumen,
 * ver `SampleMixerControls.kt`). Mismo lenguaje visual "premium" que las
 * teclas del piano ([PianoKeyBadge][com.jvk.dwpcreator.ui.components.PianoKeyBadge]):
 * degradado vertical de superficie + veta de brillo superior, nunca un
 * relleno plano. Mute/Solo se iluminan con un tono de alerta estándar de
 * cualquier mesa de mezcla/DAW (rojo/ámbar) -- deliberadamente distintos de
 * [OctaveColors]/[NeonGreen]/[NeonCyan] para que un vistazo a la fila nunca
 * confunda "esta muestra está en solo" con, por ejemplo, "esta es la octava
 * verde". El degradado real de cada estado (claro arriba, oscuro abajo) se
 * calcula en tiempo de composición a partir de estos tonos base con `lerp` +
 * `luminance` -- igual que la tecla activa por octava -- así que aquí solo
 * hace falta declarar el tono base de cada pieza, no cada variante.
 */
val MixerTrackGradientTop = Color(0xFF2C1854)
val MixerTrackGradientBottom = Color(0xFF160A2C)
val MixerTrackSheen = Color(0x33FFFFFF)
val MixerControlBorder = Color(0xFF4A3A70)
val MixerThumbHighlight = Color(0xFFFFFFFF)
val MixerThumbShadowTint = Color(0xFF8A78B8)
val MixerControlFill = NeonGreen
val MixerCaptionText = Color(0xFFC7B9E8)
val MixerEndLabelText = Color(0x80C7B9E8)

val MixerMuteActive = Color(0xFFFF5C5C)
val MixerSoloActive = Color(0xFFFFC145)
