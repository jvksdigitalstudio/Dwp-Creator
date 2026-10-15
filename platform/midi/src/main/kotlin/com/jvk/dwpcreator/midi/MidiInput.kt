package com.jvk.dwpcreator.midi

/**
 * Entrada MIDI desde el punto de vista de la aplicación: lista dispositivos,
 * se conecta a uno y entrega eventos ya decodificados.
 *
 * La implementación de Android es [MidiInputManager]; la capa de aplicación
 * solo conoce este contrato, lo que permite sustituirla en pruebas.
 */
interface MidiInput {

    /** Se invoca por cada Note On / Note Off recibido. Puede llegar desde un hilo de MIDI. */
    var onNoteEvent: ((MidiNoteEvent) -> Unit)?

    /** Se invoca con la rueda de pitch bend normalizada a -1..1. */
    var onPitchBend: ((Float) -> Unit)?

    /** Se invoca al conectarse o desconectarse un dispositivo (nombre solo si está conectado). */
    var onConnectionChanged: ((connected: Boolean, deviceName: String?) -> Unit)?

    /** `false` si el sistema no ofrece el servicio MIDI. */
    val isMidiSupported: Boolean

    /** Dispositivos de los que se puede leer, tal como los muestra el selector. */
    fun listDevices(): List<MidiDeviceSummary>

    /** Abre el dispositivo [deviceId] y empieza a escucharlo. */
    fun connectTo(deviceId: Int)

    /** Cierra la conexión actual, si la hay. */
    fun disconnect()
}
