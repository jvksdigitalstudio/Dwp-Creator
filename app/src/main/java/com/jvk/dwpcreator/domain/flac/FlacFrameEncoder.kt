package com.jvk.dwpcreator.domain.flac

/**
 * Codifica un único frame FLAC completo: frame header, un subframe por
 * canal, padding de alineación y el footer CRC-16 -- todo lo que exige la
 * especificación pública de FLAC para un stream de **blocksize fijo**
 * (single blocking strategy), que es el único modo que este encoder
 * produce (Doc/24 §Decisiones técnicas: no hay ninguna necesidad de
 * blocksize variable para audio de instrumento sampleado).
 *
 * Elección de subframe por canal, en este orden de prioridad:
 * 1. `CONSTANT` si todas las muestras del canal en este frame son idénticas.
 * 2. `VERBATIM` si el bloque tiene una sola muestra (no hay margen para un
 *    predictor fijo de ningún orden > 0 salvo el trivial orden 0, que
 *    igual se prueba primero vía [FlacFixedPredictor]).
 * 3. `FIXED` (orden 0-4, elegido por [FlacFixedPredictor.selectBestOrder])
 *    en cualquier otro caso -- el camino normal para audio real.
 *
 * No se implementa LPC ni wasted-bits-per-sample -- ver Doc/24 para la
 * justificación (simplicidad/verificabilidad sobre compresión óptima).
 */
object FlacFrameEncoder {

    private const val SYNC_CODE = 0x3FFEL // 14 bits: 11111111111110
    private const val SUBFRAME_TYPE_CONSTANT = 0b000000
    private const val SUBFRAME_TYPE_VERBATIM = 0b000001
    private const val SUBFRAME_TYPE_FIXED_BASE = 0b001000 // | order (0..4)

    /**
     * Codifica un frame para las muestras `channelSamples[canal][i]` (todos
     * los canales con el mismo tamaño de bloque, ya deinterleaved).
     * [frameIndex] es el número de frame 0-based dentro del stream (stream
     * de blocksize fijo: este campo es literalmente el índice de frame, no
     * un número de muestra).
     */
    fun encodeFrame(
        frameIndex: Long,
        channelSamples: Array<IntArray>,
        bitsPerSample: Int
    ): ByteArray {
        require(channelSamples.isNotEmpty()) { "channelSamples no puede estar vacío" }
        val blockSize = channelSamples[0].size
        // Mismo límite y misma razón que FlacEncoder.encode (1..0xFFFF, no
        // 0x10000): un frame con blockSize=65536 sería, en aislamiento,
        // representable por el campo de 16 bits "blocksize-1" del frame
        // header, pero el stream FLAC completo que lo contiene nunca podría
        // declarar ese valor en STREAMINFO.min/max_block_size (campos de 16
        // bits sin offset). Mantener ambos límites idénticos evita que este
        // componente de más bajo nivel pueda producir, por sí solo, un frame
        // que el único orquestador real (FlacEncoder) nunca podría ensamblar
        // en un stream válido.
        require(blockSize in 1..0xFFFF) { "blockSize=$blockSize fuera de rango representable (1..65535; ver FlacEncoder.encode)" }
        require(channelSamples.all { it.size == blockSize }) { "Todos los canales deben tener el mismo blockSize en un frame" }
        val channelCount = channelSamples.size
        require(channelCount in 1..8) { "channelCount=$channelCount fuera de rango soportado (1..8, codificación independiente)" }

        val bw = FlacBitWriter()
        bw.writeBits(SYNC_CODE, 14)
        bw.writeBits(0L, 1) // reserved, debe ser 0
        bw.writeBits(0L, 1) // blocking strategy: 0 = fixed-blocksize
        bw.writeBits(0b0111L, 4) // block size: código de escape "16-bit (blocksize-1) al final del header"
        bw.writeBits(0b0000L, 4) // sample rate: "obtener de STREAMINFO"
        bw.writeBits((channelCount - 1).toLong(), 4) // canales independientes: código = N-1
        bw.writeBits(0b000L, 3) // sample size: "obtener de STREAMINFO"
        bw.writeBits(0L, 1) // reserved, debe ser 0
        FlacUtf8.write(bw, frameIndex)
        bw.writeBits((blockSize - 1).toLong(), 16) // valor explícito de blocksize (por el código de escape 0111)

        val headerBytesSoFar = bw.toByteArray()
        val headerCrc8 = Crc.crc8(headerBytesSoFar)
        bw.writeBits(headerCrc8.toLong(), 8)

        for (ch in 0 until channelCount) {
            encodeSubframe(bw, channelSamples[ch], bitsPerSample)
        }

        bw.byteAlign()
        val frameBytesBeforeFooter = bw.toByteArray()
        val footerCrc16 = Crc.crc16(frameBytesBeforeFooter)
        bw.writeBits(footerCrc16.toLong(), 16)

        return bw.toByteArray()
    }

    private fun encodeSubframe(bw: FlacBitWriter, samples: IntArray, bitsPerSample: Int) {
        val allConstant = samples.all { it == samples[0] }
        when {
            allConstant -> {
                bw.writeBits(0L, 1)
                bw.writeBits(SUBFRAME_TYPE_CONSTANT.toLong(), 6)
                bw.writeBits(0L, 1) // sin wasted bits
                bw.writeBits(samples[0].toLong(), bitsPerSample)
            }
            samples.size == 1 -> {
                bw.writeBits(0L, 1)
                bw.writeBits(SUBFRAME_TYPE_VERBATIM.toLong(), 6)
                bw.writeBits(0L, 1)
                bw.writeBits(samples[0].toLong(), bitsPerSample)
            }
            else -> {
                val order = FlacFixedPredictor.selectBestOrder(samples, 0, samples.size)
                val residual = FlacFixedPredictor.residual(samples, 0, samples.size, order)
                bw.writeBits(0L, 1)
                bw.writeBits((SUBFRAME_TYPE_FIXED_BASE or order).toLong(), 6)
                bw.writeBits(0L, 1) // sin wasted bits
                for (i in 0 until order) {
                    bw.writeBits(samples[i].toLong(), bitsPerSample)
                }
                bw.writeBits(0L, 2) // residual coding method 0: Rice de 4 bits
                FlacRiceCoder.encode(bw, residual)
            }
        }
    }
}
