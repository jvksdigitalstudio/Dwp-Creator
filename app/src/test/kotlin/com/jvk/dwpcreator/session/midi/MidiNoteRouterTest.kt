package com.jvk.dwpcreator.session.midi

import com.jvk.dwpcreator.midi.MidiNoteEvent
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.session.playback.MixerController
import com.jvk.dwpcreator.session.playback.NotePlayer
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

class MidiNoteRouterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val dispatchers = AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)
    private val playback = FakePlayback()
    private val loaded = FakeLoadedSamples(
        audio = mapOf(0 to byteArrayOf(1), 1 to byteArrayOf(2)),
        samples = listOf(
            sampleInfo(index = 0, lowKey = 48, rootKey = 50, highKey = 61),
            sampleInfo(index = 1, lowKey = 62, rootKey = 62, highKey = 72)
        )
    )
    private val notes = NotePlayer(scope, dispatchers, playback, loaded, MixerController())
    private val router = MidiNoteRouter(scope, loaded, notes)

    @After
    fun tearDown() = scope.cancel()

    private fun on(note: Int, velocity: Int = 100) = router.onNoteEvent(MidiNoteEvent(note, velocity, isNoteOn = true))

    private fun off(note: Int) = router.onNoteEvent(MidiNoteEvent(note, 0, isNoteOn = false))

    @Test
    fun noteOnPlaysTheSampleWhoseRangeContainsTheNote() {
        on(55, velocity = 90)

        val play = playback.plays.single()
        assertEquals(0, play.index)
        assertEquals(90, play.velocity)
        assertEquals(55, play.key)
        assertEquals(50, play.rootKey)
    }

    @Test
    fun rangeBoundariesAreInclusive() {
        on(61)
        on(62)

        assertEquals(listOf(0, 1), playback.plays.map { it.index })
    }

    @Test
    fun aNoteOutsideEveryRangeIsIgnored() {
        on(20)

        assertTrue(playback.plays.isEmpty())
    }

    @Test
    fun noteOffStopsTheSampleUsedByNoteOnEvenIfTheMappingChangedMeanwhile() {
        on(65)
        loaded.samples = emptyList()

        off(65)

        assertEquals(listOf(1), playback.stops)
    }

    @Test
    fun aNoteOffForANoteThatNeverStartedDoesNothing() {
        off(65)

        assertTrue(playback.stops.isEmpty())
    }

    @Test
    fun releaseHeldNotesStopsEveryKeyStillDown() {
        on(55)
        on(65)

        router.releaseHeldNotes()
        off(55)

        assertEquals(listOf(0, 1), playback.stops.sorted())
    }
}
