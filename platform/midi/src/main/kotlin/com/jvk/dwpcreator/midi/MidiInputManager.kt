package com.jvk.dwpcreator.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException

/**
 * Lists and connects to external MIDI devices (USB, or an already
 * system-paired Bluetooth MIDI device) via android.media.midi, and
 * delivers incoming Note On/Off messages as [MidiNoteEvent]s.
 *
 * Unlike auto-connecting to "whatever is first", [listDevices] lets the UI
 * show every detected controller by name so the person picks the right one
 * -- important as soon as more than one MIDI-capable device is around.
 */
class MidiInputManager(context: Context) : MidiInput {

    private val appContext = context.applicationContext
    private val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private var openDevice: MidiDevice? = null

    /**
     * Se incrementa en cada [disconnect] (y, por tanto, en cada [connectTo]).
     * `MidiManager.openDevice` es ASÍNCRONO: su callback llega después. Si en
     * ese intervalo el usuario desconecta o elige otro dispositivo, el callback
     * viejo NO debe conectarse (quedaría un dispositivo "fantasma" abierto y
     * sonando, o uno anterior sin cerrar). Cada callback compara la generación
     * con la que nació contra la vigente y, si no coincide, cierra el
     * dispositivo recién abierto y se retira.
     */
    private var connectionGeneration = 0

    /** Id del dispositivo actualmente conectado, o -1. Sirve para reconocerlo en [deviceCallback]. */
    private var connectedDeviceId = -1

    /** Aviso del sistema cuando un dispositivo MIDI desaparece (p. ej. se desenchufa el cable USB). Solo registrado mientras hay conexión. */
    private var deviceCallback: MidiManager.DeviceCallback? = null

    /** All raw MIDI byte-stream decoding is delegated to this pure Kotlin/JVM parser -- see its own KDoc for the supported subset. */
    private val messageParser = MidiMessageParser()

    /** Called on the calling thread's Binder callback -- always hop to the main thread before touching UI state. */
    override var onNoteEvent: ((MidiNoteEvent) -> Unit)? = null

    /** Rueda de pitch bend, normalizada a -1..1 (0 = centro). Se invoca desde el hilo del servicio MIDI. */
    override var onPitchBend: ((Float) -> Unit)? = null
    override var onConnectionChanged: ((connected: Boolean, deviceName: String?) -> Unit)? = null

    override val isMidiSupported: Boolean get() = midiManager != null

    /** Every MIDI device currently visible to the system, regardless of connection state. */
    override fun listDevices(): List<MidiDeviceSummary> {
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
    override fun connectTo(deviceId: Int) {
        val manager = midiManager ?: run {
            onConnectionChanged?.invoke(false, null)
            return
        }
        val info = manager.allDevices().firstOrNull { it.id == deviceId }
        if (info == null || info.outputPortCount <= 0) {
            onConnectionChanged?.invoke(false, null)
            return
        }

        disconnect() // close any previous connection first (also invalidates any pending open)
        messageParser.reset() // never carry MIDI running status across a device (re)connection
        val generation = connectionGeneration

        manager.openDevice(info, { device ->
            if (generation != connectionGeneration) {
                // Llegó tarde: entretanto se desconectó o se pidió otro dispositivo.
                try {
                    device?.close()
                } catch (ignored: IOException) {
                }
                return@openDevice
            }
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
            connectedDeviceId = info.id
            port.connect(object : MidiReceiver() {
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    messageParser.parse(
                        msg, offset, count,
                        onPitchBend = { raw -> onPitchBend?.invoke(raw / PITCH_BEND_HALF_RANGE) },
                        onEvent = { event -> onNoteEvent?.invoke(event) }
                    )
                }
            })
            // Sin esto, desenchufar el teclado dejaba la app mostrando "conectado"
            // para siempre (nadie avisaba del cambio). Se despacha en el hilo principal.
            val callback = object : MidiManager.DeviceCallback() {
                override fun onDeviceRemoved(removed: MidiDeviceInfo) {
                    if (removed.id == connectedDeviceId) disconnect()
                }
            }
            deviceCallback = callback
            manager.registerDeviceCallback(callback, Handler(Looper.getMainLooper()))
            onConnectionChanged?.invoke(true, info.toSummary().name)
        }, null)
    }

    override fun disconnect() {
        connectionGeneration++ // invalida cualquier openDevice todavía pendiente
        deviceCallback?.let { midiManager?.unregisterDeviceCallback(it) }
        deviceCallback = null
        connectedDeviceId = -1
        try {
            openDevice?.close()
        } catch (ignored: IOException) {
        }
        openDevice = null
        messageParser.reset()
        onConnectionChanged?.invoke(false, null)
    }
}

/** Medio recorrido de la rueda de pitch bend de 14 bits (centro = 0, extremos -8192 y +8191). */
private const val PITCH_BEND_HALF_RANGE = 8192f
