package com.jvk.dwpcreator.sampler.api.rhythm

import kotlin.math.roundToInt

/**
 * Tabla de divisiones rítmicas: nombre mostrado y duración en **negras** (1.0 = una
 * negra, 4.0 = un compás de 4/4). Única implementación de la conversión división +
 * tempo -> tiempo, compartida por la sincronía del delay ([DelaySync]) y la del
 * LFO ([com.jvk.dwpcreator.sampler.api.rhythm.LfoSync]).
 */
class RhythmTable(val names: List<String>, private val beats: FloatArray) {

    init {
        require(names.isNotEmpty() && names.size == beats.size) { "una duración por división" }
    }

    val lastIndex: Int get() = names.size - 1

    fun indexOf(value: Float): Int = value.roundToInt().coerceIn(0, lastIndex)

    fun name(index: Int): String = names[index.coerceIn(0, lastIndex)]

    /** Duración en segundos de la división [index] a [bpm] (el tempo se limita a [MIN_BPM]..[MAX_BPM]). */
    fun cycleSeconds(bpm: Float, index: Int): Float =
        beats[index.coerceIn(0, lastIndex)] * 60f / bpm.coerceIn(MIN_BPM, MAX_BPM)

    private companion object {
        const val MIN_BPM = 20f
        const val MAX_BPM = 400f
    }
}
