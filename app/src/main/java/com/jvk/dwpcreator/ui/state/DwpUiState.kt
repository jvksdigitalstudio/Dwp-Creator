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
        val isExporting: Boolean = false
    ) : DwpUiState()

    /** Something failed (bad zip, missing samples, corrupt dwp...); message is user-facing Spanish text. */
    data class Error(val message: String, val previous: DwpUiState = Empty) : DwpUiState()
}
