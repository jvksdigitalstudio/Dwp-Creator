package com.jvk.dwpcreator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jvk.dwpcreator.audio.SamplePlayer
import com.jvk.dwpcreator.domain.dwp.DwpEngine
import com.jvk.dwpcreator.domain.io.LoadedProject
import com.jvk.dwpcreator.domain.io.ZipProjectExporter
import com.jvk.dwpcreator.domain.io.ZipProjectLoader
import com.jvk.dwpcreator.midi.MidiDeviceSummary
import com.jvk.dwpcreator.midi.MidiInputManager
import com.jvk.dwpcreator.midi.MidiNoteEvent
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single owner of app state, connecting the pure-Kotlin, already-tested
 * domain layer (DwpEngine, ZipProjectLoader, ZipProjectExporter, WavDecoder)
 * and the native audio layer (SamplePlayer) to the UI.
 *
 * All heavy work (unzipping, tokenizing, re-zipping, audio decoding) runs
 * off the main thread via [Dispatchers.Default]/[Dispatchers.IO] inside
 * [viewModelScope], so the UI never freezes.
 */
class DwpCreatorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<DwpUiState>(DwpUiState.Empty)
    val uiState: StateFlow<DwpUiState> = _uiState.asStateFlow()

    private val _playingIndex = MutableStateFlow<Int?>(null)
    /** Index of the sample currently lighting up its piano key, for UI feedback. Null = nothing playing. */
    val playingIndex: StateFlow<Int?> = _playingIndex.asStateFlow()

    /** The real in-memory project. Null until something is successfully loaded. */
    private var project: LoadedProject? = null

    private val samplePlayer = SamplePlayer()

    private val _midiConnected = MutableStateFlow(false)
    val midiConnected: StateFlow<Boolean> = _midiConnected.asStateFlow()

    private val _midiConnectedDeviceName = MutableStateFlow<String?>(null)
    val midiConnectedDeviceName: StateFlow<String?> = _midiConnectedDeviceName.asStateFlow()

    private val _midiDevices = MutableStateFlow<List<MidiDeviceSummary>>(emptyList())
    val midiDevices: StateFlow<List<MidiDeviceSummary>> = _midiDevices.asStateFlow()

    private val midiInputManager: MidiInputManager by lazy {
        MidiInputManager(getApplication()).apply {
            onConnectionChanged = { connected, name ->
                _midiConnected.value = connected
                _midiConnectedDeviceName.value = name
            }
            onNoteEvent = { event -> if (event.isNoteOn) handleMidiNoteOn(event) }
        }
    }

    fun loadFromZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = DwpUiState.Importing(message = "Leyendo zip…")
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
                }

                _uiState.value = DwpUiState.Importing(message = "Emparejando muestras…")
                val loaded = withContext(Dispatchers.Default) { ZipProjectLoader.load(bytes) }

                project = loaded
                publishLoaded(loaded)
            } catch (e: ZipProjectLoader.ZipLoadException) {
                _uiState.value = DwpUiState.Error(e.message ?: "Error al leer el zip.")
            } catch (e: Exception) {
                _uiState.value = DwpUiState.Error("No se pudo cargar el archivo: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun renameAll(newName: String) {
        val current = project ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val oldName = DwpEngine.detectInstrumentBaseName(current.document) ?: return
        if (trimmed == oldName) return

        viewModelScope.launch {
            val renamedDoc = withContext(Dispatchers.Default) {
                DwpEngine.renameInstrument(current.document, oldName, trimmed)
            }
            val renamedProject = current.copy(document = renamedDoc)
            project = renamedProject
            publishLoaded(renamedProject)
        }
    }

    fun exportTo(uri: Uri) {
        val current = project ?: return
        val loadedState = _uiState.value as? DwpUiState.Loaded ?: return

        viewModelScope.launch {
            _uiState.value = loadedState.copy(isExporting = true)
            try {
                val zipBytes = withContext(Dispatchers.Default) {
                    ZipProjectExporter.export(current, loadedState.instrumentName)
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(zipBytes) }
                        ?: throw IllegalStateException("No se pudo escribir el archivo de salida.")
                }
                _uiState.value = loadedState.copy(isExporting = false)
            } catch (e: Exception) {
                _uiState.value = DwpUiState.Error(
                    message = "No se pudo exportar: ${e.message ?: e::class.simpleName}",
                    previous = loadedState.copy(isExporting = false)
                )
            }
        }
    }

    /**
     * Plays the sample at [index] (its position in the file, stable across
     * renames) through the native audio engine, and briefly flags it as
     * "playing" for the piano-key highlight in the UI.
     */
    fun previewSample(index: Int, velocity: Int = 127) {
        val current = project ?: return
        val audio = current.audioByIndex.getOrNull(index) ?: return

        viewModelScope.launch {
            _playingIndex.value = index
            withContext(Dispatchers.Default) { samplePlayer.play(audio, velocity) }
            delay(250) // visual flash only; the sample itself keeps playing on its own AudioTrack
            if (_playingIndex.value == index) _playingIndex.value = null
        }
    }

    /** Rescans for currently visible MIDI devices (call when opening the device panel). */
    fun refreshMidiDevices() {
        _midiDevices.value = midiInputManager.listDevices()
    }

    fun connectToMidiDevice(deviceId: Int) {
        midiInputManager.connectTo(deviceId)
    }

    fun disconnectMidi() {
        midiInputManager.disconnect()
    }

    /**
     * Matches an incoming MIDI note to the sample whose key range covers it
     * -- following the .dwp's own lowKey..highKey zone exactly as verified
     * in the binary audit (not a nearest-neighbor guess), so playback here
     * matches what DirectWave/FL Studio Mobile would actually trigger.
     */
    private fun handleMidiNoteOn(event: MidiNoteEvent) {
        val samples = (_uiState.value as? DwpUiState.Loaded)?.samples ?: return
        val match = samples.firstOrNull { event.note in it.lowKey..it.highKey } ?: return
        previewSample(match.index, event.velocity)
    }

    fun dismissError() {
        val current = _uiState.value
        if (current is DwpUiState.Error) {
            _uiState.value = current.previous
        }
    }

    private fun publishLoaded(loaded: LoadedProject) {
        val name = DwpEngine.detectInstrumentBaseName(loaded.document) ?: "Instrument"
        val samples = DwpEngine.listSamples(loaded.document)
        _uiState.value = DwpUiState.Loaded(instrumentName = name, samples = samples)
    }

    override fun onCleared() {
        super.onCleared()
        samplePlayer.releaseAll()
        if (_midiConnected.value) {
            midiInputManager.disconnect()
        }
    }
}
