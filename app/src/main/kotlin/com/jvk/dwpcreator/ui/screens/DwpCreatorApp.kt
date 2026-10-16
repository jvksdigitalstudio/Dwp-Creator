package com.jvk.dwpcreator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvk.dwpcreator.ui.components.dialogs.ExportFormatDialog
import com.jvk.dwpcreator.ui.components.dialogs.MidiDevicesDialog
import com.jvk.dwpcreator.ui.components.dialogs.RenameAllDialog
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel

@Composable
internal fun DwpCreatorApp(viewModel: DwpCreatorViewModel) {
    // Doc/63 -- REVERTIDO a `collectAsState()`. Se había cambiado a
    // `collectAsStateWithLifecycle()` (Doc/52) para dejar de recolectar en
    // segundo plano; eso causó un crash real y confirmado en la tablet del
    // usuario en el primer arranque:
    //   java.lang.IllegalStateException: CompositionLocal LocalLifecycleOwner not present
    //     at androidx.lifecycle.compose.LocalLifecycleOwnerKt$LocalLifecycleOwner$1.invoke
    // `collectAsStateWithLifecycle` depende de que el CompositionLocal
    // `LocalLifecycleOwner` (del paquete androidx.lifecycle.compose) esté
    // provisto en ese punto exacto de la composición; en este proyecto, con
    // esta combinación de versiones, no lo está, y lanza esa excepción en
    // vez de simplemente no pausar la recolección. `collectAsState()` no
    // depende de ningún CompositionLocal, así que no tiene ese riesgo --
    // la única diferencia real es que sigue recolectando con la app en
    // segundo plano, un costo de eficiencia menor y conocido, no un bug.
    // No se vuelve a intentar `collectAsStateWithLifecycle` sin poder
    // probarlo primero contra un build y dispositivo reales.
    val state by viewModel.uiState.collectAsState()
    val playingIndices by viewModel.playingIndices.collectAsState()
    val mixerStates by viewModel.mixerStates.collectAsState()
    val midiConnected by viewModel.midiConnected.collectAsState()
    val midiConnectedDeviceName by viewModel.midiConnectedDeviceName.collectAsState()
    val midiDevices by viewModel.midiDevices.collectAsState()
    val fxController = remember(viewModel) {
        SamplerFxController(
            state = viewModel.fxState,
            onParamChange = viewModel::setParam,
            onParamReset = viewModel::resetParam,
            onToggle = viewModel::setToggle,
            onPreset = viewModel::applyFxPreset,
            onModuleReset = viewModel::resetFxModule,
            readPeaks = viewModel::readOutputPeaks,
            readStats = viewModel::readAudioStats
        )
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMidiDialog by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }

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

    // No existe un mime type oficial registrado para ".dwp" (formato
    // propietario de DirectWave); "application/octet-stream" es la
    // convención estándar para "archivo binario sin tipo MIME conocido",
    // ya usada en este mismo archivo para abrir el ZIP de entrada (línea
    // de openZipLauncher.launch, arriba). CreateDocument añade la
    // extensión ".dwp" del nombre sugerido al nombre final sin necesidad
    // de que el mime type la codifique.
    val exportMonolithicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportMonolithicTo(it) }
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

    if (showExportFormatDialog && loadedState != null) {
        ExportFormatDialog(
            onExportZip = {
                showExportFormatDialog = false
                exportZipLauncher.launch("${loadedState.instrumentName}.zip")
            },
            onExportMonolithic = {
                showExportFormatDialog = false
                exportMonolithicLauncher.launch("${loadedState.instrumentName}.dwp")
            },
            onDismiss = { showExportFormatDialog = false }
        )
    }

    MainScreen(
        state = state,
        onLoadClick = {
            // "application/x-zip-compressed" es el tipo con el que muchos
            // proveedores de archivos (Windows/OneDrive/descargas del navegador)
            // etiquetan un .zip; sin él esos archivos aparecían atenuados y no
            // se podían elegir en el selector.
            openZipLauncher.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        },
        onRenameAllClick = { showRenameDialog = true },
        onMidiClick = { showMidiDialog = true },
        onExportClick = {
            if (loadedState != null) showExportFormatDialog = true
        },
        onSamplePreview = { sample -> viewModel.previewSample(sample.index) },
        onNoteOn = { sample -> viewModel.noteOn(sample.index) },
        onNoteOff = { sample -> viewModel.noteOff(sample.index) },
        onDismissError = { viewModel.dismissError() },
        midiConnected = midiConnected,
        playingIndices = playingIndices,
        mixerStates = mixerStates,
        onToggleMute = { sample -> viewModel.toggleMute(sample.index) },
        onToggleSolo = { sample -> viewModel.toggleSolo(sample.index) },
        onPanChange = { sample, pan -> viewModel.setPan(sample.index, pan) },
        onVolumeChange = { sample, volume -> viewModel.setVolume(sample.index, volume) },
        fxController = fxController
    )
}
