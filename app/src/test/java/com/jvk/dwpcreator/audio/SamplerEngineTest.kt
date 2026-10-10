package com.jvk.dwpcreator.audio

import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache.CachedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SamplerEngineTest {

    private val sr = 48_000

    /** Muestra mono de 16 bits con un valor constante: así la amplitud de salida es predecible. */
    private fun constantSample(frames: Int, value: Int = 16_384, rate: Int = sr): CachedSample {
        val pcm = ByteArray(frames * 2)
        for (i in 0 until frames) {
            pcm[i * 2] = (value and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return CachedSample(pcm, rate, 1)
    }

    private fun sineSample(frames: Int, rate: Int, freq: Double): CachedSample {
        val pcm = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val v = (sin(2.0 * Math.PI * freq * i / rate) * 12_000).toInt()
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return CachedSample(pcm, rate, 1)
    }

    private fun render(engine: SamplerEngine, frames: Int): FloatArray {
        val out = FloatArray(frames * 2)
        engine.render(out, frames)
        return out
    }

    @Test
    fun silenceWithoutNotes() {
        val engine = SamplerEngine(sr)
        val out = render(engine, 1024)
        for (x in out) assertEquals(0f, x, 0f)
        assertEquals(0, engine.activeVoiceCount)
    }

    @Test
    fun noteProducesCenteredEqualPowerOutputAtVelocityAndVolume() {
        val engine = SamplerEngine(sr)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr), velocity = 127, volume = 1f, pan = 0f)
        val out = render(engine, 2048)
        // 16384/32768 = 0.5; centro de ley de potencia constante = 0.7071 por canal.
        val l = out[2 * 1500]
        val r = out[2 * 1500 + 1]
        assertEquals(0.5f * 0.7071f, l, 0.01f)
        assertEquals(l, r, 1e-4f)
        assertEquals(1, engine.activeVoiceCount)
    }

    @Test
    fun velocityScalesLinearlyAndHardPanSendsToOneSide() {
        val engine = SamplerEngine(sr)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr), velocity = 64, volume = 1f, pan = -1f)
        val out = render(engine, 2048)
        assertEquals(0.5f * 64f / 127f, out[2 * 1500], 0.01f)
        assertEquals(0f, out[2 * 1500 + 1], 1e-4f)
    }

    @Test
    fun attackRampAvoidsAStepAtNoteStart() {
        val engine = SamplerEngine(sr)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr), 127, 1f, 0f)
        val out = render(engine, 512)
        assertTrue("primer frame casi cero (sin escalón)", abs(out[0]) < 0.01f)
        assertTrue(out[2 * 300] > out[2 * 10])
    }

    @Test
    fun releaseFadesOutFinishesVoiceAndMarksToken() {
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        engine.noteOn(token, constantSample(sr * 2), 127, 1f, 0f)
        render(engine, 4096)
        assertEquals(1, engine.activeVoiceCount)

        token.released = true
        val out = render(engine, 4096)
        assertEquals(0, engine.activeVoiceCount)
        assertTrue(token.finished)
        assertEquals(0f, out[out.size - 2], 1e-6f)
        // El fundido es gradual: nunca un salto mayor que una fracción pequeña entre frames contiguos.
        var maxStep = 0f
        for (i in 1 until 4096) maxStep = maxOf(maxStep, abs(out[2 * i] - out[2 * (i - 1)]))
        assertTrue("salto máximo $maxStep", maxStep < 0.01f)
    }

    @Test
    fun releaseRequestedBeforeNoteReachesEngineStillPlaysMinimumThenStops() {
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        token.released = true
        engine.noteOn(token, constantSample(sr * 2), 127, 1f, 0f)
        val out = render(engine, 4096)
        assertTrue("suena un instante", out.any { abs(it) > 0.05f })
        assertEquals(0, engine.activeVoiceCount)
        assertTrue(token.finished)
    }

    @Test
    fun sampleEndsNaturallyAndMarksTokenFinished() {
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        engine.noteOn(token, constantSample(1000), 127, 1f, 0f)
        render(engine, 2048)
        assertEquals(0, engine.activeVoiceCount)
        assertTrue(token.finished)
        assertFalse(token.released)
    }

    @Test
    fun sampleRateConversionKeepsDurationAndPitch() {
        // 44.1 kHz de origen sobre salida de 48 kHz: 44100 frames deben durar ~1 s.
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        engine.noteOn(token, sineSample(44_100, 44_100, 441.0), 127, 1f, 0f)
        val out = render(engine, 48_000 - 400)
        assertEquals(1, engine.activeVoiceCount)
        render(engine, 1000)
        assertEquals(0, engine.activeVoiceCount)

        // 441 Hz a 48 kHz -> ~108.8 frames por ciclo: contar cruces ascendentes por cero en ~0.5 s.
        var crossings = 0
        for (i in 2000 until 2000 + 24_000) {
            if (out[2 * (i - 1)] <= 0f && out[2 * i] > 0f) crossings++
        }
        assertEquals(220.5, crossings.toDouble(), 2.0)
    }

    @Test
    fun monophonicModeReleasesThePreviousNote() {
        val engine = SamplerEngine(sr)
        engine.fxState = SamplerFxState(monophonic = true)
        val first = SamplerEngine.VoiceToken()
        val second = SamplerEngine.VoiceToken()
        engine.noteOn(first, constantSample(sr * 2), 127, 1f, 0f)
        render(engine, 2048)
        engine.noteOn(second, constantSample(sr * 2), 127, 1f, 0f)
        render(engine, 2048)
        assertTrue(first.finished)
        assertFalse(second.finished)
        assertEquals(1, engine.activeVoiceCount)
    }

    @Test
    fun polyphonicModeKeepsBothNotes() {
        val engine = SamplerEngine(sr)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr * 2), 127, 1f, 0f)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr * 2), 127, 1f, 0f)
        render(engine, 2048)
        assertEquals(2, engine.activeVoiceCount)
    }

    @Test
    fun polyphonyLimitStealsOldestVoicesWithoutCrashingAndKeepsOutputBounded() {
        val engine = SamplerEngine(sr)
        val tokens = List(100) { SamplerEngine.VoiceToken() }
        for (t in tokens) {
            engine.noteOn(t, constantSample(sr * 4), 127, 1f, 0f)
            val out = render(engine, 256)
            for (x in out) assertTrue(!x.isNaN() && abs(x) <= 0.97f + 1e-4f)
        }
        render(engine, 4096)
        assertTrue(engine.activeVoiceCount <= SamplerEngine.MAX_POLYPHONY)
        assertTrue("las primeras notas fueron robadas", tokens.first().finished)
        assertFalse("la última sigue sonando", tokens.last().finished)
    }

    @Test
    fun invalidSampleIsRejectedAndTokenFinished() {
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        engine.noteOn(token, CachedSample(ByteArray(0), sr, 1), 127, 1f, 0f)
        render(engine, 256)
        assertTrue(token.finished)
        assertEquals(0, engine.activeVoiceCount)
    }

    @Test
    fun resetAllVoicesSilencesEverything() {
        val engine = SamplerEngine(sr)
        val token = SamplerEngine.VoiceToken()
        engine.noteOn(token, constantSample(sr * 4), 127, 1f, 0f)
        render(engine, 2048)
        engine.resetAllVoices()
        render(engine, 2048)
        assertEquals(0, engine.activeVoiceCount)
        assertTrue(token.finished)
    }

    @Test
    fun renderIsIndependentOfBlockSize() {
        fun run(block: Int): FloatArray {
            val engine = SamplerEngine(sr)
            engine.noteOn(SamplerEngine.VoiceToken(), sineSample(sr, sr, 330.0), 100, 0.8f, 0.3f)
            val all = FloatArray(8192 * 2)
            var done = 0
            val tmp = FloatArray(block * 2)
            while (done < 8192) {
                val n = minOf(block, 8192 - done)
                engine.render(tmp, n)
                System.arraycopy(tmp, 0, all, done * 2, n * 2)
                done += n
            }
            return all
        }
        val a = run(8192)
        val b = run(137)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-4f)
    }

    @Test
    fun quietSecondsGrowsWhenIdleAndResetsOnSound() {
        val engine = SamplerEngine(sr)
        render(engine, sr)
        assertTrue(engine.quietSeconds >= 0.99f)
        engine.noteOn(SamplerEngine.VoiceToken(), constantSample(sr), 127, 1f, 0f)
        render(engine, 1024)
        assertEquals(0f, engine.quietSeconds, 1e-6f)
        assertFalse(engine.hasPendingCommands())
    }

    @Test
    fun enabledReverbExtendsSoundAfterTheNoteEnds() {
        val engine = SamplerEngine(sr)
        engine.fxState = SamplerFxState(reverbEnabled = true, reverbMix = 1f, reverbDecaySec = 3f)
        engine.noteOn(SamplerEngine.VoiceToken(), sineSample(4800, sr, 440.0), 127, 1f, 0f)
        render(engine, 4800 + 256)
        val tail = render(engine, 24_000)
        assertEquals(0, engine.activeVoiceCount)
        assertTrue("la cola de la reverb sigue sonando", tail.any { abs(it) > 1e-3f })
        assertTrue(engine.peakLeft >= 0f)
    }
}
