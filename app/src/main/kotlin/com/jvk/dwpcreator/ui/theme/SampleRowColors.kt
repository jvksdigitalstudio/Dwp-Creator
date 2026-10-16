package com.jvk.dwpcreator.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Separador fino entre cada [com.jvk.dwpcreator.ui.components.sample.SampleRow],
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
 * mini-mezclador (Mute/Solo/Pan/Volumen) de [SampleRow][com.jvk.dwpcreator.ui.components.sample.SampleRow]
 * -- pedido explícito del usuario con una captura anotada a mano: una línea
 * blanca vertical que separe visualmente el texto del nombre de los
 * controles, para que no se perciban como un solo bloque pegado. Blanco a
 * opacidad media (más visible que [SampleRowDivider], que es horizontal y
 * corre por 48+ filas seguidas y por eso necesita ser más discreto): esta
 * línea es corta (solo el alto de la fila) y necesita notarse de un
 * vistazo como el límite de columna que es.
 */
val NameMixerColumnDivider = Color(0x4DFFFFFF) // blanco al ~30%
