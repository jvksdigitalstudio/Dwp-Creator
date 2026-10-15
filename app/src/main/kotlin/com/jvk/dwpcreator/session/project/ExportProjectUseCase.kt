package com.jvk.dwpcreator.session.project

import android.net.Uri
import com.jvk.dwpcreator.session.AppDispatchers
import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exporta el instrumento cargado a [uri] en el [ExportFormat] indicado.
 *
 * Orden deliberado: primero se prepara TODO lo que puede fallar en CPU y solo
 * después se abre el destino, de modo que un fallo no deja un archivo vacío o a
 * medias. Mientras tanto la UI muestra el avance (ver [ExportProgressPacer]).
 */
class ExportProjectUseCase(
    private val session: ProjectSession,
    private val streams: FileStreams,
    private val dispatchers: AppDispatchers,
    private val pacer: ExportProgressPacer = ExportProgressPacer()
) {

    suspend operator fun invoke(uri: Uri, format: ExportFormat) {
        if (session.isBusy()) return
        val current = session.project ?: return
        val loadedState = session.loadedState() ?: return
        val total = current.audioByIndex.size

        coroutineScope {
            session.show(loadedState.copy(exportProgress = DwpUiState.ExportProgress(0, total, "Preparando", "")))
            val ticks = Channel<DwpUiState.ExportProgress>(Channel.UNLIMITED)
            val paintJob = launch {
                pacer.paint(total, ticks) { session.show(loadedState.copy(exportProgress = it)) }
            }

            fun abort(detail: String) {
                ticks.close()
                paintJob.cancel()
                session.show(
                    DwpUiState.Error(
                        message = "${format.errorPrefix}: $detail",
                        previous = loadedState.copy(exportProgress = null)
                    )
                )
            }

            try {
                val prepared = withContext(dispatchers.default) {
                    format.prepare(current, loadedState.instrumentName) { ticks.trySend(it) }
                }
                ticks.close()
                // Espera a que la barra termine de pintar el 100 % real antes de dar por cerrado el overlay.
                paintJob.join()

                if (format.announcesWriteStage) {
                    session.show(
                        loadedState.copy(exportProgress = DwpUiState.ExportProgress(total, total, "Escribiendo archivo", ""))
                    )
                }
                withContext(dispatchers.io) { prepared.writeTo(streams.openOutput(uri)) }
                session.show(loadedState.copy(exportProgress = null))
            } catch (e: CancellationException) {
                ticks.close()
                paintJob.cancel()
                throw e
            } catch (e: OutOfMemoryError) {
                abort(UserFacingErrors.OUT_OF_MEMORY)
            } catch (e: Exception) {
                abort(UserFacingErrors.describe(e))
            }
        }
    }
}
