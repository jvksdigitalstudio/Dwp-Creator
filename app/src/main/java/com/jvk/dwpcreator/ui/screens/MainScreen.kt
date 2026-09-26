package com.jvk.dwpcreator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.jvk.dwpcreator.domain.dwp.SampleInfo
import com.jvk.dwpcreator.ui.components.DwpStatusBar
import com.jvk.dwpcreator.ui.components.DwpTopBar
import com.jvk.dwpcreator.ui.components.EffectsPanel
import com.jvk.dwpcreator.ui.components.EffectsPanelToggleButton
import com.jvk.dwpcreator.ui.components.ExportProgressOverlay
import com.jvk.dwpcreator.ui.components.ImportingOverlay
import com.jvk.dwpcreator.ui.components.LoadEmptyState
import com.jvk.dwpcreator.ui.components.PianoKeyWidth
import com.jvk.dwpcreator.ui.components.SampleRow
import com.jvk.dwpcreator.ui.components.SampleRowEndPadding
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.state.SampleOctave
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.OctaveColors

@Composable
fun MainScreen(
    state: DwpUiState,
    onLoadClick: () -> Unit,
    onRenameAllClick: () -> Unit,
    onMidiClick: () -> Unit,
    onExportClick: () -> Unit,
    onSamplePreview: (SampleInfo) -> Unit = {},
    onNoteOn: (SampleInfo) -> Unit = onSamplePreview,
    onNoteOff: (SampleInfo) -> Unit = {},
    onDismissError: () -> Unit = {},
    midiConnected: Boolean = false,
    playingIndices: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Estado que realmente se dibuja: si hay un Error, la pantalla de fondo es
    // la que había justo antes (el diálogo de error se muestra ENCIMA).
    val displayedState = if (state is DwpUiState.Error) state.previous else state

    // Una importación o exportación en curso bloquea LOAD/RENOMBRAR/EXPORT;
    // sin proyecto cargado, RENOMBRAR/EXPORT no tienen sobre qué operar.
    val isBusy = displayedState is DwpUiState.Importing ||
        (displayedState is DwpUiState.Loaded && displayedState.exportProgress != null)
    val hasProject = displayedState is DwpUiState.Loaded

    // Errors used to only show a Snackbar that auto-dismissed after a couple
    // of seconds, and NOTHING ever called back into the view model to leave
    // the Error state -- so if you missed the toast, the app just sat there
    // silently stuck: state stayed DwpUiState.Error forever, EXPORT/RENOMBRAR
    // needed DwpUiState.Loaded to do anything, so every button after the
    // first failure became a no-op with zero feedback. This is almost
    // certainly why export "stopped doing anything at all" after the first
    // failed attempt. Now: a dialog you have to actually dismiss, with the
    // full message selectable/copyable, and dismissing it is the ONLY way
    // to close it -- which is also what puts the state back to Loaded.
    if (state is DwpUiState.Error) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("No se pudo completar la operación") },
            text = {
                SelectionContainer {
                    Text(state.message)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BgDark,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        topBar = {
            DwpTopBar(
                onLoad = onLoadClick,
                onRenameAll = onRenameAllClick,
                onMidi = onMidiClick,
                onExport = onExportClick,
                midiConnected = midiConnected,
                loadEnabled = !isBusy,
                projectActionsEnabled = hasProject && !isBusy
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(BgDark)
        ) {
            when (val effectiveState = displayedState) {
                is DwpUiState.Empty -> {
                    LoadEmptyState(onLoadClick = onLoadClick, modifier = Modifier.weight(1f))
                }

                is DwpUiState.Importing -> {
                    ImportingOverlay(message = effectiveState.message, modifier = Modifier.weight(1f))
                }

                is DwpUiState.Loaded -> {
                    // Se pide siempre, sin condicionar a exportProgress, para que el
                    // orden de llamadas a remember/composables se mantenga estable
                    // entre recomposiciones (buena práctica de Compose).
                    val listState = rememberLazyListState()
                    // Estado puramente de UI del panel del sampler (reverb/delay/etc.):
                    // deliberadamente NO vive en el ViewModel todavía -- es un placeholder
                    // provisional pedido así explícitamente por el usuario ("ponlo solo
                    // provisional ya luego se perfecciona"), sin nada que persistir aún.
                    var effectsPanelExpanded by remember { mutableStateOf(false) }
                    val exportProgress = effectiveState.exportProgress
                    if (exportProgress != null) {
                        ExportProgressOverlay(progress = exportProgress, modifier = Modifier.weight(1f))
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(effectiveState.samples, key = { it.index }) { sample ->
                                    // La octava sale de la NOTA de la muestra (SampleOctave), nunca de
                                    // su zona de disparo: lowKey/highKey de la primera/última muestra
                                    // se extienden a 0/127 para cubrir todo el teclado, y usar lowKey
                                    // pintaba la primera fila con el color de la octava 0.
                                    val octaveIndex = SampleOctave.of(sample)
                                    val accent = OctaveColors[octaveIndex.coerceIn(0, OctaveColors.lastIndex)]
                                    SampleRow(
                                        sample = sample,
                                        accentColor = accent,
                                        isPlaying = sample.index in playingIndices,
                                        onPreview = { onSamplePreview(sample) }
                                    )
                                }
                            }
                            // Superficie de gesto compartida sobre la columna de teclas de
                            // piano (derecha): un teclado real permite acordes (varios dedos
                            // a la vez) y arrastrar el dedo entre teclas sin soltarlo
                            // (glissando). Cada SampleRow, por sí sola, solo puede reaccionar
                            // a un toque aislado dentro de sus propios límites -- para que
                            // deslizar el dedo de una tecla a otra "suene" en el camino hace
                            // falta UNA superficie de toque que abarque toda la columna,
                            // independiente del scroll de cada fila individual.
                            //
                            // Se coloca encima del LazyColumn (mismo Box, después en el
                            // orden de composición -> recibe el toque primero dentro de su
                            // franja) y usa listState.layoutInfo para saber, para cualquier Y
                            // de pantalla, sobre qué fila realmente visible cae -- sin asumir
                            // una altura de fila fija ni duplicar esa suposición en ningún
                            // otro sitio.
                            //
                            // Contrapartida deliberada: dentro de esa franja concreta (ancho
                            // = PianoKeyWidth + SampleRowEndPadding) ya no se puede hacer
                            // scroll arrastrando -- igual que un teclado físico, no se
                            // "desliza" la lista tocando las teclas, se toca. Para hacer
                            // scroll, se sigue pudiendo arrastrar desde el resto de la fila
                            // (número/nombre), a la izquierda de esta franja.
                            PianoKeyGestureOverlay(
                                samples = effectiveState.samples,
                                listState = listState,
                                onNoteOn = onNoteOn,
                                onNoteOff = onNoteOff,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .fillMaxHeight()
                                    .width(PianoKeyWidth + SampleRowEndPadding)
                            )

                            // Panel del sampler (reverb/delay/etc., de momento vacío --
                            // ver KDoc de EffectsPanel): superpuesto ENCIMA del teclado y
                            // la lista de muestras, no empujando su layout, y anclado abajo
                            // para desplegarse "hacia arriba" -- pedido explícito del
                            // usuario con capturas anotadas a mano. Va último en el orden de
                            // composición de este Box para recibir el toque primero mientras
                            // está desplegado (por encima del overlay de gestos del piano).
                            EffectsPanel(
                                visible = effectsPanelExpanded,
                                onHide = { effectsPanelExpanded = false },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                            // El ícono que abre/cierra el panel de arriba, flotando EXACTO
                            // sobre el borde inferior de esta zona (pegado a donde empieza
                            // DwpStatusBar), sin reservar su propia fila/altura en el Column
                            // exterior -- pedido explícito del usuario: la primera versión de
                            // esto vivía en una fila propia entre la lista y el footer, y aunque
                            // esa fila no tenía fondo, sí sumaba altura nueva, dando la
                            // impresión de que el footer se había "engordado" en vez de solo
                            // llevar un ícono encima. Al ir en el mismo Box que
                            // PianoKeyGestureOverlay/EffectsPanel, alineado
                            // Alignment.BottomCenter y sin ningún offset, queda flush contra
                            // el borde inferior de este Box -- que es exactamente donde termina
                            // esta zona y empieza DwpStatusBar -- sin mover ni agrandar ninguno
                            // de los dos. Va último en el orden de composición para quedar
                            // visible por encima del panel incluso cuando este está desplegado.
                            EffectsPanelToggleButton(
                                expanded = effectsPanelExpanded,
                                onToggle = { effectsPanelExpanded = !effectsPanelExpanded },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                    val octaves = remember(effectiveState.samples) {
                        SampleOctave.countDistinct(effectiveState.samples)
                    }
                    DwpStatusBar(sampleCount = effectiveState.samples.size, octaveCount = octaves)
                }

                is DwpUiState.Error -> {
                    // Defensive only: an Error's `previous` should never itself be an
                    // Error (nothing in the app nests them), but the compiler can't
                    // prove that statically since `previous` is typed as DwpUiState.
                    LoadEmptyState(onLoadClick = onLoadClick, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

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
private fun PianoKeyGestureOverlay(
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
