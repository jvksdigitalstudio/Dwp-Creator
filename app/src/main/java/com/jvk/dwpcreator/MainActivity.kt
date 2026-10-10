package com.jvk.dwpcreator

import android.net.Uri
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jvk.dwpcreator.ui.components.ExportFormatDialog
import com.jvk.dwpcreator.ui.components.MidiDevicesDialog
import com.jvk.dwpcreator.ui.components.RenameAllDialog
import com.jvk.dwpcreator.ui.screens.MainScreen
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.state.SamplerFxController
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
        // La app tiene un único tema oscuro fijo (ver DwpCreatorTheme). Con el
        // `enableEdgeToEdge()` por defecto, los iconos de las barras del sistema
        // siguen el tema CLARO/OSCURO DEL TELÉFONO: con el móvil en modo claro
        // se dibujaban oscuros sobre el fondo morado oscuro de la app (hora y
        // batería ilegibles). `SystemBarStyle.dark` fuerza iconos claros.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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
        reportFullyDrawnOnFirstDraw()
    }

    /**
     * Avisa al sistema de que la app está "totalmente dibujada" **desde dentro del primer
     * frame que realmente se dibuja**, no entre frames.
     *
     * Contexto (Doc/60, 61 y 69). En una app 100 % Compose el sistema no detecta solo cuándo
     * terminó el arranque, así que se llama a [reportFullyDrawn] (arregla "Unable to confirm
     * activity launch completion" y alimenta la métrica de arranque de Android Vitals). Antes
     * se hacía en un `LaunchedEffect(Unit)`, que corre *entre* frames: Macrobenchmark busca el
     * siguiente frame de UI que termina después del aviso y exige que tenga su pareja de dibujo
     * en el hilo de render; en una pantalla estática ese frame existía pero no dibujaba nada,
     * y la medición fallaba con `No RT frame slice associated with UI thread frame slice ends
     * after reportFullyDrawn` (Startup Benchmark #1, los 3 modos). Llamarlo dentro de `onDraw`
     * garantiza que el frame que contiene el aviso es uno que se dibuja y se presenta.
     *
     * El listener se usa una sola vez y se retira en el mensaje siguiente: Android prohíbe
     * quitarlo desde dentro de `onDraw`.
     */
    private fun reportFullyDrawnOnFirstDraw() {
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var reported = false

            override fun onDraw() {
                if (reported) return
                reported = true
                reportFullyDrawn()
                decor.post {
                    val observer = decor.viewTreeObserver
                    if (observer.isAlive) observer.removeOnDrawListener(this)
                }
            }
        })
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
            onParamChange = viewModel::setFxParam,
            onParamReset = viewModel::resetFxParam,
            onToggle = viewModel::setFxToggle,
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
