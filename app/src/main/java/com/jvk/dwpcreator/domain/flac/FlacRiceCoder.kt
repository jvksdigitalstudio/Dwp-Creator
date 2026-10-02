package com.jvk.dwpcreator.domain.flac

/**
 * Codificación Rice (método 0, parámetro de 4 bits) de los residuales de un
 * subframe FIXED, tal como la define la especificación pública de FLAC para
 * RESIDUAL_CODING_METHOD = 0 ("partitioned Rice coding with 4-bit
 * parameters"). Este encoder usa siempre **un único orden de partición (0)**
 * -- una sola partición que cubre todo el residual del subframe -- en vez de
 * buscar el orden de partición óptimo. Es una simplificación deliberada
 * (Doc/24, misma justificación que [FlacFixedPredictor]: corrección y
 * verificabilidad primero, compresión óptima queda como trabajo futuro) que
 * produce FLAC igualmente válido y perfectamente reversible, solo con una
 * tasa de compresión inferior a la de un encoder de referencia con
 * partition-order search completo.
 *
 * Incluye el mecanismo de escape (parámetro reservado `1111`) para no
 * producir un stream absurdamente largo en particiones patológicas
 * (silencio total, DC constante ya cubierto por SUBFRAME_CONSTANT, picos
 * aislados extremos, etc.): si codificar en binario sin comprimir resulta
 * más barato que el mejor parámetro Rice encontrado, se usa esa vía en su
 * lugar, exactamente como contempla la especificación.
 */
object FlacRiceCoder {

    private const val ESCAPE_PARAMETER = 15
    private const val MAX_RICE_PARAMETER = 14

    /**
     * Máximo valor representable en el campo de 5 bits que sigue al
     * parámetro de escape (`1111`) e indica cuántos bits sin comprimir se
     * usan por muestra en la vía de escape -- tal como lo exige la
     * especificación de FLAC para `RESIDUAL_CODING_METHOD_PARTITIONED_RICE`
     * (método 0). Un residual que necesite más de estos bits para
     * representarse en complemento a dos con signo no puede codificarse
     * de forma válida por esta vía: con el audio de 8/16/24-bit que
     * soporta este proyecto (`PcmNormalizer`), el residual de un
     * predictor FIXED de orden ≤4 nunca se acerca a este límite (peor
     * caso: ~28 bits para 24-bit de entrada) -- ver
     * [MAX_UNENCODED_BIT_WIDTH] en el KDoc de [maxUnencodedBitWidth].
     */
    private const val MAX_UNENCODED_BIT_WIDTH = 31

    private fun zigzag(v: Long): Long = (v shl 1) xor (v shr 63)
    private fun unzigzag(u: Long): Long = (u ushr 1) xor -(u and 1L)

    /** Escribe partition order = 0 seguido de la única partición codificada. */
    fun encode(bw: FlacBitWriter, residual: LongArray) {
        bw.writeBits(0L, 4) // partition order: una sola partición
        writePartition(bw, residual)
    }

    /** Inversa exacta de [encode]: lee partition order (debe ser 0 en este encoder) y decodifica. */
    fun decode(br: FlacBitReader, residualCount: Int): LongArray {
        val partitionOrder = br.readBits(4)
        check(partitionOrder == 0) {
            "Partition order $partitionOrder no soportado: este decoder solo entiende el subconjunto " +
                "que FlacEncoder produce (siempre partition order 0, una sola partición)."
        }
        return readPartition(br, residualCount)
    }

    private fun writePartition(bw: FlacBitWriter, residual: LongArray) {
        val zz = LongArray(residual.size) { zigzag(residual[it]) }
        val (bestK, riceBitCost) = selectBestParameter(zz)
        val bitWidth = maxUnencodedBitWidth(residual)
        val escapeBitCost = 5L + residual.size.toLong() * bitWidth

        if (riceBitCost <= escapeBitCost) {
            bw.writeBits(bestK.toLong(), 4)
            for (u in zz) {
                val q = u ushr bestK
                check(q <= Int.MAX_VALUE.toLong()) {
                    "Cociente Rice desbordó Int (q=$q, k=$bestK); el costo estimado debería haber preferido escape."
                }
                bw.writeUnary(q.toInt())
                if (bestK > 0) bw.writeBits(u and ((1L shl bestK) - 1), bestK)
            }
        } else {
            bw.writeBits(ESCAPE_PARAMETER.toLong(), 4)
            bw.writeBits(bitWidth.toLong(), 5)
            for (r in residual) {
                bw.writeBits(r, bitWidth)
            }
        }
    }

    private fun readPartition(br: FlacBitReader, count: Int): LongArray {
        val param = br.readBits(4)
        val out = LongArray(count)
        if (param == ESCAPE_PARAMETER) {
            val bitWidth = br.readBits(5)
            for (i in 0 until count) {
                out[i] = br.readSignedBits(bitWidth)
            }
        } else {
            for (i in 0 until count) {
                val q = br.readUnary()
                val remainder = if (param > 0) br.readBits(param).toLong() else 0L
                val u = (q.toLong() shl param) or remainder
                out[i] = unzigzag(u)
            }
        }
        return out
    }

    /** Devuelve (mejorParametroK, costeEnBitsDeEseParametro) probando 0..14 exhaustivamente. */
    private fun selectBestParameter(zigzagged: LongArray): Pair<Int, Long> {
        var bestK = 0
        var bestCost = Long.MAX_VALUE
        for (k in 0..MAX_RICE_PARAMETER) {
            var cost = zigzagged.size.toLong() * (k + 1)
            for (u in zigzagged) cost += (u ushr k)
            if (cost < bestCost) {
                bestCost = cost
                bestK = k
            }
        }
        return bestK to bestCost
    }

    /**
     * Ancho en bits (complemento a dos, con signo) necesario para
     * representar sin pérdida cada valor de [residual] en la vía de
     * escape. Lanza [FlacEncodingException] si ese ancho excede
     * [MAX_UNENCODED_BIT_WIDTH] (31): el campo del formato que lo
     * transporta tiene 5 bits, así que un ancho mayor no puede
     * representarse en absoluto -- antes de esta corrección, ese caso se
     * truncaba silenciosamente al escribirse (`bitWidth and 0b11111`),
     * produciendo un stream corrupto sin ningún aviso. Detectado en el
     * Pass PRE-CI (`Doc/25`) mediante la primera ejecución real de CI:
     * `FlacRiceCoderTest > round trips extreme Int boundary values` y
     * `> round trips pathological large alternating residuals` fallaban
     * en el round-trip real precisamente por este truncamiento.
     */
    private fun maxUnencodedBitWidth(residual: LongArray): Int {
        var maxAbs = 0L
        for (r in residual) {
            val a = if (r < 0) -r else r
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs == 0L) return 1
        val magnitudeBits = 64 - java.lang.Long.numberOfLeadingZeros(maxAbs)
        val bitWidth = magnitudeBits + 1
        if (bitWidth > MAX_UNENCODED_BIT_WIDTH) {
            throw FlacEncodingException(
                "Un residual requiere $bitWidth bits para representarse sin pérdida en la vía de escape, " +
                    "pero el campo de ancho de esa vía (5 bits, tal como exige la especificación de FLAC para " +
                    "RESIDUAL_CODING_METHOD=0) solo admite hasta $MAX_UNENCODED_BIT_WIDTH bits. Esto no ocurre " +
                    "con el audio de 8/16/24-bit que soporta este proyecto (Doc/23 §15); indica un residual " +
                    "fuera del dominio soportado por este codificador."
            )
        }
        return bitWidth
    }
}
