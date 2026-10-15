package com.jvk.dwpcreator.session.playback

import com.jvk.dwpcreator.ui.state.SampleMixerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerControllerTest {

    private val mixer = MixerController()

    @Test
    fun untouchedSamplesUseFactorySettingsAndAreAudible() {
        assertEquals(SampleMixerState.DEFAULT, mixer.stateFor(3))
        assertTrue(mixer.isAudible(3))
    }

    @Test
    fun muteSilencesOnlyThatSample() {
        mixer.toggleMute(1)

        assertFalse(mixer.isAudible(1))
        assertTrue(mixer.isAudible(2))
    }

    @Test
    fun soloLimitsPlaybackToSoloedSamples() {
        mixer.toggleSolo(1)

        assertTrue(mixer.isAudible(1))
        assertFalse(mixer.isAudible(2))
    }

    @Test
    fun soloWinsOverMuteOnTheSameSample() {
        mixer.toggleMute(1)
        mixer.toggleSolo(1)

        assertTrue(mixer.isAudible(1))
    }

    @Test
    fun panAndVolumeAreClampedToTheirRanges() {
        mixer.setPan(1, -5f)
        mixer.setVolume(1, 9f)
        assertEquals(-1f, mixer.stateFor(1).pan, 0f)
        assertEquals(1f, mixer.stateFor(1).volume, 0f)

        mixer.setPan(1, 5f)
        mixer.setVolume(1, -9f)
        assertEquals(1f, mixer.stateFor(1).pan, 0f)
        assertEquals(0f, mixer.stateFor(1).volume, 0f)
    }

    @Test
    fun returningToFactorySettingsDropsTheEntry() {
        mixer.toggleMute(1)
        mixer.toggleMute(1)

        assertTrue(mixer.states.value.isEmpty())
    }

    @Test
    fun resetClearsEverySample() {
        mixer.toggleMute(1)
        mixer.setPan(2, 0.5f)

        mixer.reset()

        assertTrue(mixer.states.value.isEmpty())
        assertTrue(mixer.isAudible(1))
    }
}
