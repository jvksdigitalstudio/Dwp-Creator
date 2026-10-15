package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.dwp.io.ZipProjectExporter
import com.jvk.dwpcreator.ui.state.DwpUiState

/** Exporta un `.zip` con el `.dwp` y un `.wav` por muestra (formato estándar de DirectWave). */
object ZipExportFormat : ExportFormat {

    override val errorPrefix = "No se pudo exportar"

    override val announcesWriteStage = true

    override fun prepare(
        project: LoadedProject,
        instrumentName: String,
        onProgress: (DwpUiState.ExportProgress) -> Unit
    ): PreparedExport {
        val prepared = ZipProjectExporter.prepare(project, instrumentName) { done, total, sampleName ->
            onProgress(DwpUiState.ExportProgress(done, total, "Empaquetando", sampleName))
        }
        return PreparedExport { output -> prepared.writeTo(output) }
    }
}
