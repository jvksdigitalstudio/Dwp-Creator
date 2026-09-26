package com.jvk.dwpcreator.ui.state

import com.jvk.dwpcreator.domain.dwp.SampleInfo

/**
 * The single source of truth for what the main screen shows. Deliberately
 * a sealed class (not a bag of booleans) so every screen state is
 * exhaustive and explicit -- no "impossible" combinations like
 * "loading AND loaded AND error" at once.
 */
sealed class DwpUiState {

    /** Nothing loaded yet -- shows the LOAD SAMPLES screen. */
    data object Empty : DwpUiState()

    /** A zip is being read/parsed/matched. [progress] is 0f..1f, null = indeterminate. */
    data class Importing(val progress: Float? = null, val message: String = "Importando…") : DwpUiState()

    /** A project is loaded and ready to browse/edit/export. */
    data class Loaded(
        val instrumentName: String,
        val samples: List<SampleInfo>,
        val exportProgress: ExportProgress? = null
    ) : DwpUiState()

    /**
     * Progreso real (no simulado) de una exportación en curso: [current] es
     * la muestra que se acaba de terminar de procesar (1-based), [total] el
     * número total de muestras del proyecto, y [stageLabel] describe qué se
     * está haciendo con ella ("Empaquetando ZIP" / "Codificando FLAC") para
     * que el overlay de progreso pueda mostrar algo con sentido en vez de un
     * spinner indefinido -- ver `ExportProgressOverlay`.
     */
    data class ExportProgress(
        val current: Int,
        val total: Int,
        val stageLabel: String,
        val sampleName: String
    ) {
        val fraction: Float get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Something failed (bad zip, missing samples, corrupt dwp...); message is user-facing Spanish text. */
    data class Error(val message: String, val previous: DwpUiState = Empty) : DwpUiState()
}
