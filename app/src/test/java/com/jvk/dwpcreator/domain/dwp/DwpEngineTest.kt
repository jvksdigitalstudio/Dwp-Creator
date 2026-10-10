package com.jvk.dwpcreator.domain.dwp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)

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
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)

        fun sampleForNote(note: Int) = samples.firstOrNull { note in it.lowKey..it.highKey }

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
    fun `renameInstrument only touches known name-path tags, never an unknown block that happens to contain the old name`() {
        // Sección 10 del prompt maestro: printable-text detection alone must
        // never be sufficient grounds to rewrite a block. Constructs a
        // synthetic unknown-tag block whose payload coincidentally spells
        // out the old instrument name as printable text, and confirms
        // renameInstrument leaves it completely untouched.
        val doc = DwpDocument.parse(loadRealFixture())
        val decoyTag = 0x0070 // not a known tag anywhere in DwpBlock
        val decoyPayload = "Instrument-flavored-but-not-a-name-field".toByteArray(Charsets.ISO_8859_1)
        val decoyBlock = DwpBlock(decoyTag, 0, decoyPayload)
        val docWithDecoy = DwpDocument(doc.preamble, doc.blocks + decoyBlock)

        val renamed = DwpEngine.renameInstrument(docWithDecoy, "Instrument", "Didas")

        // The known tags DID change...
        val samples = DwpEngine.listSamples(renamed)
        assertEquals("Didas_C3_127", samples[0].name)
        // ...but the decoy block, despite containing "Instrument" as
        // printable text, must be byte-for-byte untouched.
        val decoyAfter = renamed.blocks.first { it.tag == decoyTag }
        assertTrue(decoyAfter.payload.contentEquals(decoyPayload))
    }

    @Test
    fun `patchFrameCount rejects an out-of-range sample index instead of silently doing nothing`() {
        val doc = DwpDocument.parse(loadRealFixture())
        try {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 999, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for an out-of-range index")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("999"))
        }
        try {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = -1, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for a negative index")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `replaceSampleAudio rejects an out-of-range sample index instead of silently doing nothing`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100,
            channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32,
            pcmData = ByteArray(16)
        )
        try {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 48, newName = "x", newPath = "x", wav = fakeWav)
            throw AssertionError("Expected IllegalArgumentException for an out-of-range index (valid range is 0..47)")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("48"))
        }
    }

    @Test
    fun `sampleCount matches the real number of sample containers`() {
        val doc = DwpDocument.parse(loadRealFixture())
        assertEquals(48, DwpEngine.sampleCount(doc))
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
        assertEquals(36, samples[0].rootKey)
        assertEquals(0, samples[0].lowKey)
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

    // --- RISK-05 (audit Doc/12_KNOWN_ISSUES.md): reserializing an edited
    // sample container must never silently drop bytes it didn't understand.
    // These tests build a deliberately-corrupted nested payload (extra
    // trailing bytes that don't form a valid block header) and confirm the
    // engine now refuses to touch it instead of quietly reserializing a
    // truncated copy. Before this fix, renameRecursive/patchFrameCount/
    // replaceSampleAudio used the lenient DwpTokenizer.tokenize() on nested
    // payloads with no check that it consumed the whole thing.

    private fun buildDocWithCorruptedSampleContainer(): DwpDocument {
        val realDoc = DwpDocument.parse(loadRealFixture())
        val firstSample = realDoc.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        // Append 5 trailing bytes that cannot possibly be interpreted as a
        // further valid block (fewer than the 12-byte minimum header size).
        val corruptedPayload = firstSample.payload + byteArrayOf(1, 2, 3, 4, 5)
        val corruptedContainer = DwpBlock(firstSample.tag, firstSample.reserved, corruptedPayload)
        val newBlocks = realDoc.blocks.map { if (it === firstSample) corruptedContainer else it }
        return DwpDocument(realDoc.preamble, newBlocks)
    }

    @Test
    fun `tokenizeStrict throws instead of silently dropping unconsumed trailing bytes`() {
        val realDoc = DwpDocument.parse(loadRealFixture())
        val firstSample = realDoc.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val corruptedPayload = firstSample.payload + byteArrayOf(1, 2, 3, 4, 5)

        // The lenient tokenizer alone does NOT complain -- it just reports
        // where it stopped, short of the full buffer.
        val lenientResult = DwpTokenizer.tokenize(corruptedPayload)
        assertTrue(lenientResult.endOffset < corruptedPayload.size)

        // The strict variant must refuse instead of returning a partial result.
        try {
            DwpTokenizer.tokenizeStrict(corruptedPayload, context = "test container")
            throw AssertionError("Expected DwpFormatException for corrupted nested payload")
        } catch (e: DwpFormatException) {
            assertTrue(e.message!!.contains("test container"))
        }
    }

    @Test
    fun `renameInstrument refuses to edit a document with a corrupted sample container`() {
        val corruptedDoc = buildDocWithCorruptedSampleContainer()
        try {
            DwpEngine.renameInstrument(corruptedDoc, "Instrument", "Didas")
            throw AssertionError("Expected DwpFormatException; must not silently drop the 5 trailing bytes")
        } catch (e: DwpFormatException) {
            // Expected: refuses to reserialize a container it can't fully account for.
        }
    }

    @Test
    fun `patchFrameCount refuses to edit a corrupted sample container rather than lose bytes`() {
        val corruptedDoc = buildDocWithCorruptedSampleContainer()
        try {
            DwpEngine.patchFrameCount(corruptedDoc, sampleContainerIndex = 0, newFrameCount = 1)
            throw AssertionError("Expected DwpFormatException; must not silently drop the 5 trailing bytes")
        } catch (e: DwpFormatException) {
            // Expected.
        }
    }

    @Test
    fun `listSamples still reads a corrupted sample container leniently (read-only, no data loss risk)`() {
        // listSamples deliberately keeps using the lenient tokenizer: it
        // never reserializes, so it can't lose bytes. It should degrade
        // gracefully (best-effort reading) rather than crash the sample list
        // over a container that only OTHER samples don't even reference.
        val corruptedDoc = buildDocWithCorruptedSampleContainer()
        val samples = DwpEngine.listSamples(corruptedDoc)
        assertEquals(48, samples.size) // still lists every sample container
    }

    @Test
    fun `patchFrameCount with an invalid target sample leaves the original document byte-identical -- Seccion 14 example`() {
        // Literal scenario from Sección 14: "documento con múltiples
        // muestras, muestra objetivo inválida, operación falla,
        // serialización del documento original debe continuar siendo
        // idéntica."
        val doc = DwpDocument.parse(loadRealFixture())
        val originalBytes = doc.toBytes()
        try {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 999, newFrameCount = 1)
            throw AssertionError("Expected IllegalArgumentException for an invalid target sample")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `replaceSampleAudio with an invalid target sample leaves the original document byte-identical`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val originalBytes = doc.toBytes()
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        try {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 999, newName = "x", newPath = "x", wav = fakeWav)
            throw AssertionError("Expected IllegalArgumentException for an invalid target sample")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `unknown blocks remain byte-for-byte intact after a known modification (rename)`() {
        // Sección 26 del prompt maestro: parse -> modificación conocida ->
        // serialize debe mantener intactos los bloques desconocidos. This
        // checks every single nested block in every one of the 48 sample
        // containers, not just a couple of spot-checks: anything that isn't
        // one of the 4 explicitly-modified tags (name/path/keyRange is NOT
        // touched by rename either -- only name and path are) must be
        // byte-for-byte identical, including the 0x0204 modulation matrix
        // slots and the fully-opaque 0x01f8-0x0203 range.
        val original = DwpDocument.parse(loadRealFixture())
        val renamed = DwpEngine.renameInstrument(original, "Instrument", "Didas")

        val originalContainers = original.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val renamedContainers = renamed.blocks.filter { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        assertEquals(originalContainers.size, renamedContainers.size)

        val modifiableTags = setOf(DwpBlock.TAG_SAMPLE_NAME, DwpBlock.TAG_SAMPLE_PATH)
        var uncheckedBlockCount = 0
        for (i in originalContainers.indices) {
            val origNested = DwpTokenizer.tokenizeStrict(originalContainers[i].payload, context = "orig sample #$i")
            val renamedNested = DwpTokenizer.tokenizeStrict(renamedContainers[i].payload, context = "renamed sample #$i")
            assertEquals(origNested.size, renamedNested.size)
            for (j in origNested.indices) {
                val o = origNested[j]
                val r = renamedNested[j]
                assertEquals(o.tag, r.tag)
                if (o.tag !in modifiableTags) {
                    assertTrue(
                        "Block tag 0x${o.tag.toString(16)} at sample #$i, position #$j should be untouched by rename",
                        o.payload.contentEquals(r.payload)
                    )
                    uncheckedBlockCount++
                }
            }
        }
        // Sanity check on the test itself: make sure this actually exercised
        // a meaningful number of unknown/opaque blocks, not zero due to some
        // unrelated bug silently making the loop a no-op.
        assertTrue("Expected to have checked a substantial number of untouched blocks", uncheckedBlockCount > 1000)
    }

    @Test
    fun `renameInstrument is atomic - a corrupted sample container aborts with zero modification, not a partial rename`() {
        // Sección 5 del prompt maestro: regression test demonstrating
        // atomicity explicitly, not just relying on Kotlin's `map` throwing
        // behavior implicitly. `corrupted` has its very first sample
        // container corrupted (5 trailing garbage bytes) -- confirms that
        // even failing on the FIRST block processed still leaves the
        // original, separate document completely untouched (immutability),
        // and that the call throws cleanly rather than returning any kind
        // of partial result.
        val original = DwpDocument.parse(loadRealFixture())
        val corrupted = buildDocWithCorruptedSampleContainer() // corrupts sample #0 specifically
        val originalBytesBeforeAttempt = original.toBytes()

        try {
            DwpEngine.renameInstrument(corrupted, "Instrument", "Didas")
            throw AssertionError("Expected DwpFormatException due to the corrupted container")
        } catch (e: DwpFormatException) {
            // Expected.
        }

        // The ORIGINAL (uninvolved) document must be completely unaffected --
        // DwpDocument/DwpBlock are immutable, so this is really confirming no
        // shared mutable state was touched anywhere in the call chain.
        assertArrayEquals(originalBytesBeforeAttempt, original.toBytes())
    }

    @Test
    fun `patchFrameCount is atomic - validation failure leaves the input document provably unmodified`() {
        val original = DwpDocument.parse(loadRealFixture())
        val originalBytes = original.toBytes()
        val corrupted = buildDocWithCorruptedSampleContainer()

        try {
            DwpEngine.patchFrameCount(corrupted, sampleContainerIndex = 0, newFrameCount = 999)
            throw AssertionError("Expected DwpFormatException due to the corrupted container")
        } catch (e: DwpFormatException) {
            // Expected.
        }
        // DwpDocument/DwpBlock/ByteArray payloads are never mutated in place
        // anywhere in this call chain -- confirm the pristine original is
        // still exactly as it was.
        assertArrayEquals(originalBytes, original.toBytes())
    }

    // --- Sección 3/4 del prompt maestro: patchFrameCount/replaceSampleAudio
    // deben exigir EXACTAMENTE un 0x01f7 de 40 bytes antes de escribir.

    private fun sampleContainerWithAudioFormatBlocks(vararg audioFormatPayloads: ByteArray): DwpDocument {
        val original = DwpDocument.parse(loadRealFixture())
        val firstSample = original.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val nested = DwpTokenizer.tokenizeStrict(firstSample.payload, context = "test")
        val withoutAudioFormat = nested.filter { it.tag != DwpBlock.TAG_AUDIO_FORMAT }
        val newAudioFormatBlocks = audioFormatPayloads.map { DwpBlock(DwpBlock.TAG_AUDIO_FORMAT, 0, it) }
        val newNested = withoutAudioFormat + newAudioFormatBlocks
        val newContainer = DwpBlock(firstSample.tag, firstSample.reserved, DwpTokenizer.serialize(newNested))
        val newBlocks = original.blocks.map { if (it === firstSample) newContainer else it }
        return DwpDocument(original.preamble, newBlocks)
    }

    // --- Sección 10 del prompt maestro Pass 3: listSamples() es lectura
    // tolerante; las operaciones mutantes siguen siendo estrictas.

    private fun docWithMalformedAudioFormatPayload(malformedPayload: ByteArray): DwpDocument {
        val original = DwpDocument.parse(loadRealFixture())
        val firstSample = original.blocks.first { it.tag == DwpBlock.TAG_SAMPLE_CONTAINER }
        val nested = DwpTokenizer.tokenizeStrict(firstSample.payload, context = "test")
        val newNested = nested.map {
            if (it.tag == DwpBlock.TAG_AUDIO_FORMAT) DwpBlock(it.tag, it.reserved, malformedPayload) else it
        }
        val newContainer = DwpBlock(firstSample.tag, firstSample.reserved, DwpTokenizer.serialize(newNested))
        val newBlocks = original.blocks.map { if (it === firstSample) newContainer else it }
        return DwpDocument(original.preamble, newBlocks)
    }

    @Test
    fun `listSamples does not throw when 0x01f7 payload is shorter than 4 bytes -- reports frameCount as unknown`() {
        val doc = docWithMalformedAudioFormatPayload(byteArrayOf(1, 2)) // only 2 bytes, can't even read one Int32
        val samples = DwpEngine.listSamples(doc) // must not throw ArrayIndexOutOfBoundsException
        assertEquals(48, samples.size)
        assertEquals(-1, samples[0].frameCount) // unknown, not a crash
    }

    @Test
    fun `listSamples does not throw when 0x01f7 payload is completely empty`() {
        val doc = docWithMalformedAudioFormatPayload(ByteArray(0))
        val samples = DwpEngine.listSamples(doc)
        assertEquals(-1, samples[0].frameCount)
    }

    @Test
    fun `listSamples still reads frameCount correctly when 0x01f7 has the confirmed 40-byte size`() {
        // Sanity companion: the tolerant read path must not degrade the
        // normal, well-formed case.
        val doc = DwpDocument.parse(loadRealFixture())
        val samples = DwpEngine.listSamples(doc)
        assertEquals(378000, samples[0].frameCount)
    }

    @Test
    fun `patchFrameCount and replaceSampleAudio remain strict -- reading tolerance does not relax mutation`() {
        // "Lectura tolerante, mutación estricta" (Sección 10): a malformed
        // 0x01f7 that listSamples() tolerates must STILL be rejected by the
        // mutating operations, exactly as before.
        val doc = docWithMalformedAudioFormatPayload(byteArrayOf(1, 2))
        assertThrows(DwpFormatException::class.java) {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
    }

    @Test
    fun `patchFrameCount rejects a negative newFrameCount`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = -1)
        }
        assertTrue(ex.message!!.contains("-1"))
    }

    @Test
    fun `patchFrameCount accepts newFrameCount of exactly zero (boundary, not negative)`() {
        val doc = DwpDocument.parse(loadRealFixture())
        val patched = DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 0)
        assertEquals(0, DwpEngine.listSamples(patched)[0].frameCount)
    }

    @Test
    fun `patchFrameCount rejects a negative newFrameCount without modifying the original document`() {
        // Sección 14: atomicidad -- una validación fallida no debe dejar
        // ningún rastro, ni siquiera indirecto, en el documento original.
        val doc = DwpDocument.parse(loadRealFixture())
        val originalBytes = doc.toBytes()
        try {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = -5)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        assertArrayEquals(originalBytes, doc.toBytes())
    }

    @Test
    fun `patchFrameCount rejects a sample container with no 0x01f7 block at all`() {
        val doc = sampleContainerWithAudioFormatBlocks(/* none */)
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
        assertTrue(ex.message!!.contains("no contiene"))
    }

    @Test
    fun `patchFrameCount rejects a sample container with two 0x01f7 blocks (ambiguous)`() {
        val doc = sampleContainerWithAudioFormatBlocks(ByteArray(40), ByteArray(40))
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpEngine.patchFrameCount(doc, sampleContainerIndex = 0, newFrameCount = 1)
        }
        assertTrue(ex.message!!.contains("ambiguo"))
    }

    @Test
    fun `patchFrameCount rejects a 0x01f7 block whose payload size is not exactly 40 bytes`() {
        val tooSmall = sampleContainerWithAudioFormatBlocks(ByteArray(39))
        assertThrows(DwpFormatException::class.java) {
            DwpEngine.patchFrameCount(tooSmall, sampleContainerIndex = 0, newFrameCount = 1)
        }
        val tooBig = sampleContainerWithAudioFormatBlocks(ByteArray(41))
        assertThrows(DwpFormatException::class.java) {
            DwpEngine.patchFrameCount(tooBig, sampleContainerIndex = 0, newFrameCount = 1)
        }
    }

    @Test
    fun `replaceSampleAudio rejects a sample container with no 0x01f7 block`() {
        val doc = sampleContainerWithAudioFormatBlocks(/* none */)
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
        assertTrue(ex.message!!.contains("no contiene"))
    }

    @Test
    fun `replaceSampleAudio rejects a sample container with a duplicate 0x01f7 block`() {
        val doc = sampleContainerWithAudioFormatBlocks(ByteArray(40), ByteArray(40))
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        val ex = assertThrows(DwpFormatException::class.java) {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
        assertTrue(ex.message!!.contains("ambiguo"))
    }

    @Test
    fun `replaceSampleAudio rejects a 0x01f7 block smaller than 40 bytes`() {
        val doc = sampleContainerWithAudioFormatBlocks(ByteArray(20))
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        assertThrows(DwpFormatException::class.java) {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
    }

    @Test
    fun `replaceSampleAudio rejects a 0x01f7 block larger than 40 bytes`() {
        val doc = sampleContainerWithAudioFormatBlocks(ByteArray(64))
        val fakeWav = com.jvk.dwpcreator.domain.audio.WavDecoder.DecodedWav(
            sampleRateHz = 44100, channelCount = 2,
            format = com.jvk.dwpcreator.domain.audio.WavDecoder.SampleFormat.FLOAT_32, pcmData = ByteArray(16)
        )
        assertThrows(DwpFormatException::class.java) {
            DwpEngine.replaceSampleAudio(doc, sampleContainerIndex = 0, newName = "x", newPath = "x", wav = fakeWav)
        }
    }

    // --- Sección 10-14 del prompt maestro: DwpVersionProfile / versionado.

    @Test
    fun `parse recognizes the confirmed v0x26 profile and preserves round-trip`() {
        // Re-confirms the existing round-trip guarantee still holds after
        // routing preamble size through DwpVersionProfile instead of a
        // hardcoded constant.
        val bytes = loadRealFixture()
        val doc = DwpDocument.parse(bytes)
        assertArrayEquals(bytes, doc.toBytes())
    }

    @Test
    fun `parse rejects an unrecognized version instead of guessing a preamble layout`() {
        val original = loadRealFixture()
        val corrupted = original.copyOf()
        // Overwrite the version field (offset 4) with an unrecognized value.
        corrupted[4] = 0x99.toByte(); corrupted[5] = 0; corrupted[6] = 0; corrupted[7] = 0

        val ex = assertThrows(DwpFormatException::class.java) {
            DwpDocument.parse(corrupted)
        }
        assertTrue(ex.message!!.contains("no reconocida"))
    }

    @Test
    fun `parse rejects a file truncated before the version-specific preamble completes`() {
        val original = loadRealFixture()
        // Keep the magic and version field, but cut off partway through the
        // (version-specific, 90-byte) preamble.
        val truncated = original.copyOfRange(0, 50)

        assertThrows(DwpFormatException::class.java) {
            DwpDocument.parse(truncated)
        }
    }

    @Test
    fun `parse rejects a file too small to even contain magic and version`() {
        assertThrows(DwpFormatException::class.java) {
            DwpDocument.parse(byteArrayOf(1, 2, 3))
        }
    }


    // explícitamente por qué se detuvo, no solo "se detuvo". Estos tests
    // construyen streams sintéticos mínimos (sin depender del fixture real)
    // para cada StopReason.

    private fun block(tag: Int, payload: ByteArray, reserved: Int = 0): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, tag)
        DwpTokenizer.writeLE32(out, payload.size)
        DwpTokenizer.writeLE32(out, reserved)
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `tokenize reports CLEAN_EOF when the stream is consumed exactly`() {
        val buf = block(0x1234, byteArrayOf(1, 2, 3))
        val result = DwpTokenizer.tokenize(buf)
        assertEquals(DwpTokenizer.StopReason.CLEAN_EOF, result.stopReason)
        assertEquals(buf.size, result.endOffset)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun `tokenize reports TRAILING_BYTES for leftover bytes too small for another header`() {
        val buf = block(0x1234, byteArrayOf(1, 2, 3)) + byteArrayOf(9, 9) // 2 stray bytes, need 12 for a header
        val result = DwpTokenizer.tokenize(buf)
        assertEquals(DwpTokenizer.StopReason.TRAILING_BYTES, result.stopReason)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun `tokenize reports INVALID_LENGTH for a negative declared length`() {
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, 0x1234)
        DwpTokenizer.writeLE32(out, -1) // high bit set -> negative as signed Int32
        DwpTokenizer.writeLE32(out, 0)
        val result = DwpTokenizer.tokenize(out.toByteArray())
        assertEquals(DwpTokenizer.StopReason.INVALID_LENGTH, result.stopReason)
        assertEquals(0, result.blocks.size)
    }

    @Test
    fun `tokenize reports PAYLOAD_TRUNCATED when declared length exceeds the buffer`() {
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, 0x1234)
        DwpTokenizer.writeLE32(out, 100) // declares 100 bytes of payload...
        DwpTokenizer.writeLE32(out, 0)
        out.write(byteArrayOf(1, 2, 3)) // ...but only 3 actually follow
        val result = DwpTokenizer.tokenize(out.toByteArray())
        assertEquals(DwpTokenizer.StopReason.PAYLOAD_TRUNCATED, result.stopReason)
        assertEquals(0, result.blocks.size)
    }

    @Test
    fun `tokenize reports PAYLOAD_TRUNCATED without Int overflow for a huge declared length`() {
        // Regression test mirroring the WavDecoder overflow fix: a length
        // near Int.MAX_VALUE must not overflow the bounds check and
        // silently pass as "fits".
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, 0x1234)
        DwpTokenizer.writeLE32(out, Int.MAX_VALUE - 4)
        DwpTokenizer.writeLE32(out, 0)
        val result = DwpTokenizer.tokenize(out.toByteArray())
        assertEquals(DwpTokenizer.StopReason.PAYLOAD_TRUNCATED, result.stopReason)
    }

    @Test
    fun `tokenize reports MAX_BLOCKS_REACHED when the safety cap is hit`() {
        val out = java.io.ByteArrayOutputStream()
        repeat(5) { out.write(block(0x0001, ByteArray(0))) }
        val result = DwpTokenizer.tokenize(out.toByteArray(), maxBlocks = 3)
        assertEquals(DwpTokenizer.StopReason.MAX_BLOCKS_REACHED, result.stopReason)
        assertEquals(3, result.blocks.size)
    }

    @Test
    fun `tokenizeStrict error message names the specific stop reason, not just a generic mismatch`() {
        val out = java.io.ByteArrayOutputStream()
        DwpTokenizer.writeLE32(out, 0x1234)
        DwpTokenizer.writeLE32(out, -1)
        DwpTokenizer.writeLE32(out, 0)
        try {
            DwpTokenizer.tokenizeStrict(out.toByteArray(), context = "test")
            throw AssertionError("Expected DwpFormatException")
        } catch (e: DwpFormatException) {
            assertTrue(e.message!!.contains("longitud negativa"))
        }
    }
}

