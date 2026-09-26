package com.jvk.dwpcreator.domain.flac

/**
 * CRC-8 y CRC-16 exactamente como los exige la especificación pública de
 * FLAC (Xiph.Org, "FLAC — Format", sección "frame header"/"frame footer"):
 *
 * - CRC-8: polinomio x^8 + x^2 + x^1 + x^0 (0x07), sin reflejar, inicial 0,
 *   cubre el frame header completo (desde el sync code hasta el final del
 *   campo del número de frame/muestra y el tamaño de bloque si se codificó
 *   explícito) y se almacena como el último byte del header.
 * - CRC-16: polinomio x^16 + x^15 + x^2 + x^0 (0x8005), sin reflejar,
 *   inicial 0, cubre el frame **completo** (header + todos los subframes +
 *   padding de alineación a byte) y se almacena como los 2 bytes finales
 *   del frame, big-endian.
 *
 * Estos dos polinomios son un hecho de la especificación pública de FLAC,
 * no una decisión específica de DwpCreator ni una inferencia sobre DWP —
 * se implementan tal cual el estándar los define, sin reflexión de bits
 * (a diferencia del CRC-32 de zlib usado en otras partes del proyecto).
 */
object Crc {

    private val TABLE_8 = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var crc = i
            repeat(8) {
                crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
            table[i] = crc
        }
    }

    private val TABLE_16 = IntArray(256).also { table ->
        for (i in 0 until 256) {
            var crc = i shl 8
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
            table[i] = crc
        }
    }

    /** CRC-8 sobre [bytes]\[from, until). */
    fun crc8(bytes: ByteArray, from: Int = 0, until: Int = bytes.size): Int {
        var crc = 0
        for (i in from until until) {
            val b = bytes[i].toInt() and 0xFF
            crc = TABLE_8[(crc xor b) and 0xFF]
        }
        return crc and 0xFF
    }

    /** CRC-16 sobre [bytes]\[from, until). */
    fun crc16(bytes: ByteArray, from: Int = 0, until: Int = bytes.size): Int {
        var crc = 0
        for (i in from until until) {
            val b = bytes[i].toInt() and 0xFF
            crc = TABLE_16[((crc ushr 8) xor b) and 0xFF] xor ((crc shl 8) and 0xFFFF)
        }
        return crc and 0xFFFF
    }
}
