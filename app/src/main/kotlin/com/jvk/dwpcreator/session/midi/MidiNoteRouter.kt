package com.jvk.dwpcreator.session.midi

import com.jvk.dwpcreator.dwp.SampleZoneResolver
import com.jvk.dwpcreator.midi.MidiNoteEvent
import com.jvk.dwpcreator.session.playback.NotePlayer
import com.jvk.dwpcreator.session.project.LoadedSamples
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Traduce los eventos Note On / Note Off del teclado MIDI en notas del
 * instrumento cargado.
 *
 * Los eventos llegan desde un hilo de MIDI; cada uno salta al hilo principal
 * ([scope]) antes de tocar el estado, que solo se usa desde allí.
 */
class MidiNoteRouter(
    private val scope: CoroutineScope,
    private val samples: LoadedSamples,
    private val notes: NotePlayer
) {

    /**
     * Nota MIDI física (0-127) -> índice de muestra disparado por ella, mientras
     * esa tecla siga apretada. Hace falta porque un Note Off solo trae el número
     * de nota: volver a buscar la muestra por rango en ese momento supondría que
     * el mapeo no cambió entre el Note On y el Note Off (frágil si se recarga el
     * instrumento con la tecla aún apretada). Guardar el índice realmente usado
     * es exacto en cualquier caso.
     */
    private val activeNoteIndices = mutableMapOf<Int, Int>()

    fun onNoteEvent(event: MidiNoteEvent) {
        if (event.isNoteOn) noteOn(event) else noteOff(event)
    }

    /** Apaga todas las notas que el teclado mantenía apretadas (p. ej. al desconectarlo). */
    fun releaseHeldNotes() {
        activeNoteIndices.values.toList().forEach(notes::noteOff)
        activeNoteIndices.clear()
    }

    /** Olvida las notas mantenidas sin apagarlas (el motor ya se reinició). */
    fun reset() {
        activeNoteIndices.clear()
    }

    private fun noteOn(event: MidiNoteEvent) {
        scope.launch {
            val match = SampleZoneResolver.resolve(samples.samples(), event.note) ?: return@launch
            activeNoteIndices[event.note] = match.index
            notes.noteOn(match.index, event.velocity, key = event.note)
        }
    }

    private fun noteOff(event: MidiNoteEvent) {
        scope.launch {
            val index = activeNoteIndices.remove(event.note) ?: return@launch
            notes.noteOff(index)
        }
    }
}
