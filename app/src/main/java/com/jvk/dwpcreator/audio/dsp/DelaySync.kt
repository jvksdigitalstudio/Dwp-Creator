package com.jvk.dwpcreator.audio.dsp

import kotlin.math.roundToInt

/**
 * Sincronía del delay con el tempo: divisiones rítmicas y su conversión a
 * milisegundos. Funciones puras, sin estado.
 *
 * El sampler no recibe tempo de un anfitrión (no es un plugin): el BPM es un
 * parámetro propio del módulo ([FxParam.DELAY_BPM]).
 */
internal object DelaySync {

    /** Nombres mostrados en la UI, en el mismo orden que [BEATS]. `T` = tresillo, `.` = con puntillo. */
    val NAMES: List<String> = listOf(
        "1/16T", "1/16", "1/8T", "1/8", "1/8.", "1/4T", "1/4", "1/4.", "1/2", "1/1"
    )

    /** Duración de cada división en **negras** (1.0 = una negra). */
    private val BEATS = floatArrayOf(
        1f / 6f, 0.25f, 1f / 3f, 0.5f, 0.75f, 2f / 3f, 1f, 1.5f, 2f, 4f
    )

    const val DEFAULT_INDEX = 3
    val LAST_INDEX: Int get() = NAMES.size - 1

    fun name(index: Int): String = NAMES[index.coerceIn(0, LAST_INDEX)]

    /**
     * Tiempo de retardo en ms para [bpm] y la división [index]. Si excede
     * [maxMs] (p. ej. 1/1 a 40 BPM = 6 s) se divide a la mitad hasta caber:
     * sigue siendo un valor rítmico (la división inmediata inferior) en vez de
     * recortarse a un número arbitrario fuera de compás.
     */
    fun resolveMs(bpm: Float, index: Int, maxMs: Float): Float {
        val beatMs = 60_000f / bpm.coerceIn(20f, 400f)
        var ms = beatMs * BEATS[index.coerceIn(0, LAST_INDEX)]
        while (ms > maxMs) ms *= 0.5f
        return ms
    }

    /** Tiempo efectivo del delay para un estado: manual o sincronizado según [SamplerFxState.delaySync]. */
    fun effectiveTimeMs(state: SamplerFxState): Float =
        if (state.delaySync) {
            resolveMs(state.delayBpm, state.delayDivision, StereoDelayEffect.MAX_DELAY_SECONDS * 1000f - 10f)
        } else {
            state.delayTimeMs
        }

    /** Índice entero desde un valor de parámetro (los knobs trabajan en `Float`). */
    fun indexOf(value: Float): Int = value.roundToInt().coerceIn(0, LAST_INDEX)
}
