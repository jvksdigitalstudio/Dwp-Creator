package com.jvk.dwpcreator.domain.audio

/**
 * Minimal, dependency-free WAV (RIFF) parser.
 *
 * Originally supported only 16-bit integer PCM and 32-bit IEEE-float PCM --
 * the two formats seen in the one reference file (FL Studio Desktop's own
 * DirectWave export) used during the original binary audit. That was too
 * narrow: real-world sample packs commonly ship as 24-bit PCM, and many
 * exporters (Adobe Audition, several plugin bounces, etc.) write
 * WAVE_FORMAT_EXTENSIBLE (`fmt` tag `0xFFFE`) regardless of bit depth. Both
 * of those made [decode] throw immediately -- which, upstream, made a whole
 * export produce *zero* output (the exception fires on the very first
 * sample, before a single byte is written to the zip) and made preview
 * playback fail completely silently (`SamplePlayer.play` swallows the
 * exception). This now covers the WAV variants actually seen in the wild:
 * 8/16/24/32-bit integer PCM, 32-bit float, and WAVE_FORMAT_EXTENSIBLE
 * wrapping any of those.
 *
 * Pure Kotlin -- no Android dependency, fully unit-testable on the JVM.
 */
object WavDecoder {

    class WavFormatException(message: String) : Exception(message)

    enum class SampleFormat { PCM_8, PCM_16, PCM_24, PCM_32, FLOAT_32 }

    private const val FORMAT_PCM = 1
    private const val FORMAT_IEEE_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    /**
     * The fixed 14-byte suffix shared by every well-known
     * `KSDATAFORMAT_SUBTYPE_*` GUID (only the first 2 bytes vary, carrying
     * the actual format tag). Used to validate a WAVE_FORMAT_EXTENSIBLE
     * subformat GUID structurally (Sección 18 del prompt maestro) instead
     * of blindly trusting its first 2 bytes.
     */
    private val KSDATAFORMAT_SUBTYPE_SUFFIX = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00,
        0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71
    )

    data class DecodedWav(
        val sampleRateHz: Int,
        val channelCount: Int,
        val format: SampleFormat,
        /** Raw PCM bytes exactly as stored in the file's `data` chunk (little-endian). */
        val pcmData: ByteArray
    ) {
        val bytesPerSample: Int
            get() = when (format) {
                SampleFormat.PCM_8 -> 1
                SampleFormat.PCM_16 -> 2
                SampleFormat.PCM_24 -> 3
                SampleFormat.PCM_32, SampleFormat.FLOAT_32 -> 4
            }
        val bytesPerFrame: Int get() = bytesPerSample * channelCount
        val frameCount: Int get() = pcmData.size / bytesPerFrame
    }

    fun decode(bytes: ByteArray): DecodedWav {
        require4CC(bytes, 0, "RIFF")
        if (12 > bytes.size) {
            throw WavFormatException("Archivo demasiado pequeño para contener el header RIFF completo (${bytes.size} bytes).")
        }
        val riffSize = readLE32(bytes, 4)
        if (riffSize < 0) {
            throw WavFormatException("El tamaño RIFF declarado (offset 4) es negativo/inválido tras leerlo como entero de 32 bits.")
        }
        // Sección 15/16 del prompt maestro: el tamaño RIFF cuenta todo lo que
        // sigue al propio campo de tamaño ("WAVE" + todos los chunks), es
        // decir riffEnd = 8 + riffSize. Un archivo cuyo tamaño real es MENOR
        // que esto está truncado -- se rechaza. Un archivo cuyo tamaño real
        // es MAYOR (bytes extra al final, padding, metadata añadida por
        // alguna herramienta) es tolerado explícitamente: no se confunde
        // padding con corrupción. A partir de aquí, `effectiveEnd` -- no
        // `bytes.size` -- es el límite autoritativo para todo el parsing:
        // ignora cualquier basura más allá de lo que RIFF declara.
        val riffEndLong = 8L + riffSize.toLong()
        if (riffEndLong > bytes.size.toLong()) {
            throw WavFormatException(
                "Archivo WAV truncado: el header RIFF declara $riffSize bytes de contenido " +
                    "(se esperaban al menos $riffEndLong bytes en total), pero el archivo real mide ${bytes.size} bytes."
            )
        }
        val effectiveEnd = riffEndLong.toInt() // safe: riffEndLong <= bytes.size.toLong() <= Int.MAX_VALUE
        require4CC(bytes, 8, "WAVE")

        var cursor = 12
        var sampleRate = -1
        var channels = -1
        var bitsPerSample = -1
        var audioFormatTag = -1
        var byteRate = -1
        var blockAlign = -1
        var fmtChunkSize = -1
        var fmtBodyStart = -1
        var dataOffset = -1
        var dataSize = -1

        while (cursor + 8 <= effectiveEnd) {
            val id = String(bytes, cursor, 4, Charsets.US_ASCII)
            val size = readLE32(bytes, cursor + 4)
            val bodyStart = cursor + 8

            if (size < 0) {
                throw WavFormatException("Chunk '$id' en offset $cursor declara un tamaño negativo/inválido.")
            }
            // Long arithmetic guards against a huge `size` overflowing the
            // Int bound check below and wrapping into a small/negative
            // value, which would otherwise let an out-of-bounds chunk read
            // slip through as if it fit. Bounded against effectiveEnd (the
            // RIFF-declared boundary), not the raw array size.
            if (bodyStart.toLong() + size.toLong() > effectiveEnd.toLong()) {
                throw WavFormatException("Chunk '$id' en offset $cursor declara más bytes ($size) de los que caben dentro del tamaño RIFF declarado.")
            }
            // Sección 4 del prompt maestro Pass 3: RIFF word-alignment means
            // an odd-sized chunk requires one physical padding byte before
            // the next chunk can start. That padding byte must ALSO fit
            // within the RIFF-declared boundary -- checked separately from
            // the payload-fits check above, because a payload that ends
            // exactly AT effectiveEnd would pass that check while still
            // lacking room for its required padding byte, silently
            // accepting a file that doesn't actually conform to RIFF.
            val needsPadding = (size and 1) == 1
            if (needsPadding && bodyStart.toLong() + size.toLong() + 1L > effectiveEnd.toLong()) {
                throw WavFormatException(
                    "Chunk '$id' en offset $cursor tiene tamaño impar ($size bytes) y requiere 1 byte de " +
                        "padding, pero ese byte queda fuera del límite RIFF declarado; se rechaza."
                )
            }

            when (id) {
                "fmt " -> {
                    if (size < 16) {
                        throw WavFormatException("Chunk 'fmt ' demasiado pequeño ($size bytes, se requieren al menos 16).")
                    }
                    audioFormatTag = readLE16(bytes, bodyStart)
                    channels = readLE16(bytes, bodyStart + 2)
                    sampleRate = readLE32(bytes, bodyStart + 4)
                    byteRate = readLE32(bytes, bodyStart + 8)
                    blockAlign = readLE16(bytes, bodyStart + 12)
                    bitsPerSample = readLE16(bytes, bodyStart + 14)
                    fmtChunkSize = size
                    fmtBodyStart = bodyStart
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
        if (dataOffset + dataSize.toLong() > effectiveEnd.toLong()) {
            throw WavFormatException("Archivo WAV truncado: el chunk 'data' declara más bytes de los que caben dentro del tamaño RIFF declarado.")
        }
        // Sección 7 del prompt maestro Pass 3: rangos básicos de coherencia,
        // más allá de lo ya implícito en la búsqueda del chunk 'fmt '.
        if (blockAlign <= 0) {
            throw WavFormatException("blockAlign declarado ($blockAlign) debe ser positivo.")
        }
        if (byteRate <= 0) {
            throw WavFormatException("byteRate declarado ($byteRate) debe ser positivo.")
        }

        // WAVE_FORMAT_EXTENSIBLE doesn't carry the real format in the tag --
        // it's inside a 22-byte extension block (cbSize(2) + validBits(2) +
        // channelMask(4) + subFormatGuid(16)) that follows the base 16-byte
        // fmt body. Structurally validate this extension (Sección 18 del
        // prompt maestro) before trusting any of it -- don't just assume
        // it's shaped correctly because the tag says 0xFFFE:
        //  - cbSize must be readable and must equal exactly 22 (the fixed
        //    size of this extension per the WAVEFORMATEXTENSIBLE struct);
        //  - the fmt chunk must actually contain all 22 declared bytes;
        //  - the subformat GUID's trailing 14 bytes must match the
        //    well-known KSDATAFORMAT_SUBTYPE constant suffix, so an
        //    unrelated GUID that happens to start with the right 2 bytes
        //    isn't silently misread as PCM/float.
        // validBitsPerSample and channelMask (fmtBodyStart+18..+24) are
        // structurally covered by the checks above but intentionally left
        // undecoded -- DwpCreator doesn't use them for anything today.
        var effectiveTag = audioFormatTag
        if (audioFormatTag == FORMAT_EXTENSIBLE) {
            if (fmtChunkSize < 18) {
                throw WavFormatException(
                    "Chunk 'fmt ' declara WAVE_FORMAT_EXTENSIBLE pero mide solo $fmtChunkSize bytes " +
                        "(se requieren al menos 18 para leer siquiera cbSize)."
                )
            }
            val cbSize = readLE16(bytes, fmtBodyStart + 16)
            if (cbSize != 22) {
                throw WavFormatException("WAVE_FORMAT_EXTENSIBLE con cbSize=$cbSize; se esperaba exactamente 22.")
            }
            if (fmtChunkSize < 18 + cbSize) {
                throw WavFormatException(
                    "Chunk 'fmt ' declara WAVE_FORMAT_EXTENSIBLE con cbSize=22 pero mide solo $fmtChunkSize bytes " +
                        "(se requieren al menos ${18 + cbSize})."
                )
            }
            // Sección 8 del prompt maestro Pass 3: validBitsPerSample (los 2
            // bytes justo después de cbSize) debe estar en el rango
            // [1, bitsPerSample] -- representa cuántos de los bitsPerSample
            // contenedores realmente llevan audio útil (ej. 20 bits válidos
            // dentro de un contenedor de 24). No se interpreta más allá de
            // validar el rango; DwpCreator no usa este valor para nada hoy.
            val validBitsPerSample = readLE16(bytes, fmtBodyStart + 18)
            if (validBitsPerSample < 1 || validBitsPerSample > bitsPerSample) {
                throw WavFormatException(
                    "WAVE_FORMAT_EXTENSIBLE con validBitsPerSample=$validBitsPerSample fuera de rango " +
                        "(debe cumplir 1 <= validBitsPerSample <= bitsPerSample=$bitsPerSample)."
                )
            }
            // channelMask (4 bytes en fmtBodyStart+20) no se interpreta ni
            // se reinterpreta (Sección 8: "no debe inventarse") -- su
            // presencia ya está estructuralmente validada por los chequeos
            // de cbSize/fmtChunkSize anteriores, que garantizan que estos 4
            // bytes existen dentro del chunk.
            // Safe without an extra bounds check: the per-chunk overflow-safe
            // validation above already proved fmtBodyStart + fmtChunkSize <=
            // effectiveEnd, and fmtChunkSize >= 40 here, so fmtBodyStart+40
            // (== subFormatOffset+16, the end of the GUID) is within bounds.
            val subFormatOffset = fmtBodyStart + 24 // 16 base + cbSize(2) + validBits(2) + channelMask(4)
            effectiveTag = readLE16(bytes, subFormatOffset)
            val guidSuffix = bytes.copyOfRange(subFormatOffset + 2, subFormatOffset + 16)
            if (!guidSuffix.contentEquals(KSDATAFORMAT_SUBTYPE_SUFFIX)) {
                throw WavFormatException(
                    "WAVE_FORMAT_EXTENSIBLE con un GUID de sub-formato no reconocido " +
                        "(sufijo: ${guidSuffix.joinToString("") { "%02x".format(it) }}); " +
                        "no coincide con el sufijo estándar KSDATAFORMAT_SUBTYPE."
                )
            }
        }

        val format = when {
            effectiveTag == FORMAT_PCM && bitsPerSample == 8 -> SampleFormat.PCM_8
            effectiveTag == FORMAT_PCM && bitsPerSample == 16 -> SampleFormat.PCM_16
            effectiveTag == FORMAT_PCM && bitsPerSample == 24 -> SampleFormat.PCM_24
            effectiveTag == FORMAT_PCM && bitsPerSample == 32 -> SampleFormat.PCM_32
            effectiveTag == FORMAT_IEEE_FLOAT && bitsPerSample == 32 -> SampleFormat.FLOAT_32
            else -> throw WavFormatException(
                "Formato de audio no soportado (tag=$audioFormatTag" +
                    (if (audioFormatTag == FORMAT_EXTENSIBLE) " → sub-formato=$effectiveTag" else "") +
                    ", bits=$bitsPerSample). Formatos soportados: PCM entero de 8/16/24/32 bits, " +
                    "o PCM float de 32 bits (directo o vía WAVE_FORMAT_EXTENSIBLE)."
            )
        }

        val bytesPerSample = when (format) {
            SampleFormat.PCM_8 -> 1
            SampleFormat.PCM_16 -> 2
            SampleFormat.PCM_24 -> 3
            SampleFormat.PCM_32, SampleFormat.FLOAT_32 -> 4
        }
        val bytesPerFrame = bytesPerSample * channels
        // Sección 5/6 del prompt maestro Pass 3: blockAlign y byteRate son
        // campos redundantes por diseño (se derivan de channels/bitsPerSample
        // y sampleRate/blockAlign respectivamente) -- un WAV bien formado
        // SIEMPRE los declara consistentes con el resto del header. Un valor
        // inconsistente es una señal fuerte de archivo corrupto/malformado o
        // de una interpretación incorrecta de bitsPerSample/canales.
        if (blockAlign != bytesPerFrame) {
            throw WavFormatException(
                "blockAlign declarado ($blockAlign) no coincide con el esperado " +
                    "($bytesPerFrame = $channels canal(es) × $bytesPerSample bytes/muestra)."
            )
        }
        // Long arithmetic throughout (Sección 6): sampleRate * blockAlign
        // could exceed Int range for extreme (if valid) sample rates: never
        // converted back to Int, only compared as Long against the
        // already-Int byteRate widened to Long.
        val expectedByteRate = sampleRate.toLong() * blockAlign.toLong()
        if (byteRate.toLong() != expectedByteRate) {
            throw WavFormatException(
                "byteRate declarado ($byteRate) no coincide con el esperado " +
                    "($expectedByteRate = sampleRate($sampleRate) × blockAlign($blockAlign))."
            )
        }
        if (dataSize % bytesPerFrame != 0) {
            throw WavFormatException(
                "Chunk 'data' de $dataSize bytes no es múltiplo exacto de $bytesPerFrame bytes/frame " +
                    "($bytesPerSample bytes/muestra × $channels canal(es)): quedarían " +
                    "${dataSize % bytesPerFrame} byte(s) sueltos de un frame incompleto. Se rechaza en vez de truncar en silencio."
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
