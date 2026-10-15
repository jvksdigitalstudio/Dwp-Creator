package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.DwpDocument
import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.ui.state.DwpUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSessionTest {

    private val session = ProjectSession()

    private fun emptyProject(audio: ByteArray = byteArrayOf(7)) = LoadedProject(
        document = DwpDocument(ByteArray(0), emptyList()),
        dwpZipEntryName = "Instrument/Instrument.dwp",
        audioByIndex = listOf(audio),
        originalZipEntryNames = listOf("Instrument/a.wav")
    )

    @Test
    fun startsEmptyAndIdle() {
        assertEquals(DwpUiState.Empty, session.uiState.value)
        assertFalse(session.isBusy())
        assertNull(session.project)
    }

    @Test
    fun importingIsBusy() {
        session.show(DwpUiState.Importing())

        assertTrue(session.isBusy())
    }

    @Test
    fun exportingIsBusyButAnIdleLoadedInstrumentIsNot() {
        session.show(DwpUiState.Loaded("X", emptyList()))
        assertFalse(session.isBusy())

        session.show(DwpUiState.Loaded("X", emptyList(), DwpUiState.ExportProgress(1, 2, "Preparando", "")))
        assertTrue(session.isBusy())
    }

    @Test
    fun anErrorDuringLoadDoesNotHideTheInstrumentThatWasAlreadyShown() {
        val loaded = DwpUiState.Loaded("X", emptyList())
        session.show(DwpUiState.Error("falló", loaded))

        assertEquals(loaded, session.stateBeforeLoad())

        session.dismissError()
        assertEquals(loaded, session.uiState.value)
    }

    @Test
    fun publishNamesTheInstrumentAndListsItsSamples() {
        val project = emptyProject()

        session.replaceProject(project)
        session.publish(project)

        val state = session.loadedState()!!
        assertEquals("Instrument", state.instrumentName)
        assertTrue(state.samples.isEmpty())
    }

    @Test
    fun audioIsOnlyAvailableForExistingSamplesOfTheLoadedProject() {
        assertNull(session.audioFor(0))

        session.replaceProject(emptyProject(audio = byteArrayOf(1, 2, 3)))

        assertArrayEquals(byteArrayOf(1, 2, 3), session.audioFor(0))
        assertNull(session.audioFor(1))
    }

    @Test
    fun samplesAreEmptyUnlessAnInstrumentIsLoaded() {
        assertTrue(session.samples().isEmpty())
    }
}
