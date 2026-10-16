package com.jvk.dwpcreator.ui.theme

import androidx.compose.ui.graphics.Color

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
