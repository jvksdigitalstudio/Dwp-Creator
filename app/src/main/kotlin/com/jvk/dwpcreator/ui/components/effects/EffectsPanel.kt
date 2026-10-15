package com.jvk.dwpcreator.ui.components.effects

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.R
import com.jvk.dwpcreator.ui.components.sampler.SamplerPanelContent
import com.jvk.dwpcreator.ui.state.SamplerFxController
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt
import com.jvk.dwpcreator.ui.theme.TextDim

/**
 * Fracción de la altura disponible (la franja que ocupan teclado + lista de
 * muestras) que cubre el panel al desplegarse: "hasta la mitad, un poquito
 * menos" -- pedido explícito del usuario con capturas anotadas a mano. Un
 * solo número, para no repetir el criterio en cada sitio que lo use.
 */
private val EffectsPanelHeightFraction = 0.45f

/**
 * Panel del sampler: el bus maestro completo (MASTER, FX DRIVE A/B, FX DELAY,
 * FX REVERB, FX CHORUS -- ver [SamplerFxPanelContent]) y los módulos de voz (muestra, filtro, envolventes, LFO -- ver [SamplerPanelContent]) con vúmetro de salida
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
            SamplerPanelContent(
                state = fxState,
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
