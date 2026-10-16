package com.jvk.dwpcreator.session.playback

import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.session.testing.FakeLoadedSamples
import com.jvk.dwpcreator.session.testing.FakePlayback
import com.jvk.dwpcreator.session.testing.sampleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePlayerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)
    private val playback = FakePlayback()
    private val mixer = MixerController()
    private val loaded = FakeLoadedSamples(
        audio = mapOf(0 to byteArrayOf(1), 1 to byteArrayOf(2), 2 to byteArrayOf(3)),
        samples = listOf(
            sampleInfo(index = 0, lowKey = 0, rootKey = 60, highKey = 61),
            sampleInfo(index = 1, lowKey = 62, rootKey = 62, highKey = 127),
            sampleInfo(index = 2, lowKey = 0, rootKey = 300, highKey = 127)
        )
    )
    private val notes = NotePlayer(scope, dispatchers, playback, loaded, mixer)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun noteOnPlaysAtTheSampleRootKeyWithMixerSettingsAndLightsTheKey() {
        mixer.setVolume(0, 0.5f)
        mixer.setPan(0, -1f)

        notes.noteOn(0, velocity = 100)

        assertEquals(listOf(FakePlayback.Play(0, 100, 0.5f, -1f, key = 60, rootKey = 60)), playback.plays)
        assertEquals(setOf(0), notes.playingIndices.value)
    }

    @Test
    fun explicitKeyIsPlayedInsteadOfTheRootKey() {
        notes.noteOn(0, velocity = 127, key = 64)

        assertEquals(FakePlayback.Play(0, 127, 1f, 0f, key = 64, rootKey = 60), playback.plays.single())
    }

    @Test
    fun anOutOfRangeRootKeyIsReportedAsUnknown() {
        notes.noteOn(2)

        assertEquals(-1, playback.plays.single().rootKey)
        assertEquals(-1, playback.plays.single().key)
    }

    @Test
    fun aMutedSampleStaysSilentButItsKeyStillLights() {
        mixer.toggleMute(0)

        notes.noteOn(0)

        assertTrue(playback.plays.isEmpty())
        assertEquals(setOf(0), notes.playingIndices.value)
    }

    @Test
    fun soloPlaysOnlyTheSoloedSample() {
        mixer.toggleSolo(1)

        notes.noteOn(0)
        notes.noteOn(1)

        assertEquals(listOf(1), playback.plays.map { it.index })
    }

    @Test
    fun anUnknownSampleDoesNothing() {
        notes.noteOn(9)

        assertTrue(playback.plays.isEmpty())
        assertTrue(notes.playingIndices.value.isEmpty())
    }

    @Test
    fun noteOffReleasesTheVoiceAndTurnsTheKeyOff() {
        notes.noteOn(0)

        notes.noteOff(0)

        assertEquals(listOf(0), playback.stops)
        assertTrue(notes.playingIndices.value.isEmpty())
    }

    @Test
    fun chordsKeepSeveralKeysLit() {
        notes.noteOn(0)
        notes.noteOn(1)

        assertEquals(setOf(0, 1), notes.playingIndices.value)
    }

    @Test
    fun resetTurnsEveryKeyOff() {
        notes.noteOn(0)
        notes.noteOn(1)

        notes.reset()

        assertTrue(notes.playingIndices.value.isEmpty())
    }
}
