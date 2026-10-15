package com.jvk.dwpcreator.ui.components.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.R
import com.jvk.dwpcreator.ui.theme.TextDim

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
