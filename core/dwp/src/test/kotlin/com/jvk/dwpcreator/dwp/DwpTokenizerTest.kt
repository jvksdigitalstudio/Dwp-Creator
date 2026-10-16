package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tokenización de bloques de [DwpTokenizer]: razones de parada y modo estricto.
 * Se ejecuta contra el `Instrument.dwp` real (ver [DwpTestFixtures]).
 */
class DwpTokenizerTest {

    @Test
    fun `tokenizeStrict throws instead of silently dropping unconsumed trailing bytes`() {
        val realDoc = DwpDocument.parse(DwpTestFixtures.loadRealFixture())
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
    fun `tokenize reports CLEAN_EOF when the stream is consumed exactly`() {
        val buf = DwpTestFixtures.block(0x1234, byteArrayOf(1, 2, 3))
        val result = DwpTokenizer.tokenize(buf)
        assertEquals(DwpTokenizer.StopReason.CLEAN_EOF, result.stopReason)
        assertEquals(buf.size, result.endOffset)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun `tokenize reports TRAILING_BYTES for leftover bytes too small for another header`() {
        val buf = DwpTestFixtures.block(0x1234, byteArrayOf(1, 2, 3)) + byteArrayOf(9, 9) // 2 stray bytes, need 12 for a header
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
        repeat(5) { out.write(DwpTestFixtures.block(0x0001, ByteArray(0))) }
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
