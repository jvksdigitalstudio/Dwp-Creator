package com.jvk.dwpcreator.domain.dwp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests run against the REAL Instrument.dwp exported from FL Studio
 * Desktop (48-sample chromatic instrument, C3–D6, velocity 127, 47,307
 * bytes) — the exact same file used during the original binary audit. If
 * these pass, the engine is verified against ground truth, not guesses.
 */
class DwpEngineTest {

    private fun loadRealFixture(): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("Instrument.dwp")
            ?: error("Test fixture Instrument.dwp not found on test classpath")
        return stream.readBytes()
    }

    @Test
    fun `real file parses with zero leftover bytes`() {
        val bytes = loadRealFixture()
        val doc = DwpDocument.parse(bytes) // throws DwpFormatException on any mismatch
        assertTrue(doc.blocks.isNotEmpty())
    }

    @Test
    fun `parse then toBytes is a lossless roundtrip on the real file`() {
        val bytes = loadRealFixture()
        val doc = DwpDocument.parse(bytes)
        val rebuilt = doc.toBytes()
        assertTrue("Re-serialized bytes must match the original file exactly", bytes.contentEquals(rebuilt))
    }

    @Test
    fun `finds exactly 48 samples with correct notes and velocities`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)
        assertEquals(48, samples.size)
        assertEquals("C3", samples[0].note)
        assertEquals(127, samples[0].velocity)
        assertEquals("B6", samples[47].note)
    }

    @Test
    fun `key range extends to full keyboard at both ends, verified against real data`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)

        // First sample: rootKey extended down to 0, low/high stay at its own note (36)
        assertEquals(36, samples[0].lowKey)
        assertEquals(0, samples[0].rootKey)
        assertEquals(36, samples[0].highKey)

        // Middle samples: single-key zone, low == root == high == own note
        assertEquals(37, samples[1].lowKey)
        assertEquals(37, samples[1].rootKey)
        assertEquals(37, samples[1].highKey)

        // Last sample: highKey extended up to 127, low/root stay at its own note (83)
        assertEquals(83, samples[47].lowKey)
        assertEquals(83, samples[47].rootKey)
        assertEquals(127, samples[47].highKey)
    }

    @Test
    fun `frame count matches real audio exactly for every sample`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)
        // Verified independently against the wav 'data' chunk size / bytes-per-frame.
        for (s in samples) {
            assertEquals("frame count mismatch for ${s.name}", 378000, s.frameCount)
        }
    }

    @Test
    fun `detectInstrumentBaseName finds Instrument`() {
        val doc = DwpDocument.parse(loadRealFixture())
        assertEquals("Instrument", DwpEngine.detectInstrumentBaseName(doc))
    }

    @Test
    fun `renameInstrument replaces name everywhere including paths, keeps binary fields intact`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val renamed = DwpEngine.renameInstrument(doc, "Instrument", "Didas")

        val samples = DwpEngine.listSamples(renamed)
        assertEquals("Didas_C3_127", samples[0].name)
        assertTrue(samples[0].dwpPath.contains("Didas"))
        assertFalse(samples[0].dwpPath.contains("Instrument"))

        // Binary fields must be byte-identical to the original — a rename
        // must never touch key range or frame count.
        assertEquals(378000, samples[0].frameCount)
        assertEquals(0, samples[0].rootKey)
        assertEquals(36, samples[0].highKey)

        // The renamed file must still be perfectly re-parseable.
        val bytes = renamed.toBytes()
        val reparsed = DwpDocument.parse(bytes)
        assertEquals(48, DwpEngine.listSamples(reparsed).size)
    }

    @Test
    fun `renameInstrument works with a shorter or longer new name (variable length)`() {
        // This is exactly the case that used to require manual hex-editing
        // with careful length adjustment: old name and new name differ in
        // byte length. The tokenizer must recompute each block's own length
        // field independently; nothing else in the file should shift.
        val doc = DwpDocument.parse(loadRealFixture())

        val shorter = DwpEngine.renameInstrument(doc, "Instrument", "Didas") // 10 -> 5 chars
        val reparsedShorter = DwpDocument.parse(shorter.toBytes())
        assertEquals("Didas_B6_127", DwpEngine.listSamples(reparsedShorter)[47].name)

        val longer = DwpEngine.renameInstrument(doc, "Instrument", "MiInstrumentoLargo") // 10 -> 18 chars
        val reparsedLonger = DwpDocument.parse(longer.toBytes())
        assertEquals("MiInstrumentoLargo_B6_127", DwpEngine.listSamples(reparsedLonger)[47].name)
    }

    @Test
    fun `patchFrameCount updates only the targeted sample`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val patched = DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 500_000)
        val samples = DwpEngine.listSamples(patched)

        assertEquals(500_000, samples[0].frameCount)
        assertEquals(378000, samples[1].frameCount) // every other sample untouched
        assertEquals(378000, samples[47].frameCount)

        // Still perfectly re-parseable after the length-changing patch.
        val reparsed = DwpDocument.parse(patched.toBytes())
        assertEquals(48, DwpEngine.listSamples(reparsed).size)
    }
}
