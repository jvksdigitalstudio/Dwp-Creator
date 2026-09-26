package com.jvk.dwpcreator.ui.state

import com.jvk.dwpcreator.domain.dwp.DwpDocument
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import com.jvk.dwpcreator.domain.dwp.SampleInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cobertura de [SampleOctave]. Reproduce el defecto real observado en
 * pantalla: la primera muestra (C3) tenía `lowKey = 0` (zona extendida hasta
 * el fondo del teclado), se agrupaba en la octava 0 y el contador mostraba
 * "5 octaves" para un instrumento de 4 octavas (C3-B6).
 */
class SampleOctaveTest {

    private fun sample(
        note: String,
        lowKey: Int,
        rootKey: Int = lowKey,
        highKey: Int = lowKey
    ) = SampleInfo(
        index = 0, name = "X_${note}_127", note = note, velocity = 127,
        lowKey = lowKey, rootKey = rootKey, highKey = highKey, frameCount = 1, dwpPath = ""
    )

    @Test
    fun `octave comes from the note label`() {
        assertEquals(3, SampleOctave.of(sample("C3", lowKey = 36)))
        assertEquals(3, SampleOctave.of(sample("B3", lowKey = 47)))
        assertEquals(4, SampleOctave.of(sample("C4", lowKey = 48)))
        assertEquals(6, SampleOctave.of(sample("A#6", lowKey = 82)))
        assertEquals(-1, SampleOctave.of(sample("A-1", lowKey = 9)))
    }

    @Test
    fun `an extended low zone does not move the sample to octave 0`() {
        val lowest = sample("C3", lowKey = 0, rootKey = 36, highKey = 36)
        assertEquals(3, SampleOctave.of(lowest))
    }

    @Test
    fun `an extended high zone does not move the sample to another octave`() {
        val highest = sample("B6", lowKey = 83, rootKey = 83, highKey = 127)
        assertEquals(6, SampleOctave.of(highest))
    }

    @Test
    fun `falls back to rootKey when the name has no note`() {
        assertEquals(4, SampleOctave.of(sample("?", lowKey = 0, rootKey = 50, highKey = 60)))
    }

    @Test
    fun `falls back to lowKey when there is no valid rootKey either`() {
        assertEquals(5, SampleOctave.of(sample("?", lowKey = 60, rootKey = -1, highKey = 71)))
    }

    @Test
    fun `falls back to octave 0 when nothing is usable`() {
        assertEquals(0, SampleOctave.of(sample("?", lowKey = -1, rootKey = -1, highKey = -1)))
    }

    @Test
    fun `the real 48-sample fixture spans exactly 4 octaves, not 5`() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")!!.readBytes()
        val samples = DwpEngine.listSamples(DwpDocument.parse(bytes))

        assertEquals(48, samples.size)
        assertEquals(4, SampleOctave.countDistinct(samples))
        // La primera muestra pertenece a la misma octava que C#3..B3, no a una aparte.
        assertEquals(SampleOctave.of(samples[1]), SampleOctave.of(samples[0]))
        assertEquals(3, SampleOctave.of(samples[0]))
    }
}
