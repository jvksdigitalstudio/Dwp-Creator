package com.jvk.dwpcreator.sampler.api.params

/** Cómo se reparte el recorrido de un knob entre el mínimo y el máximo de un parámetro. */
enum class FxCurve {
    /** Reparto uniforme. */
    LINEAR,

    /** Exponencial (frecuencias, tiempos largos): cada tramo del knob multiplica el valor por el mismo factor. Exige `min > 0`. */
    LOG,

    /** Cuadrático: resolución fina cerca del mínimo y recorrido amplio hacia el máximo (tiempos de glide, fundidos de LFO). */
    SQUARED
}
