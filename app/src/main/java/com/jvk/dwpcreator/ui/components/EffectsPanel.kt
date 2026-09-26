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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.R
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

/**
 * Panel del sampler (reverb, delay, y demás efectos por venir). **Estado
 * actual: andamiaje provisional**, pedido explícitamente así por el usuario
 * ("por el momento la ventana esté vacía... ponlo solo provisional ya luego
 * se perfecciona") -- solo el contenedor, la animación de despliegue y el
 * botón para ocultarlo están terminados; el contenido real (controles de
 * reverb/delay/etc.) todavía no existe y es intencional que no exista en
 * esta pasada.
 *
 * Se dibuja **encima** del teclado y la lista de muestras (superpuesto, no
 * empujando su layout) porque así lo pidió el usuario ("que valla encima
 * del teclado y las muestras"): el llamador debe colocar este composable
 * dentro del mismo `Box` que esas dos superficies, alineado
 * [Alignment.BottomCenter], para que se despliegue hacia arriba desde la
 * base de esa zona.
 *
 * **Bloquea el toque para que no "traspase" hacia el teclado/lista de abajo
 * (Doc/38).** `Box` en Compose no hace hit-testing por z-order visual puro:
 * si dos hermanos se superponen y el de encima (este panel) no tiene ningún
 * nodo de entrada táctil en el punto tocado, Compose sigue probando al
 * hermano de abajo hasta encontrar uno que sí lo tenga -- en este layout,
 * eso es `PianoKeyGestureOverlay` (dispara notas MIDI) o la fila tocada de
 * la `LazyColumn` (reproduce el preview de la muestra). El fondo, el texto
 * placeholder y el espacio vacío de la cabecera de este panel no tenían, por
 * sí mismos, ningún manejador de toque -- por eso tocar "en cualquier lado"
 * de esta ventana sonaba como si fuera transparente al tacto. El
 * `Modifier.clickable` (sin ripple, sin acción) en el `Column` de abajo
 * existe únicamente para que Compose registre este panel como el
 * destinatario del toque en todo su rectángulo y deje de probar los
 * hermanos de detrás; el botón de cerrar de la cabecera, al ser un
 * `clickable` más interno, lo sigue consumiendo primero y normalmente (un
 * `clickable` anidado consume el gesto antes de que llegue al `clickable`
 * del padre, así que no hay conflicto entre ambos).
 */
@Composable
fun EffectsPanel(
    visible: Boolean,
    onHide: () -> Unit,
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
            // Cabecera del panel: el pedido del usuario fue explícito en que
            // la opción para "esconder" esta ventana va ENCIMA de ella misma
            // (es decir, dentro de su propia cabecera), no solo el botón
            // externo de EffectsPanelToggleButton -- de ahí este segundo
            // control, deliberadamente redundante con el de arriba.
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

            HorizontalDivider(color = TextDim.copy(alpha = 0.15f))

            // Contenido: placeholder intencional (ver KDoc de la función).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reverb, Delay y más efectos -- próximamente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim.copy(alpha = 0.7f)
                )
            }
        }
    }
}
