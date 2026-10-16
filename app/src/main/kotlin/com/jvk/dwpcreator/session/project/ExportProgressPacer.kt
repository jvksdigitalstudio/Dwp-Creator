package com.jvk.dwpcreator.session.project

import com.jvk.dwpcreator.ui.state.DwpUiState
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay

/**
 * Reparte los avisos de progreso de una exportación a un ritmo visible.
 *
 * Con instrumentos pequeños el trabajo real termina en menos de un fotograma
 * (~16 ms) y Compose solo pinta el último valor: la barra saltaría de 0 % a
 * 100 %. El pacer deja un mínimo de tiempo por cada valor real recibido, de
 * modo que la barra avance muestra a muestra, sin retrasar el resultado más de
 * ~[TARGET_TOTAL_ANIMATION_MS] (el trabajo real ya terminó cuando se pinta).
 */
class ExportProgressPacer {

    /** Pausa tras pintar cada aviso cuando hay [total] muestras. */
    fun stepDelayMs(total: Int): Long =
        (TARGET_TOTAL_ANIMATION_MS / total.coerceAtLeast(1)).coerceIn(MIN_STEP_MS, MAX_STEP_MS)

    /** Consume [ticks] hasta que se cierre el canal, pintando cada uno con [onTick] y esperando entre ellos. */
    suspend fun paint(
        total: Int,
        ticks: ReceiveChannel<DwpUiState.ExportProgress>,
        onTick: (DwpUiState.ExportProgress) -> Unit
    ) {
        val stepDelayMs = stepDelayMs(total)
        for (tick in ticks) {
            onTick(tick)
            delay(stepDelayMs)
        }
    }

    companion object {
        const val TARGET_TOTAL_ANIMATION_MS = 900L
        const val MIN_STEP_MS = 4L
        const val MAX_STEP_MS = 40L
    }
}
