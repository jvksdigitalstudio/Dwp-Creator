package com.jvk.dwpcreator.domain.audio

/**
 * Minimal, dependency-free WAV (RIFF) parser. Supports exactly the two
 * formats DirectWave/FL Studio Desktop export: 16-bit integer PCM and
 * 32-bit IEEE-float PCM (verified against the real Instrument.wav samples:
 * 44.1kHz, stereo, 32-bit float). Throws a clear error for anything else
 * rather than silently producing garbage audio.
 *
 * Pure Kotlin -- no Android dependency, fully unit-testable on the JVM.
 */
object WavDecoder {

    class WavFormatException(message: String) : Exception(message)

    enum class SampleFormat { PCM_16, PCM_FLOAT }

    data class DecodedWav(
        val sampleRateHz: Int,
        val channelCount: Int,
        val format: SampleFormat,
        /** Raw PCM bytes exactly as stored in the file's `data` chunk (little-endian). */
        val pcmData: ByteArray
    ) {
        val bytesPerSample: Int get() = if (format == SampleFormat.PCM_16) 2 else 4
        val bytesPerFrame: Int get() = bytesPerSample * channelCount
        val frameCount: Int get() = pcmData.size / bytesPerFrame
    }

    fun decode(bytes: ByteArray): DecodedWav {
        require4CC(bytes, 0, "RIFF")
        require4CC(bytes, 8, "WAVE")

        var cursor = 12
        var sampleRate = -1
        var channels = -1
        var bitsPerSample = -1
        var audioFormatTag = -1
        var dataOffset = -1
        var dataSize = -1

        while (cursor + 8 <= bytes.size) {
            val id = String(bytes, cursor, 4, Charsets.US_ASCII)
            val size = readLE32(bytes, cursor + 4)
            val bodyStart = cursor + 8

            when (id) {
                "fmt " -> {
                    audioFormatTag = readLE16(bytes, bodyStart)
                    channels = readLE16(bytes, bodyStart + 2)
                    sampleRate = readLE32(bytes, bodyStart + 4)
                    bitsPerSample = readLE16(bytes, bodyStart + 14)
                }
                "data" -> {
                    dataOffset = bodyStart
                    dataSize = size
                }
            }
            // RIFF chunks are word-aligned: odd-sized chunks have one pad byte.
            cursor = bodyStart + size + (size and 1)
        }

        if (sampleRate <= 0 || channels <= 0 || dataOffset < 0 || dataSize < 0) {
            throw WavFormatException("Archivo WAV inválido o incompleto: falta el chunk 'fmt ' o 'data'.")
        }
        if (dataOffset + dataSize > bytes.size) {
            throw WavFormatException("Archivo WAV truncado: el chunk 'data' declara más bytes de los que existen.")
        }

        val format = when {
            audioFormatTag == 1 && bitsPerSample == 16 -> SampleFormat.PCM_16
            audioFormatTag == 3 && bitsPerSample == 32 -> SampleFormat.PCM_FLOAT
            else -> throw WavFormatException(
                "Formato de audio no soportado (tag=$audioFormatTag, bits=$bitsPerSample). " +
                    "Se esperaba PCM entero de 16 bits o PCM float de 32 bits."
            )
        }

        return DecodedWav(
            sampleRateHz = sampleRate,
            channelCount = channels,
            format = format,
            pcmData = bytes.copyOfRange(dataOffset, dataOffset + dataSize)
        )
    }

    private fun require4CC(bytes: ByteArray, offset: Int, expected: String) {
        if (offset + 4 > bytes.size) {
            throw WavFormatException("Archivo demasiado pequeño para ser un WAV válido.")
        }
        val actual = String(bytes, offset, 4, Charsets.US_ASCII)
        if (actual != expected) {
            throw WavFormatException("Encabezado WAV inválido: se esperaba '$expected' en offset $offset, se encontró '$actual'.")
        }
    }

    private fun readLE16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun readLE32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
