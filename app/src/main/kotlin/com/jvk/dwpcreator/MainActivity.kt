package com.jvk.dwpcreator

import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import com.jvk.dwpcreator.ui.screens.CrashDiagnosticScreen
import com.jvk.dwpcreator.ui.screens.DwpCreatorApp
import com.jvk.dwpcreator.ui.theme.DwpCreatorTheme
import com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel
import com.jvk.dwpcreator.viewmodel.DwpCreatorViewModelFactory

/**
 * Paso 7 complete: the full pipeline. LOAD/RENOMBRAR TODO/EXPORT operate on
 * the real, user-picked file via [DwpCreatorViewModel], which delegates to the
 * load / rename / export use cases of the session layer (Pasos 2-3, 5). Tapping a row or receiving a
 * MIDI Note On both play real audio through SamplePlayer (Paso 6). The MIDI
 * button connects to the first available external USB/Bluetooth keyboard
 * via android.media.midi (Paso 7).
 *
 * If the app crashed on its previous launch, [DwpCreatorApplication] will
 * have captured the stack trace; this shows it full-screen instead of
 * silently reopening, so a crash can be diagnosed from one screenshot.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DwpCreatorViewModel by viewModels {
        DwpCreatorViewModelFactory((application as DwpCreatorApplication).container)
    }
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
                    DwpCreatorApp(viewModel)
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
