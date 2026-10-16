package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lectura de muestras de [DwpSampleCatalog]: listado, rangos de teclas y nombre base.
 * Se ejecuta contra el `Instrument.dwp` real (ver [DwpTestFixtures]).
 */
class DwpSampleCatalogTest {

    @Test
    fun `finds exactly 48 samples with correct notes and velocities`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val samples = DwpSampleCatalog.listSamples(doc)
        assertEquals(48, samples.size)
        assertEquals("C3", samples[0].note)
        assertEquals(127, samples[0].velocity)
        assertEquals("B6", samples[47].note)
    }

    @Test
    fun `key range physical order is root, low, high -- verified against real data`() {
        // Corrects a real bug found in a later audit pass (BUG-02,
        // Doc/17_AUDIT_ERRATA.md error E-01/E-02): the physical byte order
        // of 0x01f4 is [rootKey, lowKey, highKey], NOT [lowKey, rootKey,
        // highKey] as an earlier version of this test asserted. Verified
        // against all 48 samples of the real fixture: raw byte 0 matches
        // each sample's own pitch in 48/48 cases with zero exceptions; raw
        // byte 1 matches in 47/48, deviating only for the lowest sample
        // (extended down to MIDI note 0 -- a boundary extension, never a
        // root reassignment).
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val samples = DwpSampleCatalog.listSamples(doc)

        // First sample: rootKey stays at its own note (36); lowKey extended
        // down to 0 to cover the bottom of the keyboard; highKey stays at 36.
        assertEquals(36, samples[0].rootKey)
        assertEquals(0, samples[0].lowKey)
        assertEquals(36, samples[0].highKey)

        // Middle samples: single-key zone, root == low == high == own note.
        assertEquals(37, samples[1].rootKey)
        assertEquals(37, samples[1].lowKey)
        assertEquals(37, samples[1].highKey)

        // Last sample: rootKey/lowKey stay at its own note (83); highKey
        // extended up to 127 to cover the top of the keyboard.
        assertEquals(83, samples[47].rootKey)
        assertEquals(83, samples[47].lowKey)
        assertEquals(127, samples[47].highKey)
    }

    @Test
    fun `MIDI note-to-sample selection uses the corrected range, not the old buggy one`() {
        // Reproduces exactly the range check used in
        // DwpCreatorViewModel.handleMidiNoteOn: `event.note in it.lowKey..it.highKey`.
        // Before BUG-02 was fixed, sample 0's effective range was miscomputed
        // as 36..36 (because lowKey actually held the rootKey value), so
        // notes 0-35 matched NO sample at all. This test demonstrates the
        // fixed, real behavior across the boundary notes named in the audit.
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val samples = DwpSampleCatalog.listSamples(doc)

        fun sampleForNote(note: Int) = SampleZoneResolver.resolve(samples, note)

        // Note 0: the lowest possible MIDI note. Must hit sample 0 (its
        // lowKey is extended down to 0) -- this is exactly the case that
        // used to silently match nothing.
        assertEquals(0, sampleForNote(0)?.index)
        // Note 35: still within sample 0's extended low boundary (0..36).
        assertEquals(0, sampleForNote(35)?.index)
        // Note 36: sample 0's own root/high boundary.
        assertEquals(0, sampleForNote(36)?.index)
        // Note 37: the very next sample (a single-key zone of its own).
        assertEquals(1, sampleForNote(37)?.index)
        // Note 127: the highest possible MIDI note. Must hit the last
        // sample (its highKey is extended up to 127).
        assertEquals(47, sampleForNote(127)?.index)
    }

    @Test
    fun `sampleCount matches the real number of sample containers`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        assertEquals(48, DwpSampleCatalog.sampleCount(doc))
    }

    @Test
    fun `frame count matches real audio exactly for every sample`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val samples = DwpSampleCatalog.listSamples(doc)
        // Verified independently against the wav 'data' chunk size / bytes-per-frame.
        for (s in samples) {
            assertEquals("frame count mismatch for ${s.name}", 378000, s.frameCount)
        }
    }

    @Test
    fun `detectInstrumentBaseName finds Instrument`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        assertEquals("Instrument", DwpSampleCatalog.detectInstrumentBaseName(doc))
    }

    @Test
    fun `listSamples still reads a corrupted sample container leniently (read-only, no data loss risk)`() {
        // listSamples deliberately keeps using the lenient tokenizer: it
        // never reserializes, so it can't lose bytes. It should degrade
        // gracefully (best-effort reading) rather than crash the sample list
        // over a container that only OTHER samples don't even reference.
        val corruptedDoc = DwpTestFixtures.buildDocWithCorruptedSampleContainer()
        val samples = DwpSampleCatalog.listSamples(corruptedDoc)
        assertEquals(48, samples.size) // still lists every sample container
    }

    @Test
    fun `listSamples does not throw when 0x01f7 payload is shorter than 4 bytes -- reports frameCount as unknown`() {
        val doc = DwpTestFixtures.docWithMalformedAudioFormatPayload(byteArrayOf(1, 2)) // only 2 bytes, can't even read one Int32
        val samples = DwpSampleCatalog.listSamples(doc) // must not throw ArrayIndexOutOfBoundsException
        assertEquals(48, samples.size)
        assertEquals(-1, samples[0].frameCount) // unknown, not a crash
    }

    @Test
    fun `listSamples does not throw when 0x01f7 payload is completely empty`() {
        val doc = DwpTestFixtures.docWithMalformedAudioFormatPayload(ByteArray(0))
        val samples = DwpSampleCatalog.listSamples(doc)
        assertEquals(-1, samples[0].frameCount)
    }

    @Test
    fun `listSamples still reads frameCount correctly when 0x01f7 has the confirmed 40-byte size`() {
        // Sanity companion: the tolerant read path must not degrade the
        // normal, well-formed case.
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val samples = DwpSampleCatalog.listSamples(doc)
        assertEquals(378000, samples[0].frameCount)
    }
}
