package com.jvk.dwpcreator.codec.wav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavLoopPointsTest {

    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    /** Cuerpo de un chunk `smpl` con los bucles dados como pares (inicio, fin inclusivo). */
    private fun smplBody(vararg loops: Pair<Int, Int>): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(7) { out.write(le32(0)) }          // fabricante .. smpteOffset (7 campos)
        out.write(le32(loops.size))               // numSampleLoops (offset 28)
        out.write(le32(0))                        // samplerData
        for ((start, end) in loops) {
            out.write(le32(0)); out.write(le32(0)) // id, tipo
            out.write(le32(start)); out.write(le32(end))
            out.write(le32(0)); out.write(le32(0)) // fracción, playCount
        }
        return out.toByteArray()
    }

    private fun parse(body: ByteArray, frames: Int = 1_000) = WavLoopPoints.parse(body, 0, body.size, frames)

    @Test
    fun readsTheFirstLoopAndMakesItsEndExclusive() {
        assertEquals(SampleLoop(100, 401), parse(smplBody(100 to 400)))
    }

    @Test
    fun usesTheFirstOfSeveralLoops() {
        assertEquals(SampleLoop(10, 21), parse(smplBody(10 to 20, 300 to 500)))
    }

    @Test
    fun readsTheChunkFromAnOffsetInsideALargerBuffer() {
        val body = smplBody(5 to 50)
        val buffer = ByteArray(12) + body
        assertEquals(SampleLoop(5, 51), WavLoopPoints.parse(buffer, 12, body.size, 1_000))
    }

    @Test
    fun noLoopsMeansNull() {
        assertNull(parse(smplBody()))
    }

    @Test
    fun aTruncatedChunkIsRejected() {
        val body = smplBody(100 to 400)
        assertNull(WavLoopPoints.parse(body, 0, 40, 1_000))
        assertNull(WavLoopPoints.parse(body, 0, 0, 1_000))
        assertNull(WavLoopPoints.parse(body, 10, body.size, 1_000))
        assertNull(WavLoopPoints.parse(body, -1, body.size, 1_000))
    }

    @Test
    fun aLoopCountLargerThanTheChunkIsRejected() {
        val body = smplBody(100 to 400)
        body[28] = 9 // dice que hay 9 bucles pero sólo cabe 1
        assertNull(parse(body))
    }

    @Test
    fun loopsOutsideTheSampleOrEmptyAreRejected() {
        assertNull(parse(smplBody(100 to 1_500)))     // termina después del audio
        assertNull(parse(smplBody(-3 to 50)))          // inicio negativo
        assertNull(parse(smplBody(400 to 100)))        // invertido
        assertNull(parse(smplBody(100 to 1_000)))      // fin inclusivo = frame 1000 (fuera: hay 1000 frames)
    }

    @Test
    fun theDecoderExposesTheLoopOfARealWav() {
        val frames = 400
        val data = ByteArray(frames * 2)
        val smpl = smplBody(100 to 300)
        val out = ByteArrayOutputStream()
        val fmt = le16(1) + le16(1) + le32(44_100) + le32(88_200) + le16(2) + le16(16)
        val payload = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray())
            write("fmt ".toByteArray()); write(le32(fmt.size)); write(fmt)
            write("smpl".toByteArray()); write(le32(smpl.size)); write(smpl)
            write("data".toByteArray()); write(le32(data.size)); write(data)
        }.toByteArray()
        out.write("RIFF".toByteArray()); out.write(le32(payload.size)); out.write(payload)

        val decoded = WavDecoder.decode(out.toByteArray())
        assertEquals(SampleLoop(100, 301), decoded.loop)
        assertEquals(frames, decoded.pcmData.size / 2)
    }

    @Test
    fun aWavWithoutSmplHasNoLoop() {
        val data = ByteArray(200)
        val fmt = le16(1) + le16(1) + le32(44_100) + le32(88_200) + le16(2) + le16(16)
        val payload = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray())
            write("fmt ".toByteArray()); write(le32(fmt.size)); write(fmt)
            write("data".toByteArray()); write(le32(data.size)); write(data)
        }.toByteArray()
        val wav = "RIFF".toByteArray() + le32(payload.size) + payload
        assertNull(WavDecoder.decode(wav).loop)
    }
}
