package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renombrado de instrumentos de [DwpRenamer]: lista blanca de etiquetas y atomicidad.
 * Se ejecuta contra el `Instrument.dwp` real (ver [DwpTestFixtures]).
 */
class DwpRenamerTest {

    @Test
    fun `renameInstrument only touches known name-path tags, never an unknown block that happens to contain the old name`() {
        // Sección 10 del prompt maestro: printable-text detection alone must
        // never be sufficient grounds to rewrite a block. Constructs a
        // synthetic unknown-tag block whose payload coincidentally spells
        // out the old instrument name as printable text, and confirms
        // renameInstrument leaves it completely untouched.
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val decoyTag = 0x0070 // not a known tag anywhere in DwpBlock
        val decoyPayload = "Instrument-flavored-but-not-a-name-field".toByteArray(Charsets.ISO_8859_1)
        val decoyBlock = DwpBlock(decoyTag, 0, decoyPayload)
        val docWithDecoy = DwpDocument(doc.preamble, doc.blocks + decoyBlock)

        val renamed = DwpRenamer.renameInstrument(docWithDecoy, "Instrument", "Didas")

        // The known tags DID change...
        val samples = DwpSampleCatalog.listSamples(renamed)
        assertEquals("Didas_C3_127", samples[0].name)
        // ...but the decoy block, despite containing "Instrument" as
        // printable text, must be byte-for-byte untouched.
        val decoyAfter = renamed.blocks.first { it.tag == decoyTag }
        assertTrue(decoyAfter.payload.contentEquals(decoyPayload))
    }

    @Test
    fun `renameInstrument replaces name everywhere including paths, keeps binary fields intact`() {
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val renamed = DwpRenamer.renameInstrument(doc, "Instrument", "Didas")

        val samples = DwpSampleCatalog.listSamples(renamed)
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
        assertEquals(48, DwpSampleCatalog.listSamples(reparsed).size)
    }

    @Test
    fun `renameInstrument works with a shorter or longer new name (variable length)`() {
        // This is exactly the case that used to require manual hex-editing
        // with careful length adjustment: old name and new name differ in
        // byte length. The tokenizer must recompute each block's own length
        // field independently; nothing else in the file should shift.
        val doc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())

        val shorter = DwpRenamer.renameInstrument(doc, "Instrument", "Didas") // 10 -> 5 chars
        val reparsedShorter = DwpDocument.parse(shorter.toBytes())
        assertEquals("Didas_B6_127", DwpSampleCatalog.listSamples(reparsedShorter)[47].name)

        val longer = DwpRenamer.renameInstrument(doc, "Instrument", "MiInstrumentoLargo") // 10 -> 18 chars
        val reparsedLonger = DwpDocument.parse(longer.toBytes())
        assertEquals("MiInstrumentoLargo_B6_127", DwpSampleCatalog.listSamples(reparsedLonger)[47].name)
    }

    @Test
    fun `renameInstrument refuses to edit a document with a corrupted sample container`() {
        val corruptedDoc = DwpTestFixtures.buildDocWithCorruptedSampleContainer()
        try {
            DwpRenamer.renameInstrument(corruptedDoc, "Instrument", "Didas")
            throw AssertionError("Expected DwpFormatException; must not silently drop the 5 trailing bytes")
        } catch (e: DwpFormatException) {
            // Expected: refuses to reserialize a container it can't fully account for.
        }
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
        val original = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val renamed = DwpRenamer.renameInstrument(original, "Instrument", "Didas")

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
        val original = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
        val corrupted = DwpTestFixtures.buildDocWithCorruptedSampleContainer() // corrupts sample #0 specifically
        val originalBytesBeforeAttempt = original.toBytes()

        try {
            DwpRenamer.renameInstrument(corrupted, "Instrument", "Didas")
            throw AssertionError("Expected DwpFormatException due to the corrupted container")
        } catch (e: DwpFormatException) {
            // Expected.
        }

        // The ORIGINAL (uninvolved) document must be completely unaffected --
        // DwpDocument/DwpBlock are immutable, so this is really confirming no
        // shared mutable state was touched anywhere in the call chain.
        assertArrayEquals(originalBytesBeforeAttempt, original.toBytes())
    }
}
