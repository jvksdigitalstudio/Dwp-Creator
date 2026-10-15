package com.jvk.dwpcreator.session.testing

import com.jvk.dwpcreator.dwp.SampleInfo
import com.jvk.dwpcreator.session.project.LoadedSamples

/** Doble de [LoadedSamples] con contenido modificable desde la prueba. */
class FakeLoadedSamples(
    private val audio: Map<Int, ByteArray> = emptyMap(),
    var samples: List<SampleInfo> = emptyList()
) : LoadedSamples {

    override fun audioFor(index: Int): ByteArray? = audio[index]

    override fun samples(): List<SampleInfo> = samples
}

/** Muestra mínima para pruebas: solo importan el índice y su rango de teclas. */
fun sampleInfo(index: Int, lowKey: Int, rootKey: Int, highKey: Int): SampleInfo = SampleInfo(
    index = index,
    name = "Instrument_$index",
    note = "C3",
    velocity = 127,
    lowKey = lowKey,
    rootKey = rootKey,
    highKey = highKey,
    frameCount = 100,
    dwpPath = ""
)
