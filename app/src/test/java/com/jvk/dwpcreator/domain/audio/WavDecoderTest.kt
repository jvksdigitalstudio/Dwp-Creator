package com.jvk.dwpcreator.domain.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavDecoderTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    /** Builds a minimal, spec-correct WAV file with the given format/data. */
    private fun buildWav(
        audioFormatTag: Int,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        pcmData: ByteArray
    ): ByteArray {
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(16)) // fmt chunk size
            write(le16(audioFormatTag))
            write(le16(channels))
            write(le32(sampleRate))
            write(le32(byteRate))
            write(le16(blockAlign))
            write(le16(bitsPerSample))
        }.toByteArray()

        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII))
            write(le32(pcmData.size))
            write(pcmData)
        }.toByteArray()

        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write(fmtChunk)
            write(dataChunk)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()
    }

    @Test
    fun `decodes 16-bit PCM stereo correctly`() {
        val pcm = ByteArray(16) { it.toByte() } // 4 stereo frames of 16-bit
        val wav = buildWav(audioFormatTag = 1, channels = 2, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(44100, decoded.sampleRateHz)
        assertEquals(2, decoded.channelCount)
        assertEquals(WavDecoder.SampleFormat.PCM_16, decoded.format)
        assertArrayEquals(pcm, decoded.pcmData)
        assertEquals(4, decoded.frameCount)
    }

    @Test
    fun `decodes 32-bit float PCM matching the real DirectWave export format`() {
        val pcm = ByteArray(32) { it.toByte() } // 4 stereo frames of 32-bit float
        val wav = buildWav(audioFormatTag = 3, channels = 2, sampleRate = 44100, bitsPerSample = 32, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.FLOAT_32, decoded.format)
        assertEquals(4, decoded.bytesPerSample)
        assertEquals(8, decoded.bytesPerFrame)
        assertEquals(4, decoded.frameCount)
    }

    @Test
    fun `decodes 24-bit PCM -- the most common real-world sample pack format`() {
        val pcm = ByteArray(12) { it.toByte() } // 2 stereo frames of 24-bit (3 bytes/sample)
        val wav = buildWav(audioFormatTag = 1, channels = 2, sampleRate = 48000, bitsPerSample = 24, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.PCM_24, decoded.format)
        assertEquals(3, decoded.bytesPerSample)
        assertEquals(6, decoded.bytesPerFrame)
        assertEquals(2, decoded.frameCount)
    }

    @Test
    fun `decodes 8-bit PCM`() {
        val pcm = ByteArray(4) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 22050, bitsPerSample = 8, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.PCM_8, decoded.format)
        assertEquals(1, decoded.bytesPerSample)
    }

    @Test
    fun `decodes 32-bit integer PCM`() {
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 96000, bitsPerSample = 32, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.PCM_32, decoded.format)
        assertEquals(4, decoded.bytesPerSample)
    }

    /**
     * Builds a WAVE_FORMAT_EXTENSIBLE fmt chunk (tag 0xFFFE): the base
     * 16-byte fields plus cbSize/validBits/channelMask/sub-format-GUID.
     * Many non-FL-Studio exporters (Adobe Audition, several plugin
     * bounces) write this regardless of bit depth, so [WavDecoder] must
     * unwrap it to the real sub-format instead of rejecting it outright.
     */
    private fun buildExtensibleWav(
        subFormatTag: Int,
        channels: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        pcmData: ByteArray
    ): ByteArray {
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate * blockAlign
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))              // WAVE_FORMAT_EXTENSIBLE
            write(le16(channels))
            write(le32(sampleRate))
            write(le32(byteRate))
            write(le16(blockAlign))
            write(le16(bitsPerSample))       // container size
            write(le16(22))                  // cbSize
            write(le16(bitsPerSample))       // valid bits per sample
            write(le32(0))                   // channel mask
            write(le16(subFormatTag))        // sub-format GUID, first 2 bytes matter
            // Real WAVE_FORMAT_EXTENSIBLE files always carry this exact
            // 14-byte suffix (the fixed part of every KSDATAFORMAT_SUBTYPE_*
            // GUID) -- WavDecoder now validates it structurally (Sección 18
            // del prompt maestro), so this fixture must be realistic, not
            // all-zero placeholder bytes.
            write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71))
        }.toByteArray()

        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(fmtBody.size))
            write(fmtBody)
        }.toByteArray()

        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII))
            write(le32(pcmData.size))
            write(pcmData)
        }.toByteArray()

        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write(fmtChunk)
            write(dataChunk)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()
    }

    @Test
    fun `unwraps WAVE_FORMAT_EXTENSIBLE to the real 24-bit PCM sub-format`() {
        val pcm = ByteArray(12) { it.toByte() }
        val wav = buildExtensibleWav(subFormatTag = 1, channels = 2, sampleRate = 48000, bitsPerSample = 24, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.PCM_24, decoded.format)
    }

    @Test
    fun `unwraps WAVE_FORMAT_EXTENSIBLE to the real float sub-format`() {
        val pcm = ByteArray(16) { it.toByte() }
        val wav = buildExtensibleWav(subFormatTag = 3, channels = 2, sampleRate = 44100, bitsPerSample = 32, pcmData = pcm)

        val decoded = WavDecoder.decode(wav)

        assertEquals(WavDecoder.SampleFormat.FLOAT_32, decoded.format)
    }

    @Test
    fun `rejects a file missing the RIFF magic`() {
        val bad = "NOTAWAV_".toByteArray(Charsets.US_ASCII) + ByteArray(20)
        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(bad)
        }
    }

    @Test
    fun `rejects an unsupported audio format tag`() {
        val wav = buildWav(audioFormatTag = 6, channels = 1, sampleRate = 8000, bitsPerSample = 8, pcmData = ByteArray(4))
        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
    }

    // --- Sección 11 del prompt maestro: WavDecoder como parser robusto, no
    // solo como conversor. Estos tests cubren validaciones que antes no
    // existían: rechazo explícito (no truncamiento silencioso) cuando
    // dataSize no es múltiplo exacto de bytesPerFrame, un chunk 'fmt '
    // demasiado pequeño para contener los campos que se leen de él, y
    // chunks que declaran un tamaño mayor de lo que realmente queda en el
    // archivo (incluyendo el caso de overflow con un tamaño cercano a
    // Int.MAX_VALUE, que antes podía desbordar la suma y colarse).

    @Test
    fun `rejects a data chunk whose size is not an exact multiple of bytesPerFrame`() {
        // 16-bit stereo => bytesPerFrame = 4. 10 bytes leaves 2 leftover,
        // an incomplete trailing frame -- must be rejected, not silently
        // truncated by integer division as frameCount used to do.
        val pcm = ByteArray(10) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 2, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("múltiplo"))
    }

    @Test
    fun `rejects a fmt chunk smaller than 16 bytes instead of reading past its end`() {
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(8)) // declares only 8 bytes -- too small to hold channels/sampleRate/bitsPerSample
            write(ByteArray(8))
            write("data".toByteArray(Charsets.US_ASCII))
            write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("fmt"))
    }

    @Test
    fun `rejects a chunk that declares more bytes than remain in the file`() {
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(16))
            write(le16(1)); write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            write("data".toByteArray(Charsets.US_ASCII))
            write(le32(999_999)) // wildly more than actually follows
            write(ByteArray(4))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()

        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
    }

    @Test
    fun `rejects a chunk size near Int-MAX without integer overflow silently passing the bounds check`() {
        // Regression test: bodyStart + size used to be computed purely in
        // Int arithmetic, which can overflow and wrap to a small/negative
        // value for a size this large, letting an obviously-impossible
        // chunk slip past the "fits in the file" check. Now computed in
        // Long, so this must always be rejected cleanly.
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(Int.MAX_VALUE - 4)) // huge declared size, would overflow bodyStart+size as Int
            write(ByteArray(16))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()

        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
    }

    @Test
    fun `rejects a chunk with a negative declared size`() {
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(le32(-1)) // high bit set -> negative when read as signed Int
            write(ByteArray(16))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(le32(riffBody.size))
            write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("negativo"))
    }

    // --- Sección 15/16/19 del prompt maestro: coherencia entre el tamaño
    // RIFF declarado (offset 4-7) y el tamaño real del archivo. Antes de
    // esta corrección, ese campo nunca se leía -- solo se usaba el tamaño
    // físico del ByteArray, ignorando por completo lo que el propio header
    // RIFF afirmaba.

    @Test
    fun `decodes correctly when the file has extra trailing bytes beyond what RIFF declares`() {
        // Sección 16: "no confundir padding de chunks RIFF con errores."
        // Trailing garbage/metadata appended by some tool, beyond the
        // RIFF-declared boundary, must be tolerated -- not treated as
        // corruption -- as long as everything RIFF actually claims is present.
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        val withTrailingGarbage = wav + byteArrayOf(1, 2, 3, 4, 5)

        val decoded = WavDecoder.decode(withTrailingGarbage)
        assertEquals(4, decoded.frameCount)
    }

    @Test
    fun `rejects a file truncated before the RIFF-declared size is fully present`() {
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        val truncated = wav.copyOfRange(0, wav.size - 3) // RIFF still claims the original (larger) size

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(truncated)
        }
        assertTrue(ex.message!!.contains("truncado"))
    }

    @Test
    fun `rejects a negative declared RIFF size`() {
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        val corrupted = wav.copyOf()
        // Overwrite the RIFF size field (offset 4) with 0xFFFFFFFF.
        corrupted[4] = 0xFF.toByte(); corrupted[5] = 0xFF.toByte(); corrupted[6] = 0xFF.toByte(); corrupted[7] = 0xFF.toByte()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(corrupted)
        }
        assertTrue(ex.message!!.contains("negativo"))
    }

    @Test
    fun `a chunk beyond the RIFF-declared boundary is rejected even if it physically fits in the array`() {
        // Regression test for effectiveEnd vs raw bytes.size: a chunk that
        // would fit within the physical ByteArray but extends past what
        // RIFF itself declares as the file's content must still be rejected.
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        // Lie about the RIFF size: claim less content than physically present,
        // so effectiveEnd falls in the middle of the real 'data' chunk.
        val lied = wav.copyOf()
        val actualRiffBodySize = wav.size - 8
        val lyingSize = actualRiffBodySize - 4 // pretend the file is 4 bytes shorter than it is
        val sizeBytes = le32(lyingSize)
        for (i in 0..3) lied[4 + i] = sizeBytes[i]

        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(lied)
        }
    }

    // --- Sección 18/19 del prompt maestro: validación estructural completa
    // de WAVE_FORMAT_EXTENSIBLE (cbSize y GUID de sub-formato), no solo sus
    // primeros 2 bytes.

    @Test
    fun `rejects a WAVE_FORMAT_EXTENSIBLE fmt chunk with an unexpected cbSize`() {
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))
            write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            write(le16(99)) // wrong cbSize (should be exactly 22)
            write(le16(16)); write(le32(0)); write(le16(1)); write(ByteArray(14))
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(fmtBody.size)); write(fmtBody)
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("cbSize"))
    }

    @Test
    fun `rejects a WAVE_FORMAT_EXTENSIBLE fmt chunk with an unrecognized subformat GUID suffix`() {
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))
            write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            write(le16(22)); write(le16(16)); write(le32(0))
            write(le16(1)) // subformat tag = PCM
            write(ByteArray(14) { 0x42 }) // NOT the real KSDATAFORMAT_SUBTYPE suffix
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(fmtBody.size)); write(fmtBody)
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("GUID"))
    }

    @Test
    fun `rejects a WAVE_FORMAT_EXTENSIBLE fmt chunk too small to even contain cbSize`() {
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))
            write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            // Nothing more -- only 16 bytes total, cbSize would need bytes 16-17.
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(fmtBody.size)); write(fmtBody)
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
    }

    // --- Sección 4/9 del prompt maestro Pass 3: padding RIFF de chunks de
    // tamaño impar debe validarse explícitamente contra el límite RIFF.

    @Test
    fun `accepts an odd-sized data chunk when its required padding byte is physically present`() {
        // 8-bit mono => bytesPerFrame = 1, so an odd dataSize (5) is still a
        // whole number of frames -- the only question is whether the RIFF
        // padding byte after the odd-sized chunk is correctly present and
        // counted within the declared RIFF size.
        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(16))
            write(le16(1)); write(le16(1)); write(le32(8000)); write(le32(8000)); write(le16(1)); write(le16(8))
        }.toByteArray()
        val pcm = byteArrayOf(1, 2, 3, 4, 5) // odd size: 5 bytes
        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(pcm.size)); write(pcm)
            write(0) // the required RIFF padding byte for an odd-sized chunk
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII)); write(fmtChunk); write(dataChunk)
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val decoded = WavDecoder.decode(wav)
        assertEquals(5, decoded.frameCount)
        assertArrayEquals(pcm, decoded.pcmData)
    }

    @Test
    fun `rejects an odd-sized data chunk whose required padding byte is missing`() {
        // Same shape as the accept-case above, but WITHOUT the padding byte
        // and without extending the declared RIFF size to cover it --
        // exactly the case Sección 4 describes: the odd chunk's payload
        // ends precisely at the RIFF boundary, with no room for its
        // required pad byte.
        val pcm = byteArrayOf(1, 2, 3, 4, 5) // odd size: 5 bytes
        val wav = buildWav(audioFormatTag = 1, channels = 1, sampleRate = 8000, bitsPerSample = 8, pcmData = pcm)
        // buildWav deliberately does NOT add a RIFF padding byte -- exactly
        // the malformed shape this test needs.
        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("padding"))
    }

    // --- Sección 5/6/9: blockAlign y byteRate deben coincidir con lo
    // matemáticamente esperado a partir de channels/bitsPerSample/sampleRate.

    @Test
    fun `rejects a blockAlign inconsistent with channels and bitsPerSample`() {
        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(16))
            write(le16(1))            // PCM
            write(le16(2))            // 2 channels
            write(le32(44100))
            write(le32(44100 * 4))    // byteRate consistent with the CORRECT blockAlign (4), not the wrong one below
            write(le16(1))            // WRONG blockAlign: should be 4 (2ch x 2 bytes for 16-bit), declares 1
            write(le16(16))           // 16 bits per sample
        }.toByteArray()
        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(4)); write(ByteArray(4))
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII)); write(fmtChunk); write(dataChunk)
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("blockAlign"))
    }

    @Test
    fun `rejects a byteRate inconsistent with sampleRate and blockAlign`() {
        val fmtChunk = ByteArrayOutputStream().apply {
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(16))
            write(le16(1))            // PCM
            write(le16(2))            // 2 channels
            write(le32(44100))
            write(le32(1))            // WRONG byteRate: should be 44100*4=176400
            write(le16(4))            // correct blockAlign
            write(le16(16))
        }.toByteArray()
        val dataChunk = ByteArrayOutputStream().apply {
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(4)); write(ByteArray(4))
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII)); write(fmtChunk); write(dataChunk)
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("byteRate"))
    }

    @Test
    fun `accepts a valid blockAlign and byteRate (boundary check, no false positive)`() {
        // Sanity companion to the two rejection tests above: correct values
        // must not be rejected.
        val pcm = ByteArray(8) { it.toByte() }
        val wav = buildWav(audioFormatTag = 1, channels = 2, sampleRate = 44100, bitsPerSample = 16, pcmData = pcm)
        val decoded = WavDecoder.decode(wav)
        assertEquals(2, decoded.frameCount)
    }

    // --- Sección 8/9: validBitsPerSample debe estar en [1, bitsPerSample].

    @Test
    fun `rejects WAVE_FORMAT_EXTENSIBLE with validBitsPerSample greater than bitsPerSample`() {
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))
            write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            write(le16(22))  // cbSize
            write(le16(24))  // validBitsPerSample = 24 > bitsPerSample (16) -- invalid
            write(le32(0))   // channel mask
            write(le16(1))   // subformat tag = PCM
            write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71))
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(fmtBody.size)); write(fmtBody)
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        val ex = assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
        assertTrue(ex.message!!.contains("validBitsPerSample"))
    }

    @Test
    fun `rejects WAVE_FORMAT_EXTENSIBLE with validBitsPerSample of zero`() {
        val fmtBody = ByteArrayOutputStream().apply {
            write(le16(0xFFFE))
            write(le16(2)); write(le32(44100)); write(le32(176400)); write(le16(4)); write(le16(16))
            write(le16(22))  // cbSize
            write(le16(0))   // validBitsPerSample = 0 -- invalid, must be >= 1
            write(le32(0))
            write(le16(1))
            write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0x80.toByte(), 0x00, 0x00, 0xAA.toByte(), 0x00, 0x38, 0x9B.toByte(), 0x71))
        }.toByteArray()
        val riffBody = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII)); write(le32(fmtBody.size)); write(fmtBody)
            write("data".toByteArray(Charsets.US_ASCII)); write(le32(0))
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII)); write(le32(riffBody.size)); write(riffBody)
        }.toByteArray()

        assertThrows(WavDecoder.WavFormatException::class.java) {
            WavDecoder.decode(wav)
        }
    }
}
