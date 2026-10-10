package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.R
import com.jvk.dwpcreator.audio.AudioStats
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt
import com.jvk.dwpcreator.ui.theme.TextDim
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fracción de la altura disponible (la franja que ocupan teclado + lista de
 * muestras) que cubre el panel al desplegarse: "hasta la mitad, un poquito
 * menos" -- pedido explícito del usuario con capturas anotadas a mano. Un
 * solo número, para no repetir el criterio en cada sitio que lo use.
 */
private val EffectsPanelHeightFraction = 0.45f

/** Ancho del ícono de la flecha. Subido de 18dp a 32dp y luego a 56dp -- pedido explícito y repetido del usuario de que se vea claramente más grande. */
private val EffectsPanelToggleIconWidth = 56.dp

/**
 * Relación de aspecto (alto/ancho) del triángulo tal como quedó recortado en
 * `ic_triangle_up.xml` (Doc/37): viewport 500x245, es decir 245/500. Ya no es
 * un ícono cuadrado -- el recorte le quitó a propósito el margen vacío que
 * tenía debajo (y encima) del triángulo dentro del viewBox original de
 * 500x500. Si aquí se le siguiera dando un `Modifier.size(...)` cuadrado,
 * `Icon` reintroduciría ese mismo hueco al centrar el contenido recortado
 * (`ContentScale.Fit`) dentro de una caja más alta que ancha; por eso el alto
 * real del `Modifier` se deriva de este ratio en vez de usar un tamaño fijo
 * independiente, para que el vector ocupe el 100% de su caja y no quede
 * flotando con aire debajo.
 */
private const val TriangleAspectRatio = 245f / 500f

/**
 * El icono que abre el [EffectsPanel]. Va justo encima de [DwpStatusBar]
 * ("encima de © 2026 by YeiViKas Digital Company" en el pedido original) y
 * reutiliza el icono "triangle-up" entregado por el usuario (`ic_triangle_up`,
 * vector fiel al triángulo del SVG original, solo recortado a su propio
 * bounding box -- ver Doc/37).
 *
 * **Deliberadamente solo el ícono, sin ningún contenedor alrededor.** La
 * primera versión de esto envolvía el `Icon` en un `IconButton` dentro de un
 * `Row` de ancho completo -- eso reserva una franja tocable de 48dp a lo
 * ancho de toda la pantalla, que en pantalla se lee como una segunda barra o
 * cabecera. El usuario fue explícito en que **no** pidió eso, solo la
 * flecha: aquí se usa un `Modifier.clickable` liso sobre el propio `Icon`
 * (mismo patrón que ya usa `MidiDevicesDialog.kt` en este proyecto), sin
 * `Surface`, sin `IconButton` y sin ocupar más ancho que el del propio
 * triángulo.
 *
 * **Se oculta por completo mientras el panel está abierto** (Doc/37, pedido
 * explícito del usuario: "cuando esta abierto el panel que se oculte la
 * flecha"). Antes esta flecha se quedaba visible y solo giraba 180° para
 * indicar "toca para cerrar" -- pero [EffectsPanel] ya tiene su propio botón
 * de cierre en la cabecera del panel (el `IconButton` de más abajo en este
 * mismo archivo), así que mantener esta segunda flecha visible mientras el
 * panel está desplegado era redundante y, al quedar pegada al borde inferior
 * de la pantalla, se leía como si sobrara un elemento suelto encima del
 * propio panel. Con el panel cerrado sigue exactamente igual que antes.
 */
@Composable
fun EffectsPanelToggleButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !expanded,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_triangle_up),
            contentDescription = "Mostrar panel del sampler",
            tint = TextDim,
            modifier = Modifier
                .width(EffectsPanelToggleIconWidth)
                .height(EffectsPanelToggleIconWidth * TriangleAspectRatio)
                .clickable(onClick = onToggle)
        )
    }
}

/** Cada cuánto se refresca el vúmetro mientras el panel está abierto (~30 Hz). */
private const val MeterRefreshMs = 33L

/**
 * Panel del sampler: el bus maestro completo (MASTER, FX DRIVE A/B, FX DELAY,
 * FX REVERB, FX CHORUS -- ver [SamplerFxPanelContent]) con vúmetro de salida
 * en la cabecera. Reemplaza al andamiaje provisional de Doc/31: el contenedor,
 * la animación y los dos botones de cierre son los de siempre; el contenido es
 * ahora real y está conectado de punta a punta al motor de audio
 * (`SamplerEngine`).
 *
 * Se dibuja **encima** del teclado y la lista de muestras (superpuesto, no
 * empujando su layout): el llamador debe colocarlo dentro del mismo `Box` que
 * esas dos superficies, alineado [Alignment.BottomCenter], para que se
 * despliegue hacia arriba desde la base de esa zona.
 *
 * **Bloquea el toque para que no "traspase" hacia el teclado/lista de abajo
 * (Doc/38).** El `Modifier.clickable` (sin ripple, sin acción) del `Column`
 * existe únicamente para que Compose registre este panel como destinatario del
 * toque en todo su rectángulo y deje de probar a los hermanos de detrás
 * (`PianoKeyGestureOverlay`, filas de la `LazyColumn`). Los knobs, interruptores
 * y el botón de cerrar, al ser nodos de entrada más internos, consumen su gesto
 * primero y con normalidad.
 */
@Composable
fun EffectsPanel(
    visible: Boolean,
    onHide: () -> Unit,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { fullHeight -> fullHeight } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { fullHeight -> fullHeight } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(EffectsPanelHeightFraction)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(SurfacePurpleAlt)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            // La opción para ocultar la ventana va ENCIMA de ella misma (en su
            // propia cabecera), además del botón externo de EffectsPanelToggleButton.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAMPLER",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonPurple
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutputMonitor(controller)
                    IconButton(onClick = onHide) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_triangle_up),
                            contentDescription = "Ocultar panel del sampler",
                            tint = TextDim,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(180f)
                        )
                    }
                }
            }

            HorizontalDivider(color = TextDim.copy(alpha = 0.15f))

            val fxState by controller.state.collectAsState()
            SamplerFxPanelContent(
                state = fxState,
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

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
private fun OutputMonitor(controller: SamplerFxController) {
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
