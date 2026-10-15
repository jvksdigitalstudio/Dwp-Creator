package com.jvk.dwpcreator.sampler.api.rhythm

/**
 * Sincronía del delay con el tempo: divisiones rítmicas y su conversión a
 * milisegundos. Funciones puras, sin estado.
 *
 * El sampler no recibe tempo de un anfitrión (no es un plugin): el BPM es un
 * parámetro propio del módulo ([FxParam.DELAY_BPM]).
 */
object DelaySync {

    const val DEFAULT_INDEX = 3

    /** Nombres mostrados en la UI. `T` = tresillo, `.` = con puntillo. */
    private val TABLE = RhythmTable(
        names = listOf("1/16T", "1/16", "1/8T", "1/8", "1/8.", "1/4T", "1/4", "1/4.", "1/2", "1/1"),
        beats = floatArrayOf(1f / 6f, 0.25f, 1f / 3f, 0.5f, 0.75f, 2f / 3f, 1f, 1.5f, 2f, 4f)
    )

    val NAMES: List<String> get() = TABLE.names
    val LAST_INDEX: Int get() = TABLE.lastIndex

    fun name(index: Int): String = TABLE.name(index)

    /** Índice entero desde un valor de parámetro (los knobs trabajan en `Float`). */
    fun indexOf(value: Float): Int = TABLE.indexOf(value)

    /**
     * Tiempo de retardo en ms para [bpm] y la división [index]. Si excede
     * [maxMs] (p. ej. 1/1 a 40 BPM = 6 s) se divide a la mitad hasta caber:
     * sigue siendo un valor rítmico (la división inmediata inferior) en vez de
     * recortarse a un número arbitrario fuera de compás.
     */
    fun resolveMs(bpm: Float, index: Int, maxMs: Float): Float {
        var ms = TABLE.cycleSeconds(bpm, index) * 1000f
        while (ms > maxMs) ms *= 0.5f
        return ms
    }

}
