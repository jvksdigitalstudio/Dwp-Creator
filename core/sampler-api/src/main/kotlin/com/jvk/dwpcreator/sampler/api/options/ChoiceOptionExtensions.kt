package com.jvk.dwpcreator.sampler.api.options

import kotlin.math.roundToInt

/** Etiquetas de todas las opciones de [E], en orden de declaración. */
inline fun <reified E> optionLabels(): List<String> where E : Enum<E>, E : ChoiceOption =
    enumValues<E>().map { it.label }

/** Opción cuyo índice es el [Float] [index] (redondeado y saturado al rango válido). */
inline fun <reified E> optionAt(index: Float): E where E : Enum<E>, E : ChoiceOption {
    val all = enumValues<E>()
    return all[index.roundToInt().coerceIn(0, all.size - 1)]
}
