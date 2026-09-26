package com.jvk.dwpcreator.domain.dwp.monolithic

import com.jvk.dwpcreator.domain.audio.WavDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PcmNormalizerTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le24(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte())

    @Test
    fun `PCM_8 converts unsigned 0-255 to signed -128 to 127 losslessly`() {
        val pcm = byteArrayOf(0, 128.toByte(), 255.toByte(), 1, 254.toByte())
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.PCM_8, pcm)
        val normalized = PcmNormalizer.normalize(wav)
        assertArrayEquals(intArrayOf(-128, 0, 127, -127, 126), normalized.channels[0])
        assertEquals(8, normalized.bitsPerSample)
    }

    @Test
    fun `PCM_16 deinterleaves stereo correctly`() {
        val samples = listOf(1000, -1000, 32767, -32768, 0, 5)
        val bytes = samples.flatMap { le16(it).toList() }.toByteArray()
        val wav = WavDecoder.DecodedWav(48000, 2, WavDecoder.SampleFormat.PCM_16, bytes)
        val normalized = PcmNormalizer.normalize(wav)
        assertEquals(2, normalized.channelCount)
        assertArrayEquals(intArrayOf(1000, 32767, 0), normalized.channels[0])
        assertArrayEquals(intArrayOf(-1000, -32768, 5), normalized.channels[1])
    }

    @Test
    fun `PCM_24 sign-extends correctly for negative values`() {
        val samples = listOf(0, -1, 8388607, -8388608, 123456)
        val bytes = samples.flatMap { le24(it).toList() }.toByteArray()
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.PCM_24, bytes)
        val normalized = PcmNormalizer.normalize(wav)
        assertArrayEquals(intArrayOf(0, -1, 8388607, -8388608, 123456), normalized.channels[0])
        assertEquals(24, normalized.bitsPerSample)
    }

    @Test
    fun `PCM_32 integer is explicitly rejected as unsupported`() {
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.PCM_32, ByteArray(8))
        assertThrows(UnsupportedMonolithicFormatException::class.java) {
            PcmNormalizer.normalize(wav)
        }
    }

    @Test
    fun `FLOAT_32 is explicitly rejected as unsupported`() {
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.FLOAT_32, ByteArray(8))
        assertThrows(UnsupportedMonolithicFormatException::class.java) {
            PcmNormalizer.normalize(wav)
        }
    }

    @Test
    fun `normalized output feeds directly into a valid FlacPcmAudio`() {
        val samples = listOf(100, -100, 200, -200)
        val bytes = samples.flatMap { le16(it).toList() }.toByteArray()
        val wav = WavDecoder.DecodedWav(44100, 1, WavDecoder.SampleFormat.PCM_16, bytes)
        val normalized = PcmNormalizer.normalize(wav)
        assertEquals(4, normalized.frameCount)
        assertEquals(44100, normalized.sampleRateHz)
    }
}
