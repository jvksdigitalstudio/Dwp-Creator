package com.jvk.dwpcreator.dwp

/**
 * Decide qué muestra suena para una nota MIDI.
 *
 * Usa el rango real de teclas de cada muestra (`lowKey..highKey`, bytes 1 y 2 de
 * `0x01f4`), no la nota del nombre: la muestra más grave extiende su rango hasta
 * la nota 0 y una búsqueda por nombre dejaría sin sonido todo ese tramo. Con
 * rangos solapados gana la primera muestra de la lista.
 */
object SampleZoneResolver {

    /** Primera muestra de [samples] cuyo rango de teclas contiene [note], o `null` si ninguna. */
    fun resolve(samples: List<SampleInfo>, note: Int): SampleInfo? =
        samples.firstOrNull { note in it.lowKey..it.highKey }
}
