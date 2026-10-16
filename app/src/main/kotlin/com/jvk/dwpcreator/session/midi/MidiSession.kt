package com.jvk.dwpcreator.session.midi

import com.jvk.dwpcreator.midi.MidiDeviceSummary
import com.jvk.dwpcreator.midi.MidiInput
import com.jvk.dwpcreator.sampler.api.SamplerPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Conexión con el teclado MIDI: lista de dispositivos, estado de la conexión y
 * enrutado de lo que llega hacia [router] (notas) y [playback] (pitch bend).
 *
 * La entrada MIDI se crea la primera vez que hace falta ([inputFactory]):
 * inicializar el servicio MIDI de Android tiene coste y la mayoría de las
 * sesiones nunca abren el selector.
 */
class MidiSession(
    private val inputFactory: () -> MidiInput,
    private val router: MidiNoteRouter,
    private val playback: SamplerPlayback
) {

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _devices = MutableStateFlow<List<MidiDeviceSummary>>(emptyList())
    val devices: StateFlow<List<MidiDeviceSummary>> = _devices.asStateFlow()

    // Se guarda el `Lazy` (no solo `by lazy`) para que [release] sepa si llegó a crearse.
    private val inputLazy = lazy {
        inputFactory().also { created ->
            created.onConnectionChanged = { isConnected, name ->
                _connected.value = isConnected
                _connectedDeviceName.value = name
                if (!isConnected) router.releaseHeldNotes()
            }
            created.onNoteEvent = router::onNoteEvent
            created.onPitchBend = playback::setPitchBend
        }
    }
    private val input: MidiInput get() = inputLazy.value

    fun refreshDevices() {
        _devices.value = input.listDevices()
    }

    fun connect(deviceId: Int) = input.connectTo(deviceId)

    fun disconnect() = input.disconnect()

    /** Cierra la conexión si llegó a abrirse; no crea la entrada solo para cerrarla. */
    fun release() {
        if (inputLazy.isInitialized()) input.disconnect()
    }
}
