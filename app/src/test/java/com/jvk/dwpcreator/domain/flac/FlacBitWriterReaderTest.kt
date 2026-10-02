package com.jvk.dwpcreator.domain.flac

import org.junit.Assert.assertEquals
import org.junit.Test

class FlacBitWriterReaderTest {

    @Test
    fun `writes and reads back arbitrary bit widths round trip`() {
        val bw = FlacBitWriter()
        bw.writeBits(0x3FFEL, 14)
        bw.writeBits(1L, 1)
        bw.writeBits(0L, 3)
        bw.writeBits(255L, 8)
        bw.writeBits(0L, 6)
        bw.byteAlign()
        val bytes = bw.toByteArray()

        val br = FlacBitReader(bytes)
        assertEquals(0x3FFE, br.readBits(14))
        assertEquals(1, br.readBits(1))
        assertEquals(0, br.readBits(3))
        assertEquals(255, br.readBits(8))
        assertEquals(0, br.readBits(6))
    }

    @Test
    fun `unary code round trips for several values including zero`() {
        for (n in listOf(0, 1, 5, 17, 300)) {
            val bw = FlacBitWriter()
            bw.writeUnary(n)
            bw.byteAlign()
            val br = FlacBitReader(bw.toByteArray())
            assertEquals(n, br.readUnary())
        }
    }

    @Test
    fun `signed bits round trip for negative and positive values`() {
        val values = listOf(-32768L, -1L, 0L, 1L, 32767L)
        for (v in values) {
            val bw = FlacBitWriter()
            bw.writeBits(v, 16)
            bw.byteAlign()
            val br = FlacBitReader(bw.toByteArray())
            assertEquals(v, br.readSignedBits(16))
        }
    }

    @Test
    fun `byteAlign pads with zero bits`() {
        val bw = FlacBitWriter()
        bw.writeBits(1L, 1) // 1 bit written -> 7 bits of padding expected
        bw.byteAlign()
        val bytes = bw.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(0x80.toByte(), bytes[0]) // 1000000
    }

    @Test(expected = IllegalStateException::class)
    fun `toByteArray throws if bits are pending unaligned`() {
        val bw = FlacBitWriter()
        bw.writeBits(1L, 3)
        bw.toByteArray()
    }
}
