package com.jvk.dwpcreator.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvk.dwpcreator.di.AppServices
import com.jvk.dwpcreator.midi.MidiDeviceSummary
import com.jvk.dwpcreator.sampler.api.AudioStats
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.SamplerParam
import com.jvk.dwpcreator.sampler.api.params.SamplerToggle
import com.jvk.dwpcreator.sampler.api.preset.FxPreset
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.session.fx.FxController
import com.jvk.dwpcreator.session.midi.MidiNoteRouter
import com.jvk.dwpcreator.session.midi.MidiSession
import com.jvk.dwpcreator.session.playback.MixerController
import com.jvk.dwpcreator.session.playback.NotePlayer
import com.jvk.dwpcreator.session.project.ExportProjectUseCase
import com.jvk.dwpcreator.session.project.LoadProjectUseCase
import com.jvk.dwpcreator.session.project.MonolithicExportFormat
import com.jvk.dwpcreator.session.project.ProjectSession
import com.jvk.dwpcreator.session.project.RenameInstrumentUseCase
import com.jvk.dwpcreator.session.project.ZipExportFormat
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.state.SampleMixerState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Fachada de presentación: compone los controladores de la sesión y expone a la
 * UI su estado y sus acciones. No contiene lógica propia; cada operación se
 * delega en el controlador o caso de uso que es responsable de ella.
 *
 * | Responsabilidad            | Colaborador                        |
 * |----------------------------|------------------------------------|
 * | Estado del proyecto        | [ProjectSession]                   |
 * | Cargar / renombrar / exportar | casos de uso de `session.project` |
 * | Tocar notas                | [NotePlayer]                       |
 * | Mini-mezclador             | [MixerController]                  |
 * | Voz y efectos              | [FxController]                     |
 * | Teclado MIDI               | [MidiSession] + [MidiNoteRouter]   |
 */
class DwpCreatorViewModel(services: AppServices) : ViewModel() {

    private val dispatchers = services.dispatchers
    private val playback = services.playback

    private val project = ProjectSession()
    private val mixer = MixerController()
    private val notes = NotePlayer(viewModelScope, dispatchers, playback, project, mixer)
    private val fx = FxController(viewModelScope, playback, services.fxStore)
    private val midiRouter = MidiNoteRouter(viewModelScope, project, notes)
    private val midi = MidiSession(services.midiInputFactory, midiRouter, playback)

    private val loadProject = LoadProjectUseCase(
        session = project,
        streams = services.fileStreams,
        dispatchers = dispatchers,
        onProjectReplaced = { resetPlaybackForNewProject() }
    )
    private val renameInstrument = RenameInstrumentUseCase(project, dispatchers)
    private val exportProject = ExportProjectUseCase(project, services.fileStreams, dispatchers)

    val uiState: StateFlow<DwpUiState> = project.uiState
    val playingIndices: StateFlow<Set<Int>> = notes.playingIndices
    val mixerStates: StateFlow<Map<Int, SampleMixerState>> = mixer.states
    val fxState: StateFlow<SamplerFxState> = fx.state
    val midiConnected: StateFlow<Boolean> = midi.connected
    val midiConnectedDeviceName: StateFlow<String?> = midi.connectedDeviceName
    val midiDevices: StateFlow<List<MidiDeviceSummary>> = midi.devices

    // ---- Proyecto

    /** Carga un instrumento desde un `.zip` y, si va bien, calienta la caché de audio en segundo plano. */
    fun loadFromZip(uri: Uri) {
        viewModelScope.launch {
            val loaded = loadProject(uri) ?: return@launch
            launch(dispatchers.default) { playback.prewarm(loaded.audioByIndex) }
        }
    }

    fun renameAll(newName: String) {
        viewModelScope.launch { renameInstrument(newName) }
    }

    /** Exporta un `.zip` con el `.dwp` y un `.wav` por muestra. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch { exportProject(uri, ZipExportFormat) }
    }

    /** Exporta un único `.dwp` monolítico con el audio embebido en FLAC. */
    fun exportMonolithicTo(uri: Uri) {
        viewModelScope.launch { exportProject(uri, MonolithicExportFormat) }
    }

    fun dismissError() = project.dismissError()

    // ---- Notas

    fun noteOn(index: Int, velocity: Int = 127, key: Int? = null) = notes.noteOn(index, velocity, key)

    fun noteOff(index: Int) = notes.noteOff(index)

    fun previewSample(index: Int, velocity: Int = 127) = notes.preview(index, velocity)

    // ---- Mezclador

    fun mixerStateFor(index: Int): SampleMixerState = mixer.stateFor(index)

    fun toggleMute(index: Int) = mixer.toggleMute(index)

    fun toggleSolo(index: Int) = mixer.toggleSolo(index)

    fun setPan(index: Int, pan: Float) = mixer.setPan(index, pan)

    fun setVolume(index: Int, volume: Float) = mixer.setVolume(index, volume)

    // ---- Voz y efectos

    fun setParam(param: SamplerParam, value: Float) = fx.setParam(param, value)

    fun resetParam(param: SamplerParam) = fx.resetParam(param)

    fun setToggle(toggle: SamplerToggle, enabled: Boolean) = fx.setToggle(toggle, enabled)

    fun applyFxPreset(module: FxModule, preset: FxPreset) = fx.applyPreset(module, preset)

    fun resetFxModule(module: FxModule) = fx.resetModule(module)

    fun readAudioStats(): AudioStats = playback.audioStats()

    fun readOutputPeaks(): Pair<Float, Float> = playback.outputPeaks()

    // ---- MIDI

    fun refreshMidiDevices() = midi.refreshDevices()

    fun connectToMidiDevice(deviceId: Int) = midi.connect(deviceId)

    fun disconnectMidi() = midi.disconnect()

    /**
     * Prepara la reproducción para un instrumento nuevo: apaga luces y notas
     * MIDI mantenidas, vacía el mezclador y reinicia el motor. Los índices de
     * muestra se reutilizan entre instrumentos, así que nada del anterior debe sobrevivir.
     */
    private fun resetPlaybackForNewProject() {
        notes.reset()
        midiRouter.reset()
        mixer.reset()
        playback.resetForNewProject()
    }

    override fun onCleared() {
        super.onCleared()
        midi.release()
        fx.flush()
        playback.releaseAll()
    }
}
