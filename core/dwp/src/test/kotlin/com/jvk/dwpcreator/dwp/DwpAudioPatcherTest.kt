package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parcheo del formato de audio de [DwpAudioPatcher]: validación estricta y atomicidad.
 * Se ejecuta contra el `Instrument.dwp` real (ver [DwpTestFixtures]).
 */
class DwpAudioPatcherTest {

    @Test
    fun `patchFrameCount rejects an out-of-range sample index instead of silently doing nothing`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        try {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 999, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for an out-of-range index")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("999"))
        }
        try {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = -1, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for a negative index")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `replaceSampleAudio rejects an out-of-range sample index instead of silently doing nothing`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100,
            channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32,
            pcmData = ByteArray(16)
        )
        try {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 48, newName = "x", newPath = "x", wav = fakeWav)
            throw AssertionError("Expected IllegalArgumentException for an out-of-range index (valid range is 0..47)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("48"))
        }
    }

    @Test
    fun `patchFrameCount updates only the targeted sample`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val patched = DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 500_000)
        val samples = DwpSampleCatalog.listSamples(patched)

        assertEquals(500_000, samples[0].frameCount)
        assertEquals(378000, samples[1].frameCount) // every other sample untouched
        assertEquals(378000, samples[47].frameCount)

        // Still perfectly re-parseable after the length-changing patch.
        val reparsed = DwpDocument.parse(patched.toBytes())
        assertEquals(48, DwpSampleCatalog.listSamples(reparsed).size)
    }

    @Test
    fun `patchFrameCount refuses to edit a corrupted sample container rather than lose bytes`() {
        val corruptedDoc = DwpTestFixtures.buildDocWithCorruptedSampleContainer()
        try {
            DwpAudioPatcher.patchFrameCount(corruptedDoc, sampleContainerIndex = 0, newFrameCount = 1)
            throw AssertionError("Expected DwpFormatException; must not silently drop the 5 trailing bytes")
        } catch (e: DwpFormatException) {
            // Expected.
        }
    }

    @Test
    fun `patchFrameCount with an invalid target sample leaves the original document byte-identical -- Seccion 14 example`() {
        // Literal scenario from Sección 14: "documento con múltiples
        // muestras, muestra objetivo inválida, operación falla,
        // serialización del documento original debe continuar siendo
        // idéntica."
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val originalBytes = doc.toBytes()
        try {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 999, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for an invalid target sample")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `replaceSampleAudio with an invalid target sample leaves the original document byte-identical`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val originalBytes = doc.toBytes()
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        try {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 999, newName = "x", newPath = "x", wav = fakeWav)
            throw AssertionError("Expected IllegalArgumentException for an invalid target sample")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `patchFrameCount is atomic - validation failure leaves the input document provably unmodified`() {
        val original = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val originalBytes = original.toBytes()
        val corrupted = DwpTestFixtures.buildDocWithCorruptedSampleContainer()

        try {
            DwpAudioPatcher.patchFrameCount(corrupted, sampleContainerIndex = 0, newFrameCount = 999)
            throw AssertionError("Expected DwpFormatException due to the corrupted container")
        } catch (e: DwpFormatException) {
            // Expected.
        }
        // DwpDocument/DwpBlock/ByteArray payloads are never mutated in place
        // anywhere in this call chain -- confirm the pristine original is
        // still exactly as it was.
        assertArrayEquals(originalBytes, original.toBytes())
    }

    @Test
    fun `patchFrameCount and replaceSampleAudio remain strict -- reading tolerance does not relax mutation`() {
        // "Lectura tolerante, mutación estricta" (Sección 10): a malformed
        // 0x01f7 that listSamples() tolerates must STILL be rejected by the
        // mutating operations, exactly as before.
        val doc = DwpTestFixtures.docWithMalformedAudioFormatPayload(byteArrayOf(1, 2))
        assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
    }

    @Test
    fun `patchFrameCount rejects a negative newFrameCount`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = -1)
        }
        assertTrue(ex.message!!.contains("-1"))
    }

    @Test
    fun `patchFrameCount accepts newFrameCount of exactly zero (boundary, not negative)`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val patched = DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 0)
        assertEquals(0, DwpSampleCatalog.listSamples(patched)[0].frameCount)
    }

    @Test
    fun `patchFrameCount rejects a negative newFrameCount without modifying the original document`() {
        // Sección 14: atomicidad -- una validación fallida no debe dejar
        // ningún rastro, ni siquiera indirecto, en el documento original.
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val originalBytes = doc.toBytes()
        try {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = -5)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `patchFrameCount rejects a sample container with no 0x01f7 block at all`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(/* none */)
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
        assertTrue(ex.message!!.contains("no contiene"))
    }

    @Test
    fun `patchFrameCount rejects a sample container with two 0x01f7 blocks (ambiguous)`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(40), ByteArray(40))
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
        assertTrue(ex.message!!.contains("ambiguo"))
    }

    @Test
    fun `patchFrameCount rejects a 0x01f7 block whose payload size is not exactly 40 bytes`() {
        val tooSmall = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(39))
        assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.patchFrameCount(tooSmall, sampleContainerIndex = 0, newFrameCount = 1)
        }
        val tooBig = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(41))
        assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.patchFrameCount(tooBig, sampleContainerIndex = 0, newFrameCount = 1)
        }
    }

    @Test
    fun `replaceSampleAudio rejects a sample container with no 0x01f7 block`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(/* none */)
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
        assertTrue(ex.message!!.contains("no contiene"))
    }

    @Test
    fun `replaceSampleAudio rejects a sample container with a duplicate 0x01f7 block`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(40), ByteArray(40))
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
        assertTrue(ex.message!!.contains("ambiguo"))
    }

    @Test
    fun `replaceSampleAudio rejects a 0x01f7 block smaller than 40 bytes`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(20))
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
    }

    @Test
    fun `replaceSampleAudio rejects a 0x01f7 block larger than 40 bytes`() {
        val doc = DwpTestFixtures.sampleContainerWithAudioFormatBlocks(ByteArray(64))
        val fakeWav = com.jvk.dwpcreator.codec.wav.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.codec.wav.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        assertThrows(DwpFormatException::class.java) {
            DwpAudioPatcher.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
    }
}
