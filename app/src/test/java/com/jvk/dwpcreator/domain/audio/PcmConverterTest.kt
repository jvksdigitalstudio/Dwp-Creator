package com.jvk.dwpcreator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmConverterTest {

    private fun floatBytes(vararg values: Float): ByteArray {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putFloat(it) }
        return buf.array()
    }

    private fun readInt16(bytes: ByteArray, index: Int): Short {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(index * 2)
    }

    @Test
    fun `converts full-scale values correctly`() {
        val input = floatBytes(1.0f, -1.0f, 0.0f)
        val out = PcmConverter.floatToInt16(input)

        assertEquals(6, out.size) // 3 samples * 2 bytes
        assertEquals(Short.MAX_VALUE, readInt16(out, 0))
        assertEquals((-Short.MAX_VALUE).toShort(), readInt16(out, 1))
        assertEquals(0.toShort(), readInt16(out, 2))
    }

    @Test
    fun `clamps out-of-range values instead of overflowing`() {
        val input = floatBytes(2.0f, -3.5f)
        val out = PcmConverter.floatToInt16(input)

        assertEquals(Short.MAX_VALUE, readInt16(out, 0))
        assertEquals((-Short.MAX_VALUE).toShort(), readInt16(out, 1))
    }

    @Test
    fun `half-scale value maps to roughly half of int16 range`() {
        val input = floatBytes(0.5f)
        val out = PcmConverter.floatToInt16(input)
        val value = readInt16(out, 0).toInt()

        assertEquals((Short.MAX_VALUE / 2).toDouble(), value.toDouble(), 5.0) // small tolerance for rounding
    }

    @Test
    fun `uint8ToInt16 centers unsigned 8-bit range around zero`() {
        val out = PcmConverter.uint8ToInt16(byteArrayOf(0.toByte(), 128.toByte(), 255.toByte()))
        assertEquals((-128 shl 8).toShort(), readInt16(out, 0)) // silence-adjacent low end
        assertEquals(0.toShort(), readInt16(out, 1))            // 128 = midpoint = digital silence
        assertEquals((127 shl 8).toShort(), readInt16(out, 2))  // near-top end
    }

    @Test
    fun `int24ToInt16 keeps the top 16 bits of each 24-bit sample`() {
        // 0x7FFFFF = max positive 24-bit sample, little-endian: FF FF 7F
        val out = PcmConverter.int24ToInt16(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte()))
        assertEquals(Short.MAX_VALUE, readInt16(out, 0))
    }

    @Test
    fun `int32ToInt16 keeps the top 16 bits of each 32-bit sample`() {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(Int.MAX_VALUE).array()
        val out = PcmConverter.int32ToInt16(bytes)
        assertEquals(Short.MAX_VALUE, readInt16(out, 0))
    }
}
