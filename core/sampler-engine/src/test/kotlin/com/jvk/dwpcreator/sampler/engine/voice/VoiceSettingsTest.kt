package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.rhythm.LfoSync
import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import com.jvk.dwpcreator.sampler.api.state.FilterState
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import com.jvk.dwpcreator.sampler.api.state.LfoRateMode
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.sampler.api.state.LoopMode
import com.jvk.dwpcreator.sampler.api.state.ModulationEnvelopeState
import com.jvk.dwpcreator.sampler.api.state.SampleState
import com.jvk.dwpcreator.sampler.api.state.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsTest {
    private val sr = 48_000f

    private fun filterSettings(
        filter: FilterState = FilterState(),
        envelopeAmount: Float = 0f,
        lfos: List<LfoState> = emptyList()
    ) = FilterSettings.from(filter, envelopeAmount, lfos.map { LfoSettings.from(it, 120f, sr) })

    @Test
    fun theFactoryFilterIsBypassed() {
        assertTrue(filterSettings().bypass)
        assertTrue(VoiceSettings.from(VoiceState(), sr).filter.bypass)
    }

    @Test
    fun aDisabledFilterIsBypassedWhateverItsSettings() {
        assertTrue(filterSettings(FilterState(enabled = false, cutoff = 0.2f, resonance = 0.9f)).bypass)
    }

    @Test
    fun anyAudibleSettingDisablesTheBypass() {
        assertFalse(filterSettings(FilterState(cutoff = 0.5f)).bypass)
        assertFalse(filterSettings(FilterState(resonance = 0.2f)).bypass)
        assertFalse(filterSettings(FilterState(drive = 0.2f)).bypass)
        assertFalse(filterSettings(FilterState(keyTrack = 0.5f)).bypass)
        assertFalse(filterSettings(FilterState(velocityTrack = -0.5f)).bypass)
        assertFalse(filterSettings(envelopeAmount = 0.4f).bypass)
    }

    @Test
    fun anOpenHighpassStillFiltersSoItIsNeverBypassed() {
        assertFalse(filterSettings(FilterState(mode = FilterMode.HIGHPASS_12)).bypass)
        assertFalse(filterSettings(FilterState(mode = FilterMode.BANDPASS)).bypass)
        assertFalse(filterSettings(FilterState(mode = FilterMode.NOTCH)).bypass)
    }

    @Test
    fun anLfoOnTheCutoffOrResonanceDisablesTheBypassButOneOnThePitchDoesNot() {
        assertFalse(filterSettings(lfos = listOf(LfoState(amount = 0.5f, destination = LfoDestination.FILTER))).bypass)
        assertFalse(filterSettings(lfos = listOf(LfoState(amount = 0.5f, destination = LfoDestination.RESONANCE))).bypass)
        assertTrue(filterSettings(lfos = listOf(LfoState(amount = 0.5f, destination = LfoDestination.PITCH))).bypass)
        assertTrue(filterSettings(lfos = listOf(LfoState(amount = 0f, destination = LfoDestination.FILTER))).bypass)
    }

    @Test
    fun sampleParametersAreResolved() {
        val state = VoiceState(
            sample = SampleState(
                pitchSemitones = 3f, fineCents = 50f, glideMs = 100f, loopMode = LoopMode.BOUNCE,
                reverse = true, legato = true, glideOnlyLegato = false, bendRangeSemitones = 7f,
                velocitySensitivity = 0.4f, startFraction = 0.25f, pitchTrack = 0.5f
            )
        )
        val s = VoiceSettings.from(state, sr)
        assertEquals(3.5f, s.baseSemitones, 1e-6f)
        assertEquals(4_800f, s.glideFrames, 1e-3f)
        assertEquals(LoopMode.BOUNCE, s.loopMode)
        assertTrue(s.reverse && s.legato)
        assertFalse(s.glideOnlyLegato)
        assertEquals(7f, s.bendRangeSemitones, 0f)
        assertEquals(0.4f, s.velocitySensitivity, 0f)
        assertEquals(0.25f, s.startFraction, 0f)
        assertEquals(0.5f, s.pitchTrack, 0f)
    }

    @Test
    fun envelopesAndLfosAreResolvedFromTheirStates() {
        val state = VoiceState(
            filterEnvelope = ModulationEnvelopeState(AdsrState.FILTER, 0.6f),
            pitchEnvelope = ModulationEnvelopeState(AdsrState.PITCH, -0.2f),
            lfo1 = LfoState(rateMode = LfoRateMode.TEMPO, division = LfoSync.NAMES.indexOf("1/4"), amount = 1f),
            tempoBpm = 150f
        )
        val s = VoiceSettings.from(state, sr)
        assertEquals(0.6f, s.filterEnvelopeAmount, 0f)
        assertEquals(-0.2f, s.pitchEnvelopeAmount, 0f)
        assertEquals(2.5f, s.lfo1.rateHz, 1e-4f)
        assertEquals(1f / 144f, s.ampEnvelope.attackStep, 1e-6f) // 3 ms a 48 kHz
    }
}
