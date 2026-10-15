package com.jvk.dwpcreator.midi

/** A connectable MIDI device, as shown in the device-picker panel. */
data class MidiDeviceSummary(
    val id: Int,
    val name: String,
    val typeLabel: String
)
