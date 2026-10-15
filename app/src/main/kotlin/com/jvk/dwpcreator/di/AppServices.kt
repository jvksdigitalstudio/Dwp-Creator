package com.jvk.dwpcreator.di

import com.jvk.dwpcreator.midi.MidiInput
import com.jvk.dwpcreator.sampler.api.FxStateStore
import com.jvk.dwpcreator.sampler.api.SamplerPlayback
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.session.project.FileStreams

/**
 * Dependencias externas que necesita la sesión, expresadas como contratos.
 *
 * Las crea la raíz de composición (`AppContainer`); la sesión no sabe si son
 * las reales de Android o dobles de prueba.
 *
 * @property midiInputFactory crea la entrada MIDI bajo demanda: inicializar el
 *   servicio MIDI de Android tiene coste, así que solo ocurre si el usuario abre
 *   el selector de dispositivos.
 */
class AppServices(
    val playback: SamplerPlayback,
    val fxStore: FxStateStore,
    val fileStreams: FileStreams,
    val midiInputFactory: () -> MidiInput,
    val dispatchers: AppDispatchers = AppDispatchers()
)
