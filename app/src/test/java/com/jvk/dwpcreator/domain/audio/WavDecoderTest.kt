package com.jvk.dwpcreator.domain.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

        assertEquals(WavDecoder.SampleFormat.PCM_FLOAT, decoded.format)
        assertEquals(4, decoded.bytesPerSample)
        assertEquals(8, decoded.bytesPerFrame)
        assertEquals(4, decoded.frameCount)
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
}
