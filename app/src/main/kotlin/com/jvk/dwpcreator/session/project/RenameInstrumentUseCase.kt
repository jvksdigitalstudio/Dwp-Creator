package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.dwp.DwpRenamer
import com.jvk.dwpcreator.dwp.DwpSampleCatalog
import com.jvk.dwpcreator.dwp.InstrumentNameValidator
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Renombra el instrumento cargado (nombre base de sus muestras y rutas del `.dwp`). */
class RenameInstrumentUseCase(
    private val session: ProjectSession,
    private val dispatchers: AppDispatchers
) {

    suspend operator fun invoke(newName: String) {
        if (session.isBusy()) return
        val current = session.project ?: return
        val loadedState = session.loadedState() ?: return
        val trimmed = newName.trim()

        InstrumentNameValidator.validate(trimmed)?.let { reason ->
            session.show(DwpUiState.Error("Nombre no válido: $reason", loadedState))
            return
        }

        val oldName = DwpSampleCatalog.detectInstrumentBaseName(current.document)
        if (oldName == null) {
            session.show(
                DwpUiState.Error(
                    "No se pudo determinar el nombre actual del instrumento: sus muestras no siguen el " +
                        "patrón «Nombre_Nota_Velocidad», así que no hay un nombre base que renombrar.",
                    loadedState
                )
            )
            return
        }
        if (trimmed == oldName) return

        try {
            val renamedDoc = withContext(dispatchers.default) {
                DwpRenamer.renameInstrument(current.document, oldName, trimmed)
            }
            val renamedProject = current.copy(document = renamedDoc)
            session.replaceProject(renamedProject)
            session.publish(renamedProject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            session.show(
                DwpUiState.Error("No se pudo renombrar el instrumento: ${UserFacingErrors.describe(e)}", loadedState)
            )
        }
    }
}
