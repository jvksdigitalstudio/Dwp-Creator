package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsrTest {
    private val sr = 48_000f

    private fun coefficients(attack: Float = 3f, decay: Float = 100f, sustain: Float = 0.5f, release: Float = 100f) =
        AdsrCoefficients.from(AdsrState(attack, decay, sustain, release), sr)

    private fun run(env: Adsr, c: AdsrCoefficients, frames: Int): Float {
        var v = 0f
        repeat(frames) { v = env.next(c) }
        return v
    }

    @Test
    fun attackIsLinearAndEndsAtTheAttackTime() {
        val c = coefficients(attack = 10f)
        val env = Adsr()
        env.trigger()
        assertEquals(0.5f, run(env, c, 240), 0.01f) // 10 ms = 480 muestras
        run(env, c, 242)
        assertTrue(env.stage != Adsr.Stage.ATTACK)
    }

    @Test
    fun decayFallsExponentiallyToTheSustainLevel() {
        val c = coefficients(attack = 1f, decay = 100f, sustain = 0.5f)
        val env = Adsr()
        env.trigger()
        run(env, c, 48)               // ataque de 1 ms
        val afterDecayTime = run(env, c, 4_800)
        assertTrue("tras el tiempo de decay debe quedar casi en el sostenido: $afterDecayTime", afterDecayTime < 0.5006f)
        run(env, c, 3_000)
        assertEquals(Adsr.Stage.SUSTAIN, env.stage)
        assertEquals(0.5f, env.value, 0f)
    }

    @Test
    fun sustainFollowsTheParameterLive() {
        val env = Adsr()
        env.trigger()
        run(env, coefficients(attack = 1f, sustain = 1f), 200)
        assertEquals(Adsr.Stage.SUSTAIN, env.stage)
        assertEquals(0.3f, run(env, coefficients(attack = 1f, sustain = 0.3f), 1), 0f)
    }

    @Test
    fun releaseDropsSixtyDecibelsInTheReleaseTimeAndThenGoesIdle() {
        val c = coefficients(attack = 1f, sustain = 1f, release = 100f)
        val env = Adsr()
        env.trigger()
        run(env, c, 300)
        assertEquals(1f, env.value, 0f)
        env.release()
        assertTrue(env.isFinishing)
        assertEquals(0.001f, run(env, c, 4_800), 0.0002f)
        run(env, c, 1_700)
        assertTrue(env.isIdle)
        assertEquals(0f, env.value, 0f)
    }

    @Test
    fun releaseDuringTheAttackStartsFromTheCurrentLevel() {
        val c = coefficients(attack = 3f)
        val env = Adsr()
        env.trigger()
        val before = run(env, c, 50)
        env.release()
        val after = env.next(c)
        assertTrue("debe bajar desde $before: $after", after < before && after > before * 0.9f)
    }

    @Test
    fun retriggerContinuesFromTheCurrentValueWithoutAJump() {
        val c = coefficients(attack = 10f)
        val env = Adsr()
        env.trigger()
        run(env, c, 200)
        env.release()
        val mid = run(env, c, 20)
        env.trigger()
        val next = env.next(c)
        assertTrue("salto de ${next - mid}", kotlin.math.abs(next - mid) < 0.01f)
    }

    @Test
    fun killIsLinearAndFinishesWithinTheRequestedFrames() {
        val c = coefficients(attack = 1f, sustain = 1f)
        val env = Adsr()
        env.trigger()
        run(env, c, 300)
        env.kill(100f)
        assertTrue(env.isFinishing)
        assertEquals(0.5f, run(env, c, 50), 0.02f)
        run(env, c, 52)
        assertTrue(env.isIdle)
    }

    @Test
    fun killAndReleaseAreIgnoredWhenIdle() {
        val env = Adsr()
        env.release()
        env.kill(10f)
        assertFalse(env.isFinishing)
        assertTrue(env.isIdle)
    }
}
