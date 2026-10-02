package com.jvk.dwpcreator.domain.flac

/**
 * Esquema de codificación "UTF-8 extendido" que la especificación pública
 * de FLAC usa para el campo de número de frame (stream de blocksize fijo,
 * el único modo que [FlacFrameEncoder] produce) dentro del frame header.
 * Es una variante de UTF-8 extendida hasta 7 bytes / 36 bits en vez de los
 * 4 bytes / 21 bits del UTF-8 estándar. Compartido entre encoder y decoder
 * para no duplicar esta lógica de bajo nivel en ambos lados.
 */
object FlacUtf8 {

    fun write(bw: FlacBitWriter, value: Long) {
        require(value >= 0) { "value=$value no puede ser negativo" }
        when {
            value < 0x80L -> bw.writeBits(value, 8)
            value < 0x800L -> {
                bw.writeBits(0xC0L or (value ushr 6), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            value < 0x10000L -> {
                bw.writeBits(0xE0L or (value ushr 12), 8)
                bw.writeBits(0x80L or ((value ushr 6) and 0x3F), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            value < 0x200000L -> {
                bw.writeBits(0xF0L or (value ushr 18), 8)
                bw.writeBits(0x80L or ((value ushr 12) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 6) and 0x3F), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            value < 0x4000000L -> {
                bw.writeBits(0xF8L or (value ushr 24), 8)
                bw.writeBits(0x80L or ((value ushr 18) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 12) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 6) and 0x3F), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            value < 0x80000000L -> {
                bw.writeBits(0xFCL or (value ushr 30), 8)
                bw.writeBits(0x80L or ((value ushr 24) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 18) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 12) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 6) and 0x3F), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            value < 0x1000000000L -> {
                bw.writeBits(0xFEL, 8)
                bw.writeBits(0x80L or ((value ushr 30) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 24) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 18) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 12) and 0x3F), 8)
                bw.writeBits(0x80L or ((value ushr 6) and 0x3F), 8)
                bw.writeBits(0x80L or (value and 0x3F), 8)
            }
            else -> throw IllegalArgumentException("value=$value excede 36 bits, no representable por el esquema UTF8 extendido de FLAC")
        }
    }

    fun read(br: FlacBitReader): Long {
        val first = br.readBits(8)
        if ((first and 0x80) == 0) return first.toLong()
        val extraBytes: Int
        var value: Long
        when {
            (first and 0xE0) == 0xC0 -> { extraBytes = 1; value = (first and 0x1F).toLong() }
            (first and 0xF0) == 0xE0 -> { extraBytes = 2; value = (first and 0x0F).toLong() }
            (first and 0xF8) == 0xF0 -> { extraBytes = 3; value = (first and 0x07).toLong() }
            (first and 0xFC) == 0xF8 -> { extraBytes = 4; value = (first and 0x03).toLong() }
            (first and 0xFE) == 0xFC -> { extraBytes = 5; value = (first and 0x01).toLong() }
            first == 0xFE -> { extraBytes = 6; value = 0L }
            else -> throw FlacBitReader.FlacBitstreamException(
                "Byte inicial de número de frame (UTF8 extendido) inválido: 0x${first.toString(16)}"
            )
        }
        repeat(extraBytes) {
            val b = br.readBits(8)
            if ((b and 0xC0) != 0x80) {
                throw FlacBitReader.FlacBitstreamException("Byte de continuación UTF8 inválido: 0x${b.toString(16)}")
            }
            value = (value shl 6) or (b and 0x3F).toLong()
        }
        return value
    }
}
