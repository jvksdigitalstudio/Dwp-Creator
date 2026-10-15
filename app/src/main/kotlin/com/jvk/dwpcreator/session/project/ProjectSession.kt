package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.DwpSampleCatalog
import com.jvk.dwpcreator.dwp.SampleInfo
import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dueño del estado del proyecto: el [LoadedProject] real en memoria y el
 * [DwpUiState] que se publica a la UI.
 *
 * Es solo un contenedor de estado; las operaciones (cargar, renombrar,
 * exportar) viven en sus casos de uso y lo modifican a través de esta clase.
 * Se accede únicamente desde el hilo principal.
 */
class ProjectSession : LoadedSamples {

    private val _uiState = MutableStateFlow<DwpUiState>(DwpUiState.Empty)
    val uiState: StateFlow<DwpUiState> = _uiState.asStateFlow()

    /** El proyecto real en memoria. `null` hasta que algo se carga con éxito. */
    var project: LoadedProject? = null
        private set

    /** `true` mientras se importa o se exporta: durante ese tiempo no se admiten más operaciones. */
    fun isBusy(): Boolean = when (val current = _uiState.value) {
        is DwpUiState.Importing -> true
        is DwpUiState.Loaded -> current.exportProgress != null
        else -> false
    }

    /** El estado `Loaded` actual, o `null` si no hay instrumento cargado. */
    fun loadedState(): DwpUiState.Loaded? = _uiState.value as? DwpUiState.Loaded

    /**
     * Estado al que volver si una carga falla: el actual, o el que había antes
     * si ya se estaba mostrando un error (así un error no tapa al instrumento).
     */
    fun stateBeforeLoad(): DwpUiState = _uiState.value.let { if (it is DwpUiState.Error) it.previous else it }

    /** Publica [state] a la UI. */
    fun show(state: DwpUiState) {
        _uiState.value = state
    }

    /** Sustituye el proyecto en memoria (no publica nada). */
    fun replaceProject(loaded: LoadedProject) {
        project = loaded
    }

    /** Publica [loaded] como estado `Loaded`, con el nombre base y las muestras del documento. */
    fun publish(loaded: LoadedProject) {
        val name = DwpSampleCatalog.detectInstrumentBaseName(loaded.document) ?: "Instrument"
        val samples = DwpSampleCatalog.listSamples(loaded.document)
        _uiState.value = DwpUiState.Loaded(instrumentName = name, samples = samples)
    }

    /** Cierra el error mostrado y vuelve al estado anterior. */
    fun dismissError() {
        val current = _uiState.value
        if (current is DwpUiState.Error) _uiState.value = current.previous
    }

    override fun audioFor(index: Int): ByteArray? = project?.audioByIndex?.getOrNull(index)

    override fun samples(): List<SampleInfo> = loadedState()?.samples.orEmpty()
}
