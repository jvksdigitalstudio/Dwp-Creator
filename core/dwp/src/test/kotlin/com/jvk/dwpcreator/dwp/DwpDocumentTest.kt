package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parseo, versión y round-trip sin pérdida de [DwpDocument].
 * Se ejecuta contra el `Instrument.dwp` real (ver [DwpTestFixtures]).
 */
class DwpDocumentTest {

    @Test
    fun `real file parses with zero leftover bytes`() {
        val bytes = DwpTestFixtures.loadRealFixture()
        val doc = DwpDocument.parse(bytes) // throws DwpFormatException on any mismatch
        assertTrue(doc.blocks.isNotEmpty())
    }

    @Test
    fun `parse then toBytes is a lossless roundtrip on the real file`() {
        val bytes = DwpTestFixtures.loadRealFixture()
        val doc = DwpDocument.parse(bytes)
        val rebuilt = doc.toBytes()
        assertTrue("Re-serialized bytes must match the original file exactly", bytes.contentEquals(rebuilt))
    }

    // --- Sección 10-14 del prompt maestro: DwpVersionProfile / versionado.

    @Test
    fun `parse recognizes the confirmed v0x26 profile and preserves round-trip`() {
        // Re-confirms the existing round-trip guarantee still holds after
        // routing preamble size through DwpVersionProfile instead of a
        // hardcoded constant.
        val bytes = DwpTestFixtures.loadRealFixture()
        val doc = DwpDocument.parse(bytes)
        assertArrayEquals(bytes, doc.toBytes())
    }

    @Test
    fun `parse rejects an unrecognized version instead of guessing a preamble layout`() {
        val original = DwpTestFixtures.loadRealFixture()
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
        val original = DwpTestFixtures.loadRealFixture()
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
}
