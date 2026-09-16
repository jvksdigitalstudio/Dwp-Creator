package com.jvk.dwpcreator.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
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

    /** All raw MIDI byte-stream decoding is delegated to this pure Kotlin/JVM parser -- see its own KDoc for the supported subset. */
    private val messageParser = MidiMessageParser()

    /** Called on the calling thread's Binder callback -- always hop to the main thread before touching UI state. */
    var onNoteEvent: ((MidiNoteEvent) -> Unit)? = null
    var onConnectionChanged: ((connected: Boolean, deviceName: String?) -> Unit)? = null

    val isMidiSupported: Boolean get() = midiManager != null

    /** Every MIDI device currently visible to the system, regardless of connection state. */
    fun listDevices(): List<MidiDeviceSummary> {
        val manager = midiManager ?: return emptyList()
        return manager.allDevices()
            .filter { it.outputPortCount > 0 } // we only ever read from a device (act as a receiver)
            .map { it.toSummary() }
    }

    /**
     * [MidiManager.getDevices] was deprecated in API 30 in favor of
     * [MidiManager.getDevicesForTransport], which only exists from API 30 onward.
     * Our minSdk is 26, so below API 30 the deprecated call is the only API that
     * exists at all -- there is no forward-compatible replacement to fall back to,
     * so the suppression here is deliberate and version-gated, not a blanket
     * silence of the warning.
     */
    private fun MidiManager.allDevices(): List<MidiDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            devices.toList()
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
        val info = manager.allDevices().firstOrNull { it.id == deviceId }
        if (info == null || info.outputPortCount <= 0) {
            onConnectionChanged?.invoke(false, null)
            return
        }

        disconnect() // close any previous connection first
        messageParser.reset() // never carry MIDI running status across a device (re)connection

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
                    messageParser.parse(msg, offset, count) { event -> onNoteEvent?.invoke(event) }
                }
            })
            onConnectionChanged?.invoke(true, info.toSummary().name)
        }, null)
    }

    fun disconnect() {
        try {
            openDevice?.close()
        } catch (ignored: IOException) {
        }
        openDevice = null
        messageParser.reset()
        onConnectionChanged?.invoke(false, null)
    }
}
