package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.codec.wav.SampleLoop
import com.jvk.dwpcreator.sampler.api.state.LoopMode
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplePlayheadTest {
    private val frames = 1_000
    private val loop = SampleLoop(200, 400)

    private fun playhead(
        mode: LoopMode,
        loopRegion: SampleLoop? = loop,
        start: Float = 0f,
        reverse: Boolean = false,
        total: Int = frames
    ) = SamplePlayhead().also {
        it.start(EngineTestSupport.rampSample(total, slope = 10, loop = loopRegion), start, reverse, mode)
    }

    private fun advance(p: SamplePlayhead, steps: Int, step: Double = 1.0) = repeat(steps) { p.advance(step) }

    @Test
    fun playsOnceFromStartToEndWithoutALoop() {
        val p = playhead(LoopMode.OFF)
        advance(p, 999)
        assertFalse(p.ended)
        p.advance(1.0)
        assertTrue(p.ended)
    }

    @Test
    fun readsTheInterpolatedValueAtThePosition() {
        val p = playhead(LoopMode.OFF)
        advance(p, 100)
        assertEquals(100 * 10 / 32768f, p.read(0), 1e-6f)
        p.advance(0.5)
        assertEquals(100.5f * 10 / 32768f, p.read(0), 1e-5f)
    }

    @Test
    fun forwardLoopStaysInsideTheRegionForever() {
        val p = playhead(LoopMode.FORWARD)
        advance(p, 50_000, 1.37)
        assertFalse(p.ended)
        assertTrue(p.framePosition >= 200.0 && p.framePosition < 400.0)
        assertTrue(p.looping)
    }

    @Test
    fun forwardLoopWrapsKeepingTheFractionalOvershoot() {
        val p = playhead(LoopMode.FORWARD, start = 0.375f) // posición 375
        advance(p, 1, 30.5)                                // 405,5 -> 205,5
        assertEquals(205.5, p.framePosition, 1e-9)
    }

    @Test
    fun bounceGoesBackAndForthBetweenTheLoopPoints() {
        val p = playhead(LoopMode.BOUNCE)
        advance(p, 400)
        advance(p, 200)                       // sube hasta 400, baja hasta 200 y rebota
        assertEquals(200.0, p.framePosition, 1e-9)
        advance(p, 50)
        assertEquals(250.0, p.framePosition, 1e-9)
        advance(p, 20_000)
        assertTrue(p.framePosition in 200.0..400.0)
        assertFalse(p.ended)
    }

    @Test
    fun sustainLoopsWhileHeldAndPlaysPastTheLoopAfterRelease() {
        val p = playhead(LoopMode.SUSTAIN)
        advance(p, 5_000)
        assertTrue(p.looping)
        assertFalse(p.ended)
        p.release()
        assertFalse(p.looping)
        advance(p, 1_000)
        assertTrue(p.ended)
    }

    @Test
    fun forwardLoopIgnoresReleaseAndKeepsLooping() {
        val p = playhead(LoopMode.FORWARD)
        p.release()
        advance(p, 5_000)
        assertTrue(p.looping)
        assertFalse(p.ended)
    }

    @Test
    fun loopModesWithoutLoopPointsPlayOnce() {
        for (mode in listOf(LoopMode.FORWARD, LoopMode.SUSTAIN, LoopMode.BOUNCE)) {
            val p = playhead(mode, loopRegion = null)
            assertFalse(p.looping)
            advance(p, 1_000)
            assertTrue("$mode", p.ended)
        }
    }

    @Test
    fun anInconsistentLoopIsIgnored() {
        for (bad in listOf(SampleLoop(300, 300), SampleLoop(500, 400), SampleLoop(900, 2_000), SampleLoop(-5, 100), SampleLoop(10, 11))) {
            val p = playhead(LoopMode.FORWARD, loopRegion = bad)
            assertFalse("$bad", p.looping)
        }
    }

    @Test
    fun oneShotNeverLoops() {
        val p = playhead(LoopMode.ONE_SHOT)
        assertFalse(p.looping)
        advance(p, 1_000)
        assertTrue(p.ended)
    }

    @Test
    fun reverseStartsAtTheEndAndRunsBackwards() {
        val p = playhead(LoopMode.OFF, reverse = true)
        assertEquals(999.0, p.framePosition, 1e-9)
        advance(p, 500)
        assertEquals(499.0, p.framePosition, 1e-9)
        advance(p, 499)
        assertEquals(0.0, p.framePosition, 1e-9)
        assertFalse(p.ended)
        advance(p, 1)
        assertTrue(p.ended)
    }

    @Test
    fun reverseIsIgnoredWhileALoopIsActive() {
        val p = playhead(LoopMode.FORWARD, reverse = true)
        advance(p, 10)
        assertEquals(10.0, p.framePosition, 1e-9)
    }

    @Test
    fun startFractionPositionsTheCursor() {
        assertEquals(500.0, playhead(LoopMode.OFF, start = 0.5f).framePosition, 1e-9)
        assertEquals(499.0, playhead(LoopMode.OFF, start = 0.5f, reverse = true).framePosition, 1e-9)
    }

    @Test
    fun tailGainFadesOnlyNearTheEndWhenNotLooping() {
        val p = playhead(LoopMode.OFF)
        assertEquals(1f, p.tailGain(), 0f)
        advance(p, 999)
        assertTrue(p.tailGain() < 0.05f)
        val looping = playhead(LoopMode.FORWARD, start = 0.3f)
        assertEquals(1f, looping.tailGain(), 0f)
    }

    @Test
    fun interpolationAcrossTheLoopSeamUsesTheLoopStartNotTheAudioPastTheEnd() {
        // El audio posterior al final del bucle es muy distinto (30000 frente a 1000): si los
        // vecinos de la interpolación se leyeran de ahí en vez de volver al inicio del bucle,
        // la salida se mezclaría con ese valor en la costura.
        val sample = EngineTestSupport.customSample(frames, loop = loop) { if (it < 400) 1_000 else 30_000 }
        val p = SamplePlayhead()
        p.start(sample, 0.3995f, false, LoopMode.FORWARD) // posición ~399,5
        assertEquals(1_000 / 32768f, p.read(0), 1e-6f)
    }
}
