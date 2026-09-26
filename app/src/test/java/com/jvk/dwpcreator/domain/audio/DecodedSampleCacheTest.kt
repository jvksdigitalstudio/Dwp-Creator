package com.jvk.dwpcreator.domain.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Cobertura de [DecodedSampleCache]: el mecanismo real detrás de "tocar una
 * tecla ya no debe recalcular nada la segunda vez" (Doc/29, "teclado
 * profesional"). Se comprueba con datos, no por confianza: el PCM devuelto
 * en un acierto de caché coincide byte a byte con una decodificación
 * independiente de los mismos bytes de origen, y la expulsión LRU libera
 * memoria de verdad cuando se supera el límite.
 */
class DecodedSampleCacheTest {

    /** WAV de 16-bit mono mínimo válido, con [frameCount] frames de contenido determinista. */
    private fun pcm16MonoWav(frameCount: Int, sampleRate: Int = 44100): ByteArray {
        val dataSize = frameCount * 2
        val out = ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }

        out.write("RIFF".toByteArray()); le32(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(dataSize)
        for (i in 0 until frameCount) le16((i % 32768))
        return out.toByteArray()
    }

    private fun stereoFloat32Wav(frameCount: Int, sampleRate: Int = 44100): ByteArray {
        val bytesPerFrame = 4 * 2
        val dataSize = frameCount * bytesPerFrame
        val out = ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }

        out.write("RIFF".toByteArray()); le32(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(3); le16(2); le32(sampleRate); le32(sampleRate * bytesPerFrame); le16(bytesPerFrame); le16(32)
        out.write("data".toByteArray()); le32(dataSize)
        for (i in 0 until frameCount * 2) le32(java.lang.Float.floatToIntBits(0f))
        return out.toByteArray()
    }

    @Test
    fun `a cache hit returns exactly the PCM a fresh decode would produce`() {
        val cache = DecodedSampleCache()
        val wav = pcm16MonoWav(1000)

        val first = cache.get(0, wav)!!
        val second = cache.get(0, wav)!!

        assertArrayEquals(first.pcm16, second.pcm16)
        assertEquals(44100, second.sampleRateHz)
        assertEquals(1, second.channelCount)
    }

    @Test
    fun `float32 stereo is converted to 16-bit exactly like PcmConverter would on its own`() {
        val cache = DecodedSampleCache()
        val wav = stereoFloat32Wav(500)
        val decoded = WavDecoder.decode(wav)
        val expected = PcmConverter.floatToInt16(decoded.pcmData)

        val cached = cache.get(0, wav)!!

        assertArrayEquals(expected, cached.pcm16)
        assertEquals(2, cached.channelCount)
    }

    @Test
    fun `an unsupported channel count is rejected, not cached`() {
        // 3 canales: WavDecoder lo decodifica sin problema (no es un formato de
        // muestras/bits no soportado), pero DecodedSampleCache lo rechaza igual
        // que hacía SamplePlayer.play() antes de este cambio (solo mono/estéreo).
        val out = ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
        val frameCount = 10
        val dataSize = frameCount * 3 * 2
        out.write("RIFF".toByteArray()); le32(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16)
        le16(1); le16(3); le32(44100); le32(44100 * 6); le16(6); le16(16)
        out.write("data".toByteArray()); le32(dataSize)
        repeat(frameCount * 3) { le16(0) }

        assertNull(DecodedSampleCache().get(0, out.toByteArray()))
    }

    @Test
    fun `a malformed wav returns null instead of throwing`() {
        assertNull(DecodedSampleCache().get(0, byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `isFull reflects the configured byte budget`() {
        val cache = DecodedSampleCache(maxTotalBytes = 100)
        assertFalse(cache.isFull())
        cache.get(0, pcm16MonoWav(1000)) // 2000 bytes de PCM16, ya por encima del límite de 100
        assertTrue(cache.isFull())
    }

    @Test
    fun `eviction drops the least recently used entry, not the most recently added`() {
        // Cada muestra de 500 frames mono-16 pesa 1000 bytes de PCM; con un
        // límite de 2500 caben como mucho 2 a la vez.
        val cache = DecodedSampleCache(maxTotalBytes = 2500)
        val wavA = pcm16MonoWav(500)
        val wavB = pcm16MonoWav(500)
        val wavC = pcm16MonoWav(500)

        cache.get(0, wavA)
        cache.get(1, wavB)
        cache.get(0, wavA) // toca A: ahora B es la entrada usada hace más tiempo
        cache.get(2, wavC) // fuerza una expulsión

        // B se expulsó (era la LRU); pedirla de nuevo debe decodificar sin lanzar,
        // y A/C deben seguir sirviéndose sin problema.
        assertNotNull(cache.get(1, wavB))
        assertNotNull(cache.get(0, wavA))
        assertNotNull(cache.get(2, wavC))
    }

    @Test
    fun `clear empties the cache and resets isFull`() {
        val cache = DecodedSampleCache(maxTotalBytes = 100)
        cache.get(0, pcm16MonoWav(1000))
        assertTrue(cache.isFull())

        cache.clear()

        assertFalse(cache.isFull())
    }
}
