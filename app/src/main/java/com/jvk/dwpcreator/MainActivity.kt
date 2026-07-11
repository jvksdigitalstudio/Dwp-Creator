package com.jvk.dwpcreator

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jvk.dwpcreator.ui.components.MidiDevicesDialog
import com.jvk.dwpcreator.ui.components.RenameAllDialog
import com.jvk.dwpcreator.ui.screens.MainScreen
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.theme.DwpCreatorTheme
import com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel

/**
 * Paso 7 complete: the full pipeline. LOAD/RENOMBRAR TODO/EXPORT operate on
 * the real, user-picked file via [DwpCreatorViewModel] -> ZipProjectLoader/
 * DwpEngine/ZipProjectExporter (Pasos 2-3, 5). Tapping a row or receiving a
 * MIDI Note On both play real audio through SamplePlayer (Paso 6). The MIDI
 * button connects to the first available external USB/Bluetooth keyboard
 * via android.media.midi (Paso 7).
 *
 * If the app crashed on its previous launch, [DwpCreatorApplication] will
 * have captured the stack trace; this shows it full-screen instead of
 * silently reopening, so a crash can be diagnosed from one screenshot.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lastCrash = DwpCreatorApplication.consumeLastCrash(application)
        setContent {
            DwpCreatorTheme {
                if (lastCrash != null) {
                    CrashDiagnosticScreen(lastCrash)
                } else {
                    DwpCreatorApp()
                }
            }
        }
    }
}

@Composable
private fun CrashDiagnosticScreen(stackTrace: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "La app se cerró inesperadamente la última vez. Esto es lo que pasó:",
            color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        SelectionContainer {
            Text(
                stackTrace,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DwpCreatorApp(viewModel: DwpCreatorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val playingIndex by viewModel.playingIndex.collectAsState()
    val midiConnected by viewModel.midiConnected.collectAsState()
    val midiConnectedDeviceName by viewModel.midiConnectedDeviceName.collectAsState()
    val midiDevices by viewModel.midiDevices.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMidiDialog by remember { mutableStateOf(false) }

    val openZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromZip(it) }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportTo(it) }
    }

    val loadedState = state as? DwpUiState.Loaded

    if (showRenameDialog && loadedState != null) {
        RenameAllDialog(
            currentName = loadedState.instrumentName,
            onConfirm = { newName ->
                viewModel.renameAll(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showMidiDialog) {
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshMidiDevices() }
        MidiDevicesDialog(
            devices = midiDevices,
            connectedDeviceName = midiConnectedDeviceName,
            onRefresh = { viewModel.refreshMidiDevices() },
            onConnect = { device -> viewModel.connectToMidiDevice(device.id) },
            onDisconnect = { viewModel.disconnectMidi() },
            onDismiss = { showMidiDialog = false }
        )
    }

    MainScreen(
        state = state,
        onLoadClick = {
            openZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        },
        onRenameAllClick = { showRenameDialog = true },
        onMidiClick = { showMidiDialog = true },
        onExportClick = {
            loadedState?.let { exportZipLauncher.launch("${it.instrumentName}.zip") }
        },
        onSamplePreview = { sample -> viewModel.previewSample(sample.index) },
        midiConnected = midiConnected,
        playingIndex = playingIndex
    )
}
