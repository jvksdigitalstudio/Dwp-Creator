package com.jvk.dwpcreator.ui.components.piano

import androidx.compose.ui.unit.dp

/**
 * Ancho/alto fijos de una tecla -- fuente única de verdad, reutilizada por
 * [MainScreen][com.jvk.dwpcreator.ui.screens.MainScreen] para calcular
 * exactamente qué franja de la pantalla es "la columna de teclas" al
 * implementar el overlay de gestos multi-touch (acordes + arrastre entre
 * teclas). No duplicar este número en ningún otro sitio.
 */
val PianoKeyWidth = 58.dp

val PianoKeyHeight = 42.dp
