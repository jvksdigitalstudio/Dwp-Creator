package com.jvk.dwpcreator.domain.flac

import java.io.ByteArrayOutputStream

/**
 * Construye el bloque de metadata STREAMINFO de FLAC, el único bloque de
 * metadata que este encoder emite (Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md
 * §17: "el mínimo viable... solo STREAMINFO... salvo que un fixture real
 * confirme que hace falta más"). No se emiten SEEKTABLE, VORBIS_COMMENT ni
 * PADDING — decisión deliberada, no una omisión accidental.
 *
 * Estructura exacta (especificación pública de FLAC, sección "METADATA_BLOCK_STREAMINFO"):
 * cabecera de metadata (4 bytes) + cuerpo STREAMINFO (34 bytes) = 38 bytes totales.
 */
object FlacMetadataWriter {

    const val STREAMINFO_BODY_SIZE = 34
    private const val BLOCK_TYPE_STREAMINFO = 0

    data class StreamInfo(
        val minBlockSize: Int,
        val maxBlockSize: Int,
        val minFrameSize: Int,
        val maxFrameSize: Int,
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val totalSamples: Long,
        val md5: ByteArray
    ) {
        init {
            require(minBlockSize in 0..0xFFFF) { "minBlockSize fuera de rango 16 bits: $minBlockSize" }
            require(maxBlockSize in 0..0xFFFF) { "maxBlockSize fuera de rango 16 bits: $maxBlockSize" }
            require(minFrameSize in 0..0xFFFFFF) { "minFrameSize fuera de rango 24 bits: $minFrameSize" }
            require(maxFrameSize in 0..0xFFFFFF) { "maxFrameSize fuera de rango 24 bits: $maxFrameSize" }
            require(sampleRateHz in 1..0xFFFFF) { "sampleRateHz fuera de rango 20 bits: $sampleRateHz" }
            require(channelCount in 1..8) { "channelCount fuera de rango soportado (1..8, codificación independiente): $channelCount" }
            require(bitsPerSample in 4..32) { "bitsPerSample fuera de rango STREAMINFO (4..32): $bitsPerSample" }
            require(totalSamples in 0..0xFFFFFFFFFL) { "totalSamples fuera de rango 36 bits: $totalSamples" }
            require(md5.size == 16) { "md5 debe tener exactamente 16 bytes, tiene ${md5.size}" }
        }

        // ByteArray no tiene equals/hashCode estructural en Kotlin/JVM (es
        // identidad de referencia); sin este override, dos StreamInfo con el
        // mismo MD5 pero distinta instancia de array compararían como
        // distintos -- el mismo motivo por el que DwpBlock, en el Core,
        // sobrescribe equals/hashCode en vez de usar los generados por
        // `data class` a ciegas.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StreamInfo) return false
            return minBlockSize == other.minBlockSize &&
                maxBlockSize == other.maxBlockSize &&
                minFrameSize == other.minFrameSize &&
                maxFrameSize == other.maxFrameSize &&
                sampleRateHz == other.sampleRateHz &&
                channelCount == other.channelCount &&
                bitsPerSample == other.bitsPerSample &&
                totalSamples == other.totalSamples &&
                md5.contentEquals(other.md5)
        }

        override fun hashCode(): Int {
            var result = minBlockSize
            result = 31 * result + maxBlockSize
            result = 31 * result + minFrameSize
            result = 31 * result + maxFrameSize
            result = 31 * result + sampleRateHz
            result = 31 * result + channelCount
            result = 31 * result + bitsPerSample
            result = 31 * result + totalSamples.hashCode()
            result = 31 * result + md5.contentHashCode()
            return result
        }
    }

    /**
     * Serializa [info] como METADATA_BLOCK (header + STREAMINFO body).
     * [isLastMetadataBlock] debe ser `true` en este encoder porque STREAMINFO
     * es el único bloque de metadata emitido -- ver la KDoc de esta clase.
     */
    fun write(info: StreamInfo, isLastMetadataBlock: Boolean = true): ByteArray {
        val bw = FlacBitWriter()
        bw.writeBits(if (isLastMetadataBlock) 1L else 0L, 1)
        bw.writeBits(BLOCK_TYPE_STREAMINFO.toLong(), 7)
        bw.writeBits(STREAMINFO_BODY_SIZE.toLong(), 24)

        bw.writeBits(info.minBlockSize.toLong(), 16)
        bw.writeBits(info.maxBlockSize.toLong(), 16)
        bw.writeBits(info.minFrameSize.toLong(), 24)
        bw.writeBits(info.maxFrameSize.toLong(), 24)
        bw.writeBits(info.sampleRateHz.toLong(), 20)
        bw.writeBits((info.channelCount - 1).toLong(), 3)
        bw.writeBits((info.bitsPerSample - 1).toLong(), 5)
        bw.writeBits(info.totalSamples, 36)
        bw.byteAlign()

        val out = ByteArrayOutputStream()
        out.write(bw.toByteArray())
        out.write(info.md5)
        return out.toByteArray()
    }
}
