package com.jvk.dwpcreator.session.playback

import com.jvk.dwpcreator.sampler.api.SamplerPlayback
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.session.project.LoadedSamples
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dispara y apaga notas sobre el motor y lleva la cuenta de qué teclas están
 * encendidas en la UI.
 *
 * Solo se usa desde el hilo principal (el overlay táctil y [MidiNoteRouter][com.jvk.dwpcreator.session.midi.MidiNoteRouter]
 * saltan a él antes de llamar), por eso no necesita sincronización propia.
 */
class NotePlayer(
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val playback: SamplerPlayback,
    private val samples: LoadedSamples,
    private val mixer: MixerController
) {

    private val _playingIndices = MutableStateFlow<Set<Int>>(emptySet())

    /**
     * Índices de todas las teclas encendidas ahora mismo. Es un `Set` y no un
     * único `Int?` porque un teclado real admite acordes (varios dedos) y
     * glissando: en ambos casos hay más de una tecla resaltada a la vez.
     */
    val playingIndices: StateFlow<Set<Int>> = _playingIndices.asStateFlow()

    /**
     * Un auto-apagado por índice, usado **solo** por [preview] (un toque simple
     * sin gesto de «soltar»), para que dos teclas resaltadas a la vez no se
     * pisen los temporizadores. El camino de «tecla mantenida» ([noteOn] /
     * [noteOff]) no programa temporizadores: la luz se apaga únicamente con [noteOff].
     */
    private val highlightJobs = mutableMapOf<Int, Job>()

    /**
     * Enciende la tecla [index] y, si el mezclador la deja sonar, dispara su muestra.
     *
     * @param key nota MIDI tocada; si es `null` se toca a la altura raíz de la muestra.
     */
    fun noteOn(index: Int, velocity: Int = 127, key: Int? = null) {
        val audio = samples.audioFor(index) ?: return

        if (mixer.isAudible(index)) {
            val mix = mixer.stateFor(index)
            val rootKey = rootKeyOf(index)
            val playedKey = key ?: rootKey
            // Decodificar y disparar fuera del hilo principal: la primera nota de una muestra
            // paga su decodificación y no debe congelar la UI.
            scope.launch(dispatchers.default) {
                playback.play(
                    index, audio, velocity,
                    volume = mix.volume, pan = mix.pan,
                    key = playedKey, rootKey = rootKey
                )
            }
        }

        highlightJobs.remove(index)?.cancel()
        _playingIndices.value = _playingIndices.value + index
    }

    /** Apaga la tecla [index] y libera su voz con el fundido de liberación del motor. */
    fun noteOff(index: Int) {
        highlightJobs.remove(index)?.cancel()
        _playingIndices.value = _playingIndices.value - index
        playback.stopNote(index)
    }

    /** Toque simple: dispara la muestra y apaga la tecla sola tras [KEY_HIGHLIGHT_MS]. */
    fun preview(index: Int, velocity: Int = 127) {
        noteOn(index, velocity)
        highlightJobs[index] = scope.launch {
            delay(KEY_HIGHLIGHT_MS)
            noteOff(index)
        }
    }

    /** Apaga todas las luces y cancela los auto-apagados pendientes. */
    fun reset() {
        highlightJobs.values.forEach { it.cancel() }
        highlightJobs.clear()
        _playingIndices.value = emptySet()
    }

    /** Tecla raíz (0..127) de la muestra [index], o -1 si se desconoce. */
    private fun rootKeyOf(index: Int): Int {
        val root = samples.samples().firstOrNull { it.index == index }?.rootKey ?: return -1
        return if (root in 0..127) root else -1
    }

    private companion object {
        /** Cuánto se queda encendida una tecla tras un [preview]. */
        const val KEY_HIGHLIGHT_MS = 250L
    }
}
