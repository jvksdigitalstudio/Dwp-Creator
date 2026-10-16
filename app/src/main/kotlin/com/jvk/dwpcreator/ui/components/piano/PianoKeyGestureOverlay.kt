package com.jvk.dwpcreator.ui.components.piano

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.jvk.dwpcreator.dwp.SampleInfo

/**
 * Rastrea cada dedo (`PointerId`) de forma independiente -- soporte real de
 * multi-touch, no solo "el último toque gana" -- y, para cada uno, dispara
 * [onNoteOn] al entrar en una tecla nueva y [onNoteOff] al salir de la
 * anterior (arrastre/glissando) o al levantar el dedo. Mantener el dedo
 * quieto sobre la misma tecla NO retriggerea el sonido -- igual que un
 * teclado real, solo "ataca" la nota al pasar a una tecla distinta.
 *
 * **Por qué los callbacks y la lista van por [rememberUpdatedState]** (y
 * `pointerInput` solo depende de [listState]): las lambdas que llegan desde
 * `MainActivity` capturan el ViewModel (tipo inestable para Compose), así que
 * pueden recrearse en cada recomposición, y cada `noteOn`/`noteOff`
 * cambia `playingIndices` -> recompone. Con esas lambdas como claves de
 * `pointerInput`, el bloque de gestos se cancelaba y reiniciaba en pleno
 * toque: se perdía `activeKeyForPointer` (el dedo que seguía apoyado se
 * volvía a tratar como "tecla nueva" en su siguiente evento y RE-disparaba
 * la nota) y un dedo quieto con un micro-movimiento sonaba dos veces. Así el
 * bloque vive mientras el composable viva y siempre llama a la versión más
 * reciente de cada callback.
 */
@Composable
internal fun PianoKeyGestureOverlay(
    samples: List<SampleInfo>,
    listState: LazyListState,
    onNoteOn: (SampleInfo) -> Unit,
    onNoteOff: (SampleInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSamples by rememberUpdatedState(samples)
    val currentNoteOn by rememberUpdatedState(onNoteOn)
    val currentNoteOff by rememberUpdatedState(onNoteOff)

    Box(
        modifier = modifier.pointerInput(listState) {
            val activeKeyForPointer = mutableMapOf<Long, SampleInfo>()
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val pointerId = change.id.value
                            if (change.pressed) {
                                val hit = sampleAtY(change.position.y, listState, currentSamples)
                                val previous = activeKeyForPointer[pointerId]
                                if (hit == null) {
                                    // El dedo salió de las filas visibles (arrastrado por
                                    // encima/debajo de la lista): la tecla anterior se suelta.
                                    if (previous != null) {
                                        activeKeyForPointer.remove(pointerId)
                                        currentNoteOff(previous)
                                    }
                                } else if (hit.index != previous?.index) {
                                    if (previous != null) currentNoteOff(previous)
                                    currentNoteOn(hit)
                                    activeKeyForPointer[pointerId] = hit
                                }
                                change.consume()
                            } else {
                                val released = activeKeyForPointer.remove(pointerId)
                                if (released != null) currentNoteOff(released)
                            }
                        }
                    }
                }
            } finally {
                // Si el overlay sale de la composición con dedos aún apoyados
                // (p. ej. la lista se sustituye por un overlay de exportación),
                // ninguna tecla debe quedar encendida para siempre.
                activeKeyForPointer.values.forEach { currentNoteOff(it) }
                activeKeyForPointer.clear()
            }
        }
    )
}

/** Qué muestra corresponde a la posición Y (en px, relativa al viewport del LazyColumn) dada, usando las filas realmente visibles y medidas -- nunca una altura de fila asumida/fija. */
private fun sampleAtY(y: Float, listState: LazyListState, samples: List<SampleInfo>): SampleInfo? {
    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        y >= info.offset.toFloat() && y < (info.offset + info.size).toFloat()
    } ?: return null
    return samples.getOrNull(item.index)
}
