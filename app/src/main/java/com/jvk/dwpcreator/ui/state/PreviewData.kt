package com.jvk.dwpcreator.ui.state

import com.jvk.dwpcreator.domain.dwp.SampleInfo

/**
 * Generates a fake but structurally-accurate sample list -- same note
 * range (C3-B6, MIDI 36-83), same key-range extension pattern (rootKey=0
 * on the first, highKey=127 on the last) verified against the real
 * Instrument.dwp -- purely so the UI can be visually validated before the
 * real load/export wiring lands in the next step.
 */
object PreviewData {

    private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun fakeInstrument(name: String = "Instrument", firstMidi: Int = 36, lastMidi: Int = 83): List<SampleInfo> {
        val notes = (firstMidi..lastMidi).map { midi ->
            NOTE_NAMES[midi % 12] + (midi / 12)
        }
        return notes.mapIndexed { i, note ->
            val midi = firstMidi + i
            val isFirst = i == 0
            val isLast = i == notes.lastIndex
            SampleInfo(
                index = i,
                name = "${name}_${note}_127",
                note = note,
                velocity = 127,
                lowKey = midi,
                rootKey = if (isFirst) 0 else midi,
                highKey = if (isLast) 127 else midi,
                frameCount = 378_000,
                dwpPath = "D:\\$name\\${name}_${note}_127.wav"
            )
        }
    }
}
