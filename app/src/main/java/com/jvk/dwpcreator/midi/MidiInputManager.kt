package com.jvk.dwpcreator.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import java.io.IOException

/** A simplified MIDI Note On/Off event, decoded from the raw MIDI byte stream. */
data class MidiNoteEvent(val note: Int, val velocity: Int, val isNoteOn: Boolean)

/** A connectable MIDI device, as shown in the device-picker panel. */
data class MidiDeviceSummary(
    val id: Int,
    val name: String,
    val typeLabel: String
)

/**
 * Lists and connects to external MIDI devices (USB, or an already
 * system-paired Bluetooth MIDI device) via android.media.midi, and
 * delivers incoming Note On/Off messages as [MidiNoteEvent]s.
 *
 * Unlike auto-connecting to "whatever is first", [listDevices] lets the UI
 * show every detected controller by name so the person picks the right one
 * -- important as soon as more than one MIDI-capable device is around.
 */
class MidiInputManager(context: Context) {

    private val appContext = context.applicationContext
    private val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private var openDevice: MidiDevice? = null

    /** Called on the calling thread's Binder callback -- always hop to the main thread before touching UI state. */
    var onNoteEvent: ((MidiNoteEvent) -> Unit)? = null
    var onConnectionChanged: ((connected: Boolean, deviceName: String?) -> Unit)? = null

    val isMidiSupported: Boolean get() = midiManager != null

    /** Every MIDI device currently visible to the system, regardless of connection state. */
    fun listDevices(): List<MidiDeviceSummary> {
        val manager = midiManager ?: return emptyList()
        return manager.devices
            .filter { it.outputPortCount > 0 } // we only ever read from a device (act as a receiver)
            .map { it.toSummary() }
    }

    private fun MidiDeviceInfo.toSummary(): MidiDeviceSummary {
        val name = properties.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "Dispositivo MIDI #$id"
        val typeLabel = when (type) {
            MidiDeviceInfo.TYPE_USB -> "USB"
            MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth"
            MidiDeviceInfo.TYPE_VIRTUAL -> "Virtual"
            else -> "Desconocido"
        }
        return MidiDeviceSummary(id, name, typeLabel)
    }

    /** Connects to the device with the given [deviceId] (from [listDevices]), replacing any prior connection. */
    fun connectTo(deviceId: Int) {
        val manager = midiManager ?: run {
            onConnectionChanged?.invoke(false, null)
            return
        }
        val info = manager.devices.firstOrNull { it.id == deviceId }
        if (info == null || info.outputPortCount <= 0) {
            onConnectionChanged?.invoke(false, null)
            return
        }

        disconnect() // close any previous connection first

        manager.openDevice(info, { device ->
            if (device == null) {
                onConnectionChanged?.invoke(false, null)
                return@openDevice
            }
            val port = device.openOutputPort(0)
            if (port == null) {
                try {
                    device.close()
                } catch (ignored: IOException) {
                }
                onConnectionChanged?.invoke(false, null)
                return@openDevice
            }
            openDevice = device
            port.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    parseMidiBytes(msg, offset, count)
                }
            })
            onConnectionChanged?.invoke(true, info.toSummary().name)
        }, null)
    }

    /** Decodes raw MIDI 1.0 bytes, dispatching Note On (0x9n) / Note Off (0x8n) as [MidiNoteEvent]s. */
    private fun parseMidiBytes(msg: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val status = msg[i].toInt() and 0xFF
            val command = status and 0xF0
            if ((command == 0x90 || command == 0x80) && i + 2 < end) {
                val note = msg[i + 1].toInt() and 0x7F
                val velocity = msg[i + 2].toInt() and 0x7F
                val isNoteOn = command == 0x90 && velocity > 0
                onNoteEvent?.invoke(MidiNoteEvent(note, velocity, isNoteOn))
                i += 3
            } else {
                i += 1
            }
        }
    }

    fun disconnect() {
        try {
            openDevice?.close()
        } catch (ignored: IOException) {
        }
        openDevice = null
        onConnectionChanged?.invoke(false, null)
    }
}
