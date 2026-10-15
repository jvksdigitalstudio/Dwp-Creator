package com.jvk.dwpcreator.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores de la franja de mini-mezclador por muestra (Mute/Solo/Pan/Volumen,
 * ver `SampleMixerControls.kt`). Mismo lenguaje visual "premium" que las
 * teclas del piano ([PianoKeyBadge][com.jvk.dwpcreator.ui.components.piano.PianoKeyBadge]):
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
