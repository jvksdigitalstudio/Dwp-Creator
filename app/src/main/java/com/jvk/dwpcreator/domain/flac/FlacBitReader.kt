package com.jvk.dwpcreator.domain.flac

/**
 * Lector de bits MSB-first, inverso exacto de [FlacBitWriter]. Usado por
 * [FlacDecoder] (y, por extensión, por cualquier validador que necesite
 * decodificar de vuelta el subconjunto de FLAC que [FlacEncoder] produce
 * para verificar round-trip).
 */
class FlacBitReader(private val data: ByteArray, startByteOffset: Int = 0) {

    private var byteIndex = startByteOffset
    private var bitIndex = 0 // 0..7, bits ya consumidos del byte actual

    class FlacBitstreamException(message: String) : Exception(message)

    fun readBits(bitCount: Int): Int = readBitsLong(bitCount).toInt()

    fun readBitsLong(bitCount: Int): Long {
        require(bitCount in 0..64) { "bitCount=$bitCount fuera de rango 0..64" }
        var result = 0L
        var remaining = bitCount
        while (remaining > 0) {
            if (byteIndex >= data.size) {
                throw FlacBitstreamException("Fin de stream inesperado leyendo $bitCount bits (offset de byte=$byteIndex, tamaño=${data.size}).")
            }
            val currentByte = data[byteIndex].toInt() and 0xFF
            val bitsLeftInByte = 8 - bitIndex
            val take = minOf(bitsLeftInByte, remaining)
            val shift = bitsLeftInByte - take
            val mask = (1 shl take) - 1
            val bits = (currentByte ushr shift) and mask
            result = (result shl take) or bits.toLong()
            bitIndex += take
            remaining -= take
            if (bitIndex == 8) {
                bitIndex = 0
                byteIndex++
            }
        }
        return result
    }

    /** Lee [bitCount] bits como entero con signo en complemento a dos. */
    fun readSignedBits(bitCount: Int): Long {
        val raw = readBitsLong(bitCount)
        if (bitCount == 64) return raw
        val signBit = 1L shl (bitCount - 1)
        return if ((raw and signBit) != 0L) raw - (1L shl bitCount) else raw
    }

    /** Lee un código unario: cuenta ceros hasta el primer 1 (sin incluirlo). */
    fun readUnary(): Int {
        var count = 0
        while (readBitsLong(1) == 0L) {
            count++
            if (count > 10_000_000) {
                throw FlacBitstreamException("Código unario sospechosamente largo (>10,000,000 ceros); stream corrupto.")
            }
        }
        return count
    }

    /** Avanza hasta el siguiente límite de byte, descartando bits de padding. */
    fun byteAlign() {
        if (bitIndex != 0) {
            bitIndex = 0
            byteIndex++
        }
    }

    /** Offset de byte actual (válido tras [byteAlign], o si ya se está alineado). */
    fun byteOffset(): Int {
        check(bitIndex == 0) { "byteOffset() llamado sin alinear a byte." }
        return byteIndex
    }

    /** Lee [n] bytes crudos; requiere estar alineado a byte. */
    fun readRawBytes(n: Int): ByteArray {
        check(bitIndex == 0) { "readRawBytes() requiere estar alineado a byte." }
        if (byteIndex + n > data.size) {
            throw FlacBitstreamException("Fin de stream inesperado leyendo $n bytes crudos (offset=$byteIndex, tamaño=${data.size}).")
        }
        val out = data.copyOfRange(byteIndex, byteIndex + n)
        byteIndex += n
        return out
    }

    fun seekToByte(offset: Int) {
        byteIndex = offset
        bitIndex = 0
    }

    fun hasMoreBytes(): Boolean = byteIndex < data.size
}
