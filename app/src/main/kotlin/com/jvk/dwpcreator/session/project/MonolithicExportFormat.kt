package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.dwp.monolithic.MonolithicDwpProjectBuilder
import com.jvk.dwpcreator.ui.state.DwpUiState

/** Exporta un único `.dwp` monolítico con el audio embebido en FLAC. */
object MonolithicExportFormat : ExportFormat {

    override val errorPrefix = "No se pudo exportar el .dwp monolítico"

    override val announcesWriteStage = false

    override fun prepare(
        project: LoadedProject,
        instrumentName: String,
        onProgress: (DwpUiState.ExportProgress) -> Unit
    ): PreparedExport {
        val dwpBytes = MonolithicDwpProjectBuilder.buildBytes(project) { done, total, sampleName ->
            onProgress(DwpUiState.ExportProgress(done, total, "Codificando FLAC", sampleName))
        }
        return PreparedExport { output -> output.use { it.write(dwpBytes) } }
    }
}
