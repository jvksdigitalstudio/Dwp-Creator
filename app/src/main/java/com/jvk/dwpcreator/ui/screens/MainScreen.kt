package com.jvk.dwpcreator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.domain.dwp.SampleInfo
import com.jvk.dwpcreator.ui.components.DwpStatusBar
import com.jvk.dwpcreator.ui.components.DwpTopBar
import com.jvk.dwpcreator.ui.components.ImportingOverlay
import com.jvk.dwpcreator.ui.components.LoadEmptyState
import com.jvk.dwpcreator.ui.components.SampleRow
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.OctaveColors

/**
 * Number of semitones per octave -- used only to pick a rotating accent
 * color per octave in the list; has no bearing on the DWP engine itself.
 */
private const val SEMITONES_PER_OCTAVE = 12

@Composable
fun MainScreen(
    state: DwpUiState,
    onLoadClick: () -> Unit,
    onRenameAllClick: () -> Unit,
    onMidiClick: () -> Unit,
    onExportClick: () -> Unit,
    onSamplePreview: (SampleInfo) -> Unit = {},
    onSampleRename: (SampleInfo) -> Unit = {},
    onSampleDelete: (SampleInfo) -> Unit = {},
    midiConnected: Boolean = false,
    playingIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    if (state is DwpUiState.Error) {
        LaunchedEffect(state.message) {
            snackbarHostState.showSnackbar(state.message)
        }
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
                midiConnected = midiConnected
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(BgDark)
        ) {
            when (val effectiveState = if (state is DwpUiState.Error) state.previous else state) {
                is DwpUiState.Empty -> {
                    LoadEmptyState(onLoadClick = onLoadClick, modifier = Modifier.weight(1f))
                }

                is DwpUiState.Importing -> {
                    ImportingOverlay(message = effectiveState.message, modifier = Modifier.weight(1f))
                }

                is DwpUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(effectiveState.samples, key = { it.index }) { sample ->
                            // lowKey is used here (never rootKey/highKey): those two get
                            // extended to 0/127 on the first/last sample to cover the full
                            // keyboard, which would otherwise miscount octaves at the edges.
                            val octaveIndex = sample.lowKey / SEMITONES_PER_OCTAVE
                            val accent = OctaveColors[octaveIndex.coerceIn(0, OctaveColors.lastIndex)]
                            SampleRow(
                                sample = sample,
                                accentColor = accent,
                                isPlaying = playingIndex == sample.index,
                                onPreview = { onSamplePreview(sample) },
                                onRename = { onSampleRename(sample) },
                                onDelete = { onSampleDelete(sample) }
                            )
                        }
                    }
                    val octaves = effectiveState.samples
                        .map { it.lowKey / SEMITONES_PER_OCTAVE }
                        .distinct().size
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
