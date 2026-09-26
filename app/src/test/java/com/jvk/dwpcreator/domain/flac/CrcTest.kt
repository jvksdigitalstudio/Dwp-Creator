package com.jvk.dwpcreator.domain.flac

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CRC-8 (poly 0x07) y CRC-16 (poly 0x8005), ambos sin reflejar y con
 * inicial 0, tienen vectores de verificación públicos y ampliamente
 * conocidos para la cadena ASCII "123456789":
 * - CRC-8/SMBUS: 0xF4
 * - CRC-16/BUYPASS: 0xFEE8
 * Estos son exactamente los parámetros que usa FLAC para el frame header
 * (CRC-8) y el frame footer (CRC-16). Verificarlos contra estos vectores
 * de terceros (no derivados de este proyecto) es la forma más fuerte de
 * confirmar que [Crc] implementa el polinomio/parámetros correctos, antes
 * de confiar en él para el resto del subsistema FLAC.
 */
class CrcTest {

    private val checkString = "123456789".toByteArray(Charsets.US_ASCII)

    @Test
    fun `crc8 matches CRC-8-SMBUS known check value`() {
        assertEquals(0xF4, Crc.crc8(checkString))
    }

    @Test
    fun `crc16 matches CRC-16-BUYPASS known check value`() {
        assertEquals(0xFEE8, Crc.crc16(checkString))
    }

    @Test
    fun `crc8 of empty input is zero`() {
        assertEquals(0, Crc.crc8(ByteArray(0)))
    }

    @Test
    fun `crc16 of empty input is zero`() {
        assertEquals(0, Crc.crc16(ByteArray(0)))
    }

    @Test
    fun `crc8 respects from-until range`() {
        val data = byteArrayOf(0x00, 0x31, 0x32, 0x33, 0x00) // "123" padded
        val full = Crc.crc8(byteArrayOf(0x31, 0x32, 0x33))
        val ranged = Crc.crc8(data, from = 1, until = 4)
        assertEquals(full, ranged)
    }

    @Test
    fun `crc16 respects from-until range`() {
        val data = byteArrayOf(0x00, 0x31, 0x32, 0x33, 0x00)
        val full = Crc.crc16(byteArrayOf(0x31, 0x32, 0x33))
        val ranged = Crc.crc16(data, from = 1, until = 4)
        assertEquals(full, ranged)
    }

    @Test
    fun `crc8 over all 256 byte values matches independently computed vector`() {
        val data = ByteArray(256) { it.toByte() }
        // Vector calculado de forma independiente (Python, misma tabla/polinomio 0x07, sin reflejar).
        assertEquals(0x14, Crc.crc8(data))
    }

    @Test
    fun `crc16 over all 256 byte values matches independently computed vector`() {
        val data = ByteArray(256) { it.toByte() }
        assertEquals(0x3B7A, Crc.crc16(data))
    }
}
