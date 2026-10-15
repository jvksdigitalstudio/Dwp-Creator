package com.jvk.dwpcreator.session.project

import android.net.Uri
import com.jvk.dwpcreator.dwp.io.LoadedProject
import com.jvk.dwpcreator.dwp.io.ZipLoadException
import com.jvk.dwpcreator.dwp.io.ZipProjectLoader
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Carga un instrumento desde un `.zip` (un `.dwp` más sus `.wav`).
 *
 * @param onProjectReplaced se invoca con el proyecto nuevo **antes** de publicarlo:
 *   es el momento de apagar notas y vaciar mezclador y caché, porque los índices de
 *   muestra se reutilizan entre instrumentos.
 */
class LoadProjectUseCase(
    private val session: ProjectSession,
    private val streams: FileStreams,
    private val dispatchers: AppDispatchers,
    private val onProjectReplaced: (LoadedProject) -> Unit
) {

    /**
     * @return el proyecto cargado, o `null` si había una operación en curso o la carga
     *   falló (en ese caso el error ya está publicado en [ProjectSession.uiState]).
     */
    suspend operator fun invoke(uri: Uri): LoadedProject? {
        if (session.isBusy()) return null
        val stateBeforeLoad = session.stateBeforeLoad()

        session.show(DwpUiState.Importing(message = "Leyendo y emparejando muestras…"))
        return try {
            val loaded = withContext(dispatchers.io) {
                // Búfer grande: la descompresión pide los bytes en trozos pequeños y, hacia un
                // archivo real, eso son muchas más llamadas al sistema de las necesarias.
                ZipProjectLoader.load(BufferedInputStream(streams.openInput(uri), ZIP_READ_BUFFER_BYTES))
            }
            session.replaceProject(loaded)
            onProjectReplaced(loaded)
            session.publish(loaded)
            loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: ZipLoadException) {
            session.show(DwpUiState.Error(e.message ?: "Error al leer el zip.", stateBeforeLoad))
            null
        } catch (e: OutOfMemoryError) {
            session.show(DwpUiState.Error(UserFacingErrors.OUT_OF_MEMORY, stateBeforeLoad))
            null
        } catch (e: Exception) {
            session.show(
                DwpUiState.Error("No se pudo cargar el archivo: ${UserFacingErrors.describe(e)}", stateBeforeLoad)
            )
            null
        }
    }

    private companion object {
        const val ZIP_READ_BUFFER_BYTES = 64 * 1024
    }
}
