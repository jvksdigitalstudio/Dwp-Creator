package com.jvk.dwpcreator.domain.flac

import java.io.ByteArrayOutputStream

/**
 * Escritor de bits MSB-first (bit más significativo primero), tal como lo
 * exige el bitstream de FLAC para el frame header, los subframes y la
 * codificación Rice de los residuales. No conoce nada de DWP ni de la
 * semántica de FLAC de más alto nivel (STREAMINFO, subframes, frames): es
 * deliberadamente una utilidad de bajo nivel, reutilizable por
 * [FlacFrameEncoder] y [FlacRiceCoder] sin que ninguno de ellos duplique
 * lógica de empaquetado de bits.
 *
 * Al terminar de escribir un frame completo, [byteAlign] debe llamarse una
 * única vez para rellenar con ceros hasta el siguiente byte, tal como exige
 * la especificación antes del CRC-16 de pie de frame.
 */
class FlacBitWriter {

    private val out = ByteArrayOutputStream()
    private var currentByte = 0
    private var bitsInCurrentByte = 0

    /** Escribe los [bitCount] bits menos significativos de [value], MSB primero. */
    fun writeBits(value: Long, bitCount: Int) {
        require(bitCount in 0..64) { "bitCount=$bitCount fuera de rango 0..64" }
        var remaining = bitCount
        while (remaining > 0) {
            val bit = ((value ushr (remaining - 1)) and 1L).toInt()
            currentByte = (currentByte shl 1) or bit
            bitsInCurrentByte++
            if (bitsInCurrentByte == 8) {
                out.write(currentByte and 0xFF)
                currentByte = 0
                bitsInCurrentByte = 0
            }
            remaining--
        }
    }

    fun writeBits(value: Int, bitCount: Int) = writeBits(value.toLong() and 0xFFFFFFFFL, bitCount)

    /** Escribe [count] ceros seguidos de un 1 (código unario, usado por Rice coding). */
    fun writeUnary(count: Int) {
        require(count >= 0) { "count=$count no puede ser negativo" }
        repeat(count) { writeBits(0L, 1) }
        writeBits(1L, 1)
    }

    /** Rellena con ceros hasta completar el byte actual (padding de alineación de frame). */
    fun byteAlign() {
        if (bitsInCurrentByte > 0) {
            currentByte = currentByte shl (8 - bitsInCurrentByte)
            out.write(currentByte and 0xFF)
            currentByte = 0
            bitsInCurrentByte = 0
        }
    }

    /** Bytes escritos hasta ahora. Requiere que [byteAlign] ya se haya llamado. */
    fun toByteArray(): ByteArray {
        check(bitsInCurrentByte == 0) { "toByteArray() llamado con bits pendientes sin alinear a byte; llamar byteAlign() antes." }
        return out.toByteArray()
    }

    /** Número de bytes completos escritos hasta ahora (sin contar bits parciales pendientes). */
    fun byteCountSoFar(): Int = out.size()
}
