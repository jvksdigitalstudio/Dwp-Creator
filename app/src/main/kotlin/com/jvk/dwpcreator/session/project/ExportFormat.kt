package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.ui.state.DwpUiState

/** Un formato de exportación del instrumento (estrategia de [ExportProjectUseCase]). */
interface ExportFormat {

    /** Prefijo del mensaje que se muestra si la exportación falla. */
    val errorPrefix: String

    /** Si, antes de volcar al destino, se anuncia la etapa «Escribiendo archivo». */
    val announcesWriteStage: Boolean

    /**
     * Calcula y valida la exportación. Trabajo de CPU: se llama fuera del hilo
     * principal. [onProgress] puede invocarse tantas veces como muestras haya.
     */
    fun prepare(
        project: LoadedProject,
        instrumentName: String,
        onProgress: (DwpUiState.ExportProgress) -> Unit
    ): PreparedExport
}
