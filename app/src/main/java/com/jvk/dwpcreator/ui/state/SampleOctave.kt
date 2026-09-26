package com.jvk.dwpcreator.ui.state

import com.jvk.dwpcreator.domain.dwp.SampleInfo

/**
 * Determina a qué octava pertenece una muestra, para agrupar/colorear la
 * lista y para el contador "N octaves" de la barra de estado.
 *
 * **Por qué no se usa `lowKey`** (la versión anterior sí lo hacía): en un
 * instrumento DirectWave, la zona de la muestra MÁS GRAVE se extiende hacia
 * abajo hasta la nota MIDI 0 (y la más aguda hacia arriba hasta 127) para
 * cubrir todo el teclado -- ver `DwpEngine.listSamples`. Con `lowKey / 12`,
 * la primera muestra (p. ej. `C3`, MIDI 36) caía en la octava 0 en vez de la
 * 3: aparecía con un color distinto al del resto de su propia octava y el
 * contador reportaba una octava de más (5 en vez de 4 para C3-B6). La
 * octava de una muestra es una propiedad de SU NOTA, no de su zona de
 * disparo.
 *
 * Prioridad: (1) el número de octava escrito en la nota del propio nombre
 * (`C#3` -> 3; misma convención que `DwpEngine`, donde MIDI 36 = C3 =
 * `36 / 12`); (2) si el nombre no sigue el patrón, `rootKey / 12` (la nota
 * real de la muestra, nunca su zona extendida); (3) `lowKey / 12` como
 * último recurso.
 *
 * Kotlin puro, sin dependencias de Android ni de Compose -- testeable en JVM.
 */
object SampleOctave {

    private const val SEMITONES_PER_OCTAVE = 12

    /** Octava de [sample]. Puede ser negativa (p. ej. `A-1`); quien la usa como índice debe acotarla. */
    fun of(sample: SampleInfo): Int {
        octaveFromNoteLabel(sample.note)?.let { return it }
        val key = when {
            sample.rootKey in 0..127 -> sample.rootKey
            sample.lowKey in 0..127 -> sample.lowKey
            else -> 0
        }
        return key / SEMITONES_PER_OCTAVE
    }

    /** Número de octavas distintas que ocupan [samples]. */
    fun countDistinct(samples: List<SampleInfo>): Int =
        samples.map { of(it) }.distinct().size

    /** `"C#3"` -> 3, `"A-1"` -> -1, `"?"` -> null. */
    private fun octaveFromNoteLabel(note: String): Int? =
        note.dropWhile { !it.isDigit() && it != '-' }.toIntOrNull()
}
