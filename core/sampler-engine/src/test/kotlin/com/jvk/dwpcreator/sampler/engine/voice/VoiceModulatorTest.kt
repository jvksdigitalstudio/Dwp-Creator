package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.sampler.api.state.ModulationEnvelopeState
import com.jvk.dwpcreator.sampler.api.state.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModulatorTest {
    private val sr = 48_000f

    private fun settings(block: (VoiceState) -> VoiceState = { it }) = VoiceSettings.from(block(VoiceState()), sr)

    private fun lfoState(
        destination: LfoDestination,
        amount: Float,
        shape: LfoShape = LfoShape.SINE,
        rateHz: Float = 5f
    ) = LfoState(rateHz = rateHz, shape = shape, amount = amount, destination = destination)

    private fun modulatorFor(s: VoiceSettings) = VoiceModulator().also { it.start(s, 1) }

    @Test
    fun defaultSettingsModulateNothing() {
        val s = settings()
        val m = modulatorFor(s)
        repeat(50) { m.update(16, s) }
        assertEquals(0f, m.pitchSemitones, 0f)
        assertEquals(0f, m.cutoffOctaves, 0f)
        assertEquals(0f, m.resonance, 0f)
        assertEquals(1f, m.level, 0f)
        assertEquals(0f, m.pan, 0f)
    }

    @Test
    fun filterEnvelopeAmountScalesToEightOctaves() {
        val s = settings { it.copy(filterEnvelope = ModulationEnvelopeState(AdsrState(1f, 4_000f, 1f, 300f), 1f)) }
        val m = modulatorFor(s)
        repeat(20) { m.update(16, s) } // pasado el ataque de 1 ms
        assertEquals(VoiceModulator.FILTER_ENVELOPE_OCTAVES, m.cutoffOctaves, 0.01f)
    }

    @Test
    fun pitchEnvelopeNegativeAmountPullsThePitchDown() {
        val s = settings { it.copy(pitchEnvelope = ModulationEnvelopeState(AdsrState(1f, 4_000f, 1f, 300f), -0.5f)) }
        val m = modulatorFor(s)
        repeat(20) { m.update(16, s) }
        assertEquals(-12f, m.pitchSemitones, 0.01f)
    }

    @Test
    fun envelopeFollowsItsAdsrOverTime() {
        val s = settings { it.copy(filterEnvelope = ModulationEnvelopeState(AdsrState(1f, 100f, 0f, 300f), 1f)) }
        val m = modulatorFor(s)
        repeat(20) { m.update(16, s) }
        val early = m.cutoffOctaves
        repeat(((0.5f * sr) / 16f).toInt()) { m.update(16, s) }
        assertTrue("$early -> ${m.cutoffOctaves}", early > 4f && m.cutoffOctaves < 0.01f)
    }

    @Test
    fun pitchLfoDepthFollowsTheQuadraticTaper() {
        for ((amount, expectedPeak) in listOf(1f to 12f, 0.5f to 3f)) {
            val s = settings { it.copy(lfo1 = lfoState(LfoDestination.PITCH, amount)) }
            val m = modulatorFor(s)
            var max = -100f
            var min = 100f
            repeat(((0.4f * sr) / 16f).toInt()) {
                m.update(16, s)
                max = maxOf(max, m.pitchSemitones)
                min = minOf(min, m.pitchSemitones)
            }
            assertEquals("amount $amount", expectedPeak, max, 0.2f)
            assertEquals("amount $amount", -expectedPeak, min, 0.2f)
        }
    }

    @Test
    fun levelLfoDipsBetweenFullLevelAndOneMinusDepth() {
        val s = settings { it.copy(lfo1 = lfoState(LfoDestination.LEVEL, 0.5f, LfoShape.SQUARE, rateHz = 2f)) }
        val m = modulatorFor(s)
        val seen = HashSet<Float>()
        repeat(((1f * sr) / 16f).toInt()) {
            m.update(16, s)
            seen += m.level
        }
        assertEquals(setOf(1f, 0.5f), seen)
    }

    @Test
    fun negativeLevelAmountInvertsTheWaveform() {
        fun firstLevel(amount: Float): Float {
            val s = settings { it.copy(lfo1 = lfoState(LfoDestination.LEVEL, amount, LfoShape.SQUARE, rateHz = 1f)) }
            return modulatorFor(s).also { it.update(16, s) }.level
        }
        assertEquals(1f, firstLevel(0.5f), 1e-6f)
        assertEquals(0.5f, firstLevel(-0.5f), 1e-6f)
    }

    @Test
    fun panAndResonanceLfosHaveTheirOwnScale() {
        val pan = settings { it.copy(lfo1 = lfoState(LfoDestination.PAN, 1f, LfoShape.SQUARE, rateHz = 1f)) }
        val reso = settings { it.copy(lfo2 = lfoState(LfoDestination.RESONANCE, 1f, LfoShape.SQUARE, rateHz = 1f)) }
        assertEquals(1f, modulatorFor(pan).also { it.update(16, pan) }.pan, 1e-6f)
        assertEquals(VoiceModulator.LFO_RESONANCE, modulatorFor(reso).also { it.update(16, reso) }.resonance, 1e-6f)
    }

    @Test
    fun twoLfosOnTheSameDestinationAdd() {
        val s = settings {
            it.copy(
                lfo1 = lfoState(LfoDestination.FILTER, 0.5f, LfoShape.SQUARE, rateHz = 1f),
                lfo2 = lfoState(LfoDestination.FILTER, 0.5f, LfoShape.SQUARE, rateHz = 1f)
            )
        }
        val m = modulatorFor(s).also { it.update(16, s) }
        assertEquals(2f * 0.5f * VoiceModulator.LFO_FILTER_OCTAVES, m.cutoffOctaves, 1e-5f)
    }
}
