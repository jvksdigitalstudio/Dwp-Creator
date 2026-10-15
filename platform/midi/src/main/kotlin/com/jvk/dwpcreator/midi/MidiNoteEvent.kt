package com.jvk.dwpcreator.midi

/** A simplified MIDI Note On/Off event, decoded from the raw MIDI byte stream. */
data class MidiNoteEvent(val note: Int, val velocity: Int, val isNoteOn: Boolean)
