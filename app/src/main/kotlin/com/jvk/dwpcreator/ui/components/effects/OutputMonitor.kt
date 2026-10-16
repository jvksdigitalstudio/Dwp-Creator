package com.jvk.dwpcreator.ui.components.effects

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.sampler.api.AudioStats
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxLevelMeter
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** Cada cuánto se refresca el vúmetro mientras el panel está abierto (~30 Hz). */
private const val MeterRefreshMs = 33L

/** Cada cuántos ciclos del vúmetro se relee la estadística de carga (~0,5 s): el texto no necesita 30 Hz. */
private const val StatsEveryTicks = 15

/**
 * Monitor de la cabecera: vúmetro de salida + métricas de rendimiento **medidas en
 * el hilo de audio del propio dispositivo** (carga DSP, tamaño del búfer y
 * *underruns*; ver `AudioStats`). Es el "benchmark" en vivo: con él se ve de
 * inmediato si una combinación de efectos y notas deja margen o se acerca al límite
 * de tiempo real, sin `adb`. El texto pasa a ámbar con carga alta o cualquier
 * *underrun*, y a rojo cerca de la saturación.
 *
 * Vive en su propio composable para que su refresco (estado propio) recomponga
 * **sólo** este monitor y no el panel entero. Al salir del árbol (panel cerrado) su
 * `LaunchedEffect` se cancela: sin panel visible no hay sondeo.
 */
@Composable
internal fun OutputMonitor(controller: SamplerFxController) {
    var peakLeft by remember { mutableFloatStateOf(0f) }
    var peakRight by remember { mutableFloatStateOf(0f) }
    var stats by remember { mutableStateOf(AudioStats()) }
    LaunchedEffect(controller) {
        var tick = 0
        while (true) {
            val (left, right) = controller.readPeaks()
            peakLeft = left
            peakRight = right
            if (tick % StatsEveryTicks == 0) stats = controller.readStats()
            tick++
            delay(MeterRefreshMs)
        }
    }

    val statsColor = when {
        stats.dspLoadPercent >= 90f -> MixerMuteActive
        stats.dspLoadPercent >= 70f || stats.underruns > 0 -> MixerSoloActive
        else -> TextDim
    }
    Text(
        text = "DSP ${stats.dspLoadPercent.roundToInt()}% · ${"%.1f".format(Locale.US, stats.bufferMs)} ms · " +
            "${stats.underruns} xruns",
        color = statsColor,
        fontSize = 9.sp,
        maxLines = 1,
        modifier = Modifier.padding(end = 8.dp)
    )
    FxLevelMeter(peakLeft = peakLeft, peakRight = peakRight)
}
