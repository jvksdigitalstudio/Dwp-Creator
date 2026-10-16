package com.jvk.dwpcreator.ui.theme

import androidx.compose.ui.graphics.Color

val TextDim = Color(0xFFB9A8D6)

/**
 * Texto de una tecla pulsada cuando se pinta con el color de su octava (ver
 * KDoc de [com.jvk.dwpcreator.ui.components.piano.PianoKeyBadge], sección
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
