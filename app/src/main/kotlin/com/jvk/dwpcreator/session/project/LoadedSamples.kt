package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.SampleInfo

/**
 * Vista de solo lectura del instrumento cargado, para quien necesita tocar
 * muestras (reproducción y MIDI) sin poder modificar el proyecto.
 */
interface LoadedSamples {

    /** Audio (WAV) de la muestra [index], o `null` si no hay instrumento o el índice no existe. */
    fun audioFor(index: Int): ByteArray?

    /** Muestras del instrumento cargado; vacía si no hay ninguno. */
    fun samples(): List<SampleInfo>
}
