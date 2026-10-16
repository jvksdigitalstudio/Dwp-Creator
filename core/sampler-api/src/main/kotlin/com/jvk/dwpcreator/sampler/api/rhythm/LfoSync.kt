package com.jvk.dwpcreator.sampler.api.rhythm

/**
 * Divisiones rítmicas del LFO en modo tempo y su conversión a frecuencia. La
 * duración de cada ciclo se expresa en **negras** (1.0 = una negra, 4.0 = un compás de 4/4).
 */
object LfoSync {

    const val DEFAULT_INDEX = 4

    private val TABLE = RhythmTable(
        names = listOf("4 bar", "2 bar", "1 bar", "1/2", "1/4", "1/8", "1/16", "1/32", "1/4T", "1/8T", "1/16T", "1/4."),
        beats = floatArrayOf(16f, 8f, 4f, 2f, 1f, 0.5f, 0.25f, 0.125f, 2f / 3f, 1f / 3f, 1f / 6f, 1.5f)
    )

    val NAMES: List<String> get() = TABLE.names
    val LAST_INDEX: Int get() = TABLE.lastIndex

    fun indexOf(value: Float): Int = TABLE.indexOf(value)

    /** Ciclos por segundo para [bpm] y la división [index]. */
    fun rateHz(bpm: Float, index: Int): Float = 1f / TABLE.cycleSeconds(bpm, index)
}
