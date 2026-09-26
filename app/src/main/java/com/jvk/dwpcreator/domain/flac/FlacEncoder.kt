package com.jvk.dwpcreator.domain.flac

import java.security.MessageDigest

class FlacEncodingException(message: String) : Exception(message)

/**
 * Codifica [FlacPcmAudio] a un stream FLAC nativo completo y válido
 * (`fLaC` + STREAMINFO + frames), priorizando corrección sobre
 * optimización (Sección 19 del prompt maestro de esta fase). Único punto
 * de entrada público del subsistema FLAC; orquesta
 * [FlacMetadataWriter]/[FlacFrameEncoder]/[FlacStreamWriter] sin conocer
 * nada de DWP.
 */
object FlacEncoder {

    /** Tamaño de bloque por defecto (muestras por frame), igual al valor por defecto del encoder de referencia de FLAC. */
    const val DEFAULT_BLOCK_SIZE = 4096

    fun encode(pcm: FlacPcmAudio, blockSize: Int = DEFAULT_BLOCK_SIZE): ByteArray {
        // Límite real: 1..0xFFFF (65535), NO 0x10000 (65536). El campo de
        // block size en el frame header (código de escape 0111, 16 bits
        // "blocksize-1") técnicamente podría codificar hasta 65536 en un
        // frame aislado, pero los campos min_block_size/max_block_size de
        // STREAMINFO son enteros SIN offset de 16 bits (0..65535, sección
        // "METADATA_BLOCK_STREAMINFO" de la especificación pública de FLAC):
        // no existe ninguna forma válida de declarar un blockSize=65536 ahí.
        // Bug real encontrado en la auditoría de FASE 1 (Doc/26): antes de
        // esta corrección, `encode(pcm, blockSize = 0x10000)` pasaba este
        // require() y solo fallaba más tarde, dentro de
        // `FlacMetadataWriter.StreamInfo.init`, con un `IllegalArgumentException`
        // opaca ("maxBlockSize fuera de rango 16 bits") en vez de rechazarse
        // aquí mismo con un mensaje claro -- y solo si `pcm.frameCount` era
        // lo bastante grande para que el encoder llegara a usar realmente
        // ese tamaño de bloque completo (ver `FlacFixedPredictorTest`/
        // `FlacEncoderRoundTripTest` para la cobertura de este límite).
        require(blockSize in 1..0xFFFF) { "blockSize=$blockSize fuera de rango representable (1..65535; STREAMINFO min/max_block_size son campos de 16 bits sin offset)" }
        if (pcm.frameCount == 0) {
            throw FlacEncodingException("No se puede codificar audio con frameCount=0 (sin muestras).")
        }

        val frames = mutableListOf<ByteArray>()
        var frameIndex = 0L
        var offset = 0
        var minBlockSizeUsed = Int.MAX_VALUE
        var maxBlockSizeUsed = 0
        var minFrameSize = Int.MAX_VALUE
        var maxFrameSize = 0

        while (offset < pcm.frameCount) {
            val thisBlockSize = minOf(blockSize, pcm.frameCount - offset)
            val channelSlices = Array(pcm.channelCount) { ch ->
                pcm.channels[ch].copyOfRange(offset, offset + thisBlockSize)
            }
            val frameBytes = FlacFrameEncoder.encodeFrame(frameIndex, channelSlices, pcm.bitsPerSample)
            frames.add(frameBytes)

            minBlockSizeUsed = minOf(minBlockSizeUsed, thisBlockSize)
            maxBlockSizeUsed = maxOf(maxBlockSizeUsed, thisBlockSize)
            minFrameSize = minOf(minFrameSize, frameBytes.size)
            maxFrameSize = maxOf(maxFrameSize, frameBytes.size)

            offset += thisBlockSize
            frameIndex++
        }

        val streamInfo = FlacMetadataWriter.StreamInfo(
            minBlockSize = minBlockSizeUsed,
            maxBlockSize = maxBlockSizeUsed,
            minFrameSize = minFrameSize,
            maxFrameSize = maxFrameSize,
            sampleRateHz = pcm.sampleRateHz,
            channelCount = pcm.channelCount,
            bitsPerSample = pcm.bitsPerSample,
            totalSamples = pcm.frameCount.toLong(),
            md5 = computeMd5(pcm)
        )
        val streamInfoBlock = FlacMetadataWriter.write(streamInfo, isLastMetadataBlock = true)
        return FlacStreamWriter.assemble(streamInfoBlock, frames)
    }

    /**
     * MD5 del audio sin codificar tal como lo exige STREAMINFO: muestras
     * intercaladas por frame (interleaved), como enteros con signo en
     * complemento a dos, little-endian, cada una redondeada al número
     * entero de bytes más próximo por arriba ([bytesPerSampleContainer]).
     * Se calcula sobre la representación que este propio encoder produce
     * (no sobre los bytes crudos de origen), para que MD5 y STREAMINFO
     * describan exactamente el mismo dato que los frames codifican.
     */
    /**
     * Visibilidad pública (Pass PRE-CI, `Doc/25`): [MonolithicDwpValidator]
     * la necesita para recalcular el MD5 del PCM decodificado y compararlo
     * contra el declarado en STREAMINFO tras un round-trip -- exactamente
     * la responsabilidad que Doc/23 §7.2 ya asignaba al validador
     * ("sample count, MD5, CRC") y que no estaba implementada: el campo
     * MD5 se escribía correctamente en STREAMINFO pero nunca se
     * verificaba en ningún punto del pipeline, así que una corrupción
     * localizada exactamente en esos 16 bytes pasaba inadvertida (bug
     * real detectado en la primera ejecución de CI:
     * `MonolithicDwpValidatorTest > validate detects a corrupted embedded
     * FLAC payload` fallaba precisamente porque el byte corrompido caía
     * dentro del campo MD5, que nada comparaba). Sin cambios de lógica
     * respecto a la versión privada original.
     */
    fun computeMd5(pcm: FlacPcmAudio): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        val bytesPerSample = bytesPerSampleContainer(pcm.bitsPerSample)
        val frameBuf = ByteArray(bytesPerSample)
        for (i in 0 until pcm.frameCount) {
            for (ch in 0 until pcm.channelCount) {
                writeLittleEndianSigned(pcm.channels[ch][i].toLong(), bytesPerSample, frameBuf)
                md.update(frameBuf)
            }
        }
        return md.digest()
    }

    private fun bytesPerSampleContainer(bitsPerSample: Int): Int = (bitsPerSample + 7) / 8

    private fun writeLittleEndianSigned(value: Long, byteCount: Int, out: ByteArray) {
        var v = value
        for (i in 0 until byteCount) {
            out[i] = (v and 0xFF).toByte()
            v = v shr 8
        }
    }
}
