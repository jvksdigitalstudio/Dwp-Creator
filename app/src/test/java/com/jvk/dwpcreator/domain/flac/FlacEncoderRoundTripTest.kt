package com.jvk.dwpcreator.domain.flac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import kotlin.random.Random

class FlacEncoderRoundTripTest {

    private fun assertRoundTrip(pcm: FlacPcmAudio, blockSize: Int = FlacEncoder.DEFAULT_BLOCK_SIZE) {
        val flac = FlacEncoder.encode(pcm, blockSize)
        assertEquals("fLaC", String(flac, 0, 4, Charsets.US_ASCII))
        val decoded = FlacDecoder.decode(flac)
        assertEquals(pcm, decoded.pcm)
        assertEquals(pcm.frameCount.toLong(), decoded.streamInfo.totalSamples)
        assertEquals(pcm.channelCount, decoded.streamInfo.channelCount)
        assertEquals(pcm.bitsPerSample, decoded.streamInfo.bitsPerSample)
        assertEquals(pcm.sampleRateHz, decoded.streamInfo.sampleRateHz)
    }

    @Test
    fun `round trips silence mono 16-bit`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(IntArray(1000)), 44100, 16))
    }

    @Test
    fun `round trips constant nonzero stereo 16-bit`() {
        val left = IntArray(500) { 123 }
        val right = IntArray(500) { -456 }
        assertRoundTrip(FlacPcmAudio(arrayOf(left, right), 44100, 16))
    }

    @Test
    fun `round trips a linear ramp mono 16-bit`() {
        val samples = IntArray(3000) { (it % 1000) - 500 }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16))
    }

    @Test
    fun `round trips random mono 16-bit across multiple frames (small block size)`() {
        val rnd = Random(42)
        val samples = IntArray(5000) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 256)
    }

    @Test
    fun `round trips random stereo 16-bit`() {
        val rnd = Random(43)
        val left = IntArray(4321) { rnd.nextInt(-32768, 32768) }
        val right = IntArray(4321) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(left, right), 44100, 16), blockSize = 1024)
    }

    @Test
    fun `round trips random 8-bit mono`() {
        val rnd = Random(44)
        val samples = IntArray(2000) { rnd.nextInt(-128, 128) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 22050, 8), blockSize = 512)
    }

    @Test
    fun `round trips random 24-bit mono`() {
        val rnd = Random(45)
        val samples = IntArray(1500) { rnd.nextInt(-8388608, 8388608) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 48000, 24), blockSize = 300)
    }

    @Test
    fun `round trips a single-sample block`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(12345)), 44100, 16))
    }

    @Test
    fun `round trips alternating 16-bit extremes (stresses Rice escape path)`() {
        val samples = IntArray(2000) { if (it % 2 == 0) 32767 else -32768 }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 64)
    }

    @Test
    fun `round trips a stream length that is not an exact multiple of block size`() {
        val samples = IntArray(1017) { it - 500 }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 100)
    }

    @Test
    fun `round trips eight independent channels`() {
        val rnd = Random(46)
        val channels = Array(8) { IntArray(200) { rnd.nextInt(-32768, 32768) } }
        assertRoundTrip(FlacPcmAudio(channels, 44100, 16), blockSize = 64)
    }

    @Test
    fun `encoding zero-length audio throws FlacEncodingException`() {
        assertThrows(FlacEncodingException::class.java) {
            FlacEncoder.encode(FlacPcmAudio(arrayOf(IntArray(0)), 44100, 16))
        }
    }

    @Test
    fun `STREAMINFO md5 matches independently computed md5 of the reconstructed interleaved PCM`() {
        val rnd = Random(47)
        val samples = IntArray(1000) { rnd.nextInt(-32768, 32768) }
        val pcm = FlacPcmAudio(arrayOf(samples), 44100, 16)
        val flac = FlacEncoder.encode(pcm)
        val decoded = FlacDecoder.decode(flac)

        val md = MessageDigest.getInstance("MD5")
        for (s in samples) {
            md.update(byteArrayOf((s and 0xFF).toByte(), ((s shr 8) and 0xFF).toByte()))
        }
        assertArrayEquals(md.digest(), decoded.streamInfo.md5)
    }

    @Test
    fun `decoding a corrupted frame header CRC-8 fails loudly`() {
        val pcm = FlacPcmAudio(arrayOf(IntArray(2000) { it }), 44100, 16)
        val flac = FlacEncoder.encode(pcm, blockSize = 256).copyOf()
        // El primer frame empieza justo después de "fLaC" (4) + metadata block (4 header + 34 body = 38) = offset 42.
        // Corrompemos un byte bien dentro del primer frame header (después del sync code, offset+2) para que el CRC-8 no cuadre.
        val corruptOffset = 42 + 3
        flac[corruptOffset] = (flac[corruptOffset].toInt() xor 0xFF).toByte()
        assertThrows(FlacDecodingException::class.java) {
            FlacDecoder.decode(flac)
        }
    }

    @Test
    fun `decoding a stream missing the fLaC magic fails loudly`() {
        assertThrows(FlacDecodingException::class.java) {
            FlacDecoder.decode(byteArrayOf(1, 2, 3, 4, 5))
        }
    }

    // ------------------------------------------------------------------
    // Cobertura añadida en FASE 1 (Doc/26): la Sección 11 del prompt
    // maestro exige explícitamente edge cases deterministas alrededor de
    // los límites de tamaño de bloque, y combinaciones mono/stereo x
    // profundidad de bits que faltaban. Todos los valores usan generadores
    // pseudoaleatorios *deterministas* (semilla fija), nunca aleatoriedad
    // real, tal como exige esa misma sección ("No utilizar random no
    // determinista").
    // ------------------------------------------------------------------

    @Test
    fun `round trips exactly at block size boundary 511 (one sample under default multiple)`() {
        val rnd = Random(511)
        val samples = IntArray(511) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 511)
    }

    @Test
    fun `round trips exactly at block size boundary 512`() {
        val rnd = Random(512)
        val samples = IntArray(512) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 512)
    }

    @Test
    fun `round trips exactly at block size boundary 513 (one sample over, forces a second partial frame)`() {
        val rnd = Random(513)
        val samples = IntArray(513) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = 512)
    }

    @Test
    fun `round trips exactly at block size boundary 4095 (one sample under the default block size)`() {
        val rnd = Random(4095)
        val samples = IntArray(4095) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = FlacEncoder.DEFAULT_BLOCK_SIZE)
    }

    @Test
    fun `round trips exactly at block size boundary 4096 (exactly the default block size, single frame)`() {
        val rnd = Random(4096)
        val samples = IntArray(4096) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = FlacEncoder.DEFAULT_BLOCK_SIZE)
    }

    @Test
    fun `round trips exactly at block size boundary 4097 (one sample over the default block size, second frame has a single sample)`() {
        val rnd = Random(4097)
        val samples = IntArray(4097) { rnd.nextInt(-32768, 32768) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 16), blockSize = FlacEncoder.DEFAULT_BLOCK_SIZE)
    }

    @Test
    fun `round trips a stream that is exactly two samples long (mono)`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(12345, -6789)), 44100, 16))
    }

    @Test
    fun `round trips a stream that is exactly three samples long (mono)`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(1, -1, 32767)), 44100, 16))
    }

    @Test
    fun `round trips random stereo 8-bit`() {
        val rnd = Random(48)
        val left = IntArray(1200) { rnd.nextInt(-128, 128) }
        val right = IntArray(1200) { rnd.nextInt(-128, 128) }
        assertRoundTrip(FlacPcmAudio(arrayOf(left, right), 22050, 8), blockSize = 256)
    }

    @Test
    fun `round trips random stereo 24-bit`() {
        val rnd = Random(49)
        val left = IntArray(1800) { rnd.nextInt(-8388608, 8388608) }
        val right = IntArray(1800) { rnd.nextInt(-8388608, 8388608) }
        assertRoundTrip(FlacPcmAudio(arrayOf(left, right), 48000, 24), blockSize = 512)
    }

    @Test
    fun `round trips random mono 32-bit (FLAC codec layer only -- PcmNormalizer does not route 32-bit WAV here, see PcmNormalizerTest)`() {
        // FlacEncoder/FlacPcmAudio soportan bitsPerSample 4..32 (STREAMINFO
        // lo permite, Sección 11 del prompt maestro: "32-bit si el modelo
        // realmente lo permite"). El propio códec FLAC de este proyecto sí
        // lo permite a este nivel; lo que NO soporta esta fase es la
        // conversión WAV PCM_32/FLOAT_32 -> FlacPcmAudio en PcmNormalizer
        // (rechazo explícito y documentado, Doc/23 §15, por ausencia de
        // evidencia sobre qué espera DirectWave). Este test construye el
        // FlacPcmAudio de 32-bit directamente, sin pasar por PcmNormalizer,
        // para verificar que el códec en sí es correcto en ese ancho de
        // bits, sin mezclar esa verificación con la limitación deliberada
        // de la capa WAV/Monolithic (que PcmNormalizerTest ya cubre).
        // Rango acotado deliberadamente a ±2^24 (no al máximo teórico de
        // 32-bit, ±2^31): con datos verdaderamente aleatorios e
        // independientes, un predictor FIXED de orden 4 puede amplificar la
        // magnitud del residual hasta 16x en el peor caso (suma de los
        // coeficientes binomiales 1,4,6,4,1) frente a la muestra original --
        // 16 * 2^24 = 2^28, con margen cómodo bajo el límite de 31 bits de
        // la vía de escape de Rice coding (`FlacRiceCoder`), sin depender de
        // que `selectBestOrder` elija un orden bajo por casualidad. Esto
        // prueba el códec de 32-bit en sí (Sección 11 del prompt maestro),
        // no el límite absoluto de amplitud de FlacRiceCoder, que ya cubre
        // `FlacRiceCoderTest` de forma dedicada y determinista.
        val rnd = Random(50)
        val bound = 1 shl 24
        val samples = IntArray(600) { rnd.nextInt(-bound, bound) }
        assertRoundTrip(FlacPcmAudio(arrayOf(samples), 44100, 32), blockSize = 300)
    }

    @Test
    fun `round trips 8-bit boundary values (min and max representable)`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(-128, 127, -128, 127, 0, -128, 127)), 44100, 8))
    }

    @Test
    fun `round trips 24-bit boundary values (min and max representable)`() {
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(-8388608, 8388607, -8388608, 8388607, 0)), 44100, 24))
    }

    @Test
    fun `encoding with blockSize=0x10000 (65536) is rejected -- STREAMINFO min max_block_size are 16-bit fields without offset, so 65536 is not representable`() {
        // Bug real encontrado en la auditoría de FASE 1 (Doc/26): antes de
        // esta corrección, este blockSize pasaba el require() de entrada de
        // encode() sin problema y solo fallaba, con datos suficientes, muy
        // dentro de FlacMetadataWriter.StreamInfo.init con un mensaje que no
        // explicaba la causa real. Ahora se rechaza inmediatamente en la
        // frontera pública, con un mensaje que sí explica el porqué. El
        // valor máximo válido, 0xFFFF (65535), sigue funcionando (ver el
        // siguiente test).
        assertThrows(IllegalArgumentException::class.java) {
            FlacEncoder.encode(FlacPcmAudio(arrayOf(intArrayOf(1, 2, 3)), 44100, 16), blockSize = 0x10000)
        }
    }

    @Test
    fun `encoding with the maximum valid blockSize (0xFFFF, 65535) succeeds`() {
        // No se genera un array de 65535 muestras reales (coste de tiempo de
        // test innecesario para lo que esto verifica): con menos muestras
        // que el blockSize, el encoder simplemente produce un único frame
        // parcial de blockSize real = frameCount, y el propio blockSize
        // pedido (0xFFFF) debe pasar el require() de entrada sin lanzar,
        // que es exactamente el límite que este test protege.
        assertRoundTrip(FlacPcmAudio(arrayOf(intArrayOf(1, 2, 3, 4, 5)), 44100, 16), blockSize = 0xFFFF)
    }
}
