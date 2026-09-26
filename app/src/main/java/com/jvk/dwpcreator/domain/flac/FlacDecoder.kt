package com.jvk.dwpcreator.domain.flac

class FlacDecodingException(message: String) : Exception(message)

/**
 * Decodifica un stream FLAC de vuelta a PCM. **No es un decoder FLAC
 * genérico**: entiende exactamente el subconjunto que [FlacEncoder]
 * produce (STREAMINFO único, blocksize fijo, canales independientes,
 * sample rate/tamaño de muestra "desde STREAMINFO", subframes
 * CONSTANT/VERBATIM/FIXED sin wasted-bits, residual coding method 0). Un
 * FLAC producido por otro encoder (LPC, mid-side, blocksize variable,
 * SEEKTABLE, etc.) puede fallar aquí con una excepción explícita en vez de
 * decodificarse incorrectamente -- ese es el comportamiento deseado: este
 * decoder existe para **verificar round-trip de lo que este proyecto
 * genera**, no para reproducir FLAC arbitrario (esa es una capacidad de
 * aplicación, no de esta especificación -- ver
 * Doc/23_MONOLITHIC_DWP_FLAC_IMPLEMENTATION_SPEC.md §31, "Compatibilidad esperada").
 */
object FlacDecoder {

    data class DecodedFlac(val pcm: FlacPcmAudio, val streamInfo: FlacMetadataWriter.StreamInfo)

    private const val SYNC_CODE = 0x3FFE
    private const val MAX_REASONABLE_TOTAL_SAMPLES = 200_000_000L // ~75 min estéreo a 44.1kHz; límite de seguridad, no del formato

    fun decode(bytes: ByteArray): DecodedFlac {
        try {
            return decodeInternal(bytes)
        } catch (e: FlacDecodingException) {
            throw e
        } catch (e: Exception) {
            // Cualquier otra falla de bajo nivel (bitstream truncado, campo de
            // STREAMINFO fuera de rango tras una corrupción, índice fuera de
            // límites, etc.) se normaliza a FlacDecodingException: el
            // contrato público de esta clase es "solo lanza
            // FlacDecodingException", para que quien llama a decode() (p. ej.
            // MonolithicDwpValidator) pueda manejar un único tipo de error
            // ante cualquier entrada malformada, sin tener que conocer los
            // detalles internos de qué componente de más bajo nivel falló.
            throw FlacDecodingException("Fallo decodificando FLAC (${e.javaClass.simpleName}): ${e.message}")
        }
    }

    private fun decodeInternal(bytes: ByteArray): DecodedFlac {
        if (bytes.size < 4 || String(bytes, 0, 4, Charsets.US_ASCII) != "fLaC") {
            throw FlacDecodingException("Falta el marcador 'fLaC' al inicio del stream.")
        }
        val br = FlacBitReader(bytes, 4)

        var streamInfo: FlacMetadataWriter.StreamInfo? = null
        while (true) {
            val isLast = br.readBits(1) == 1
            val blockType = br.readBits(7)
            val blockLen = br.readBits(24)
            if (blockType == 0) {
                streamInfo = readStreamInfo(br)
            } else {
                // Bloque de metadata desconocido/no producido por este encoder: se
                // salta sin interpretarlo (no se inventa su semántica).
                br.readRawBytes(blockLen)
            }
            if (isLast) break
        }
        val info = streamInfo ?: throw FlacDecodingException("El stream no contiene ningún bloque STREAMINFO.")
        if (info.totalSamples > MAX_REASONABLE_TOTAL_SAMPLES) {
            throw FlacDecodingException(
                "STREAMINFO.total_samples=${info.totalSamples} excede el límite de seguridad " +
                    "($MAX_REASONABLE_TOTAL_SAMPLES); se rechaza antes de asignar memoria para evitar un allocation " +
                    "ilimitado a partir de un campo de longitud potencialmente corrupto (Sección 18 del prompt maestro)."
            )
        }

        val channelBuffers = Array(info.channelCount) { IntArray(info.totalSamples.toInt()) }
        var decodedSoFar = 0
        while (decodedSoFar < info.totalSamples) {
            val syncPos = br.byteOffset()
            val syncCode = br.readBits(14)
            if (syncCode != SYNC_CODE) {
                throw FlacDecodingException("Sync code de frame inválido en offset de byte $syncPos: 0x${syncCode.toString(16)}")
            }
            val reserved1 = br.readBits(1)
            if (reserved1 != 0) throw FlacDecodingException("Bit reservado de frame header != 0 en offset $syncPos")
            val blockingStrategy = br.readBits(1)
            if (blockingStrategy != 0) {
                throw FlacDecodingException("blocking strategy variable no soportada por este decoder (solo fixed-blocksize).")
            }
            val blockSizeCode = br.readBits(4)
            val sampleRateCode = br.readBits(4)
            if (sampleRateCode != 0) {
                throw FlacDecodingException("sample rate code=$sampleRateCode no soportado (este decoder solo entiende 0000 = 'desde STREAMINFO').")
            }
            val channelAssignment = br.readBits(4)
            if (channelAssignment !in 0..7) {
                throw FlacDecodingException("channel assignment=$channelAssignment no soportado (solo canales independientes 0000-0111).")
            }
            val sampleSizeCode = br.readBits(3)
            if (sampleSizeCode != 0) {
                throw FlacDecodingException("sample size code=$sampleSizeCode no soportado (solo 000 = 'desde STREAMINFO').")
            }
            val reserved2 = br.readBits(1)
            if (reserved2 != 0) throw FlacDecodingException("Segundo bit reservado de frame header != 0 en offset $syncPos")

            FlacUtf8.read(br) // frame number -- verificado implícitamente por el CRC-8, no se usa el valor

            val blockSize = when (blockSizeCode) {
                0b0110 -> br.readBits(8) + 1
                0b0111 -> br.readBits(16) + 1
                else -> throw FlacDecodingException("block size code=$blockSizeCode no soportado (este encoder solo emite 0111).")
            }
            val channelCount = channelAssignment + 1

            val headerEnd = br.byteOffset()
            val expectedCrc8 = Crc.crc8(bytes, syncPos, headerEnd)
            val actualCrc8 = br.readBits(8)
            if (actualCrc8 != expectedCrc8) {
                throw FlacDecodingException(
                    "CRC-8 de frame header no coincide en offset $syncPos (esperado=$expectedCrc8, real=$actualCrc8): frame header corrupto."
                )
            }

            val frameChannels = Array(channelCount) { decodeSubframe(br, blockSize, info.bitsPerSample) }

            br.byteAlign()
            val footerStart = br.byteOffset()
            val expectedCrc16 = Crc.crc16(bytes, syncPos, footerStart)
            val actualCrc16 = br.readBits(16)
            if (actualCrc16 != expectedCrc16) {
                throw FlacDecodingException(
                    "CRC-16 de frame no coincide en offset $syncPos (esperado=$expectedCrc16, real=$actualCrc16): frame corrupto."
                )
            }

            for (ch in 0 until channelCount) {
                System.arraycopy(frameChannels[ch], 0, channelBuffers[ch], decodedSoFar, blockSize)
            }
            decodedSoFar += blockSize
        }

        val pcm = FlacPcmAudio(channelBuffers, info.sampleRateHz, info.bitsPerSample)
        return DecodedFlac(pcm, info)
    }

    private fun readStreamInfo(br: FlacBitReader): FlacMetadataWriter.StreamInfo {
        val minBlockSize = br.readBits(16)
        val maxBlockSize = br.readBits(16)
        val minFrameSize = br.readBits(24)
        val maxFrameSize = br.readBits(24)
        val sampleRate = br.readBits(20)
        val channelCount = br.readBits(3) + 1
        val bitsPerSample = br.readBits(5) + 1
        val totalSamples = br.readBitsLong(36)
        val md5 = br.readRawBytes(16)
        return FlacMetadataWriter.StreamInfo(
            minBlockSize, maxBlockSize, minFrameSize, maxFrameSize,
            sampleRate, channelCount, bitsPerSample, totalSamples, md5
        )
    }

    private fun decodeSubframe(br: FlacBitReader, blockSize: Int, bitsPerSample: Int): IntArray {
        val zeroPad = br.readBits(1)
        if (zeroPad != 0) throw FlacDecodingException("Bit de padding de subframe != 0")
        val subframeType = br.readBits(6)
        val wastedFlag = br.readBits(1)
        if (wastedFlag != 0) {
            throw FlacDecodingException("wasted-bits-per-sample no soportado por este encoder/decoder (flag=1 inesperado).")
        }
        return when {
            subframeType == 0b000000 -> {
                val value = br.readSignedBits(bitsPerSample).toInt()
                IntArray(blockSize) { value }
            }
            subframeType == 0b000001 -> {
                IntArray(blockSize) { br.readSignedBits(bitsPerSample).toInt() }
            }
            (subframeType and 0b111000) == 0b001000 && (subframeType and 0b000111) <= FlacFixedPredictor.MAX_ORDER -> {
                val order = subframeType and 0b000111
                val warmup = IntArray(order) { br.readSignedBits(bitsPerSample).toInt() }
                val residualMethod = br.readBits(2)
                if (residualMethod != 0) {
                    throw FlacDecodingException("residual coding method=$residualMethod no soportado (solo método 0).")
                }
                val residual = FlacRiceCoder.decode(br, blockSize - order)
                FlacFixedPredictor.reconstruct(warmup, residual, order)
            }
            else -> throw FlacDecodingException(
                "Tipo de subframe 0x${subframeType.toString(16)} no soportado (LPC u otro tipo que este encoder nunca produce)."
            )
        }
    }
}
