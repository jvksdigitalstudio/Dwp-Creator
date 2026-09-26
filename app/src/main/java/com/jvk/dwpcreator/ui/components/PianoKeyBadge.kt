package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextDark
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextLight
import com.jvk.dwpcreator.ui.theme.BlackKeyBorder
import com.jvk.dwpcreator.ui.theme.BlackKeyGradientBottom
import com.jvk.dwpcreator.ui.theme.BlackKeyGradientTop
import com.jvk.dwpcreator.ui.theme.BlackKeyPressedGradientBottom
import com.jvk.dwpcreator.ui.theme.BlackKeyPressedGradientTop
import com.jvk.dwpcreator.ui.theme.BlackKeyPressedText
import com.jvk.dwpcreator.ui.theme.BlackKeySheen
import com.jvk.dwpcreator.ui.theme.BlackKeyText
import com.jvk.dwpcreator.ui.theme.WhiteKeyBorder
import com.jvk.dwpcreator.ui.theme.WhiteKeyGradientBottom
import com.jvk.dwpcreator.ui.theme.WhiteKeyGradientTop
import com.jvk.dwpcreator.ui.theme.WhiteKeyPressedGradientBottom
import com.jvk.dwpcreator.ui.theme.WhiteKeyPressedGradientTop
import com.jvk.dwpcreator.ui.theme.WhiteKeySheen
import com.jvk.dwpcreator.ui.theme.WhiteKeyText

/**
 * Ancho/alto fijos de una tecla -- fuente única de verdad, reutilizada por
 * [MainScreen][com.jvk.dwpcreator.ui.screens.MainScreen] para calcular
 * exactamente qué franja de la pantalla es "la columna de teclas" al
 * implementar el overlay de gestos multi-touch (acordes + arrastre entre
 * teclas). No duplicar este número en ningún otro sitio.
 */
val PianoKeyWidth = 58.dp
val PianoKeyHeight = 42.dp

/**
 * Ancho de la banda vertical de octava (el borde lateral izquierdo de color,
 * pegado de arriba abajo de la tecla) y alto de la veta de brillo superior.
 *
 * Historial de diseño (ver `Doc/30`): la primera versión de esta marca era
 * un "pie" horizontal pegado al borde *inferior* de la tecla. Feedback
 * directo del usuario sobre esa versión: un piano/teclado se lee de
 * izquierda a derecha (grave -> agudo), así que la marca que indica "aquí
 * empieza una octava nueva" debe ir en el borde *lateral izquierdo* -- el
 * lado por el que se "entra" a la tecla al recorrer el teclado -- y no
 * abajo. Se cambió de una franja horizontal (`fillMaxWidth` + `height`) a
 * una franja vertical (`fillMaxHeight` + `width`), alineada a
 * [Alignment.CenterStart] en vez de [Alignment.BottomCenter].
 */
private val OctaveStripeWidth = 3.dp
private val SheenHeight = 3.dp

/** Separación entre la banda de octava (o el borde de la tecla, si no hay banda) y el texto de la nota. */
private val LabelStartGap = 6.dp

/**
 * Una tecla de piano, blanca o negra según la nota, con acabado "premium":
 * un barniz vertical (más claro arriba, más oscuro abajo) más una veta de
 * brillo cerca del borde superior, en vez de un color plano -- es lo que le
 * da la sensación de superficie física vista de frente/en ángulo, no una
 * etiqueta plana. Solo redondeada por abajo (el borde superior queda recto),
 * como la cara frontal visible de una tecla real vista desde el ángulo del
 * usuario.
 *
 * **En reposo**, la tecla en sí nunca cambia de color por octava -- ver el
 * KDoc de `ui/theme/Color.kt`. La única marca que varía con la octava
 * mientras la tecla está inactiva es [octaveAccentColor], y solo aparece en
 * la tecla Do ([isOctaveStart]) como una **banda vertical pegada al borde
 * lateral izquierdo** de la tecla (ver `Doc/30`) -- el lado por el que
 * "empieza" cada octava recorriendo el teclado de grave a agudo, no el
 * borde inferior.
 *
 * El nombre de la nota de **todas** las teclas -- no solo el Do -- se
 * posiciona centrado verticalmente y pegado a ese mismo lado izquierdo
 * ([Alignment.CenterStart]), de modo que toda la columna de etiquetas queda
 * alineada sobre una única línea vertical imaginaria junto al borde
 * izquierdo, en vez de repartida entre "abajo" y "abajo-izquierda" según la
 * tecla. En el Do de cada octava el texto aparece además más grande y en
 * negrita para que el ojo encuentre el inicio de cada octava de un vistazo.
 *
 * **Pulsación coloreada por octava.** Mientras [active] es `true` -- la
 * tecla está sonando de verdad, dedo o nota MIDI todavía sostenidos, ver
 * `DwpCreatorViewModel.noteOn`/`noteOff` -- la tecla se ilumina con el
 * **color de su propia octava** ([octaveAccentColor]) en vez de un tono
 * "pulsada" genérico compartido por todas: se calcula un barniz vertical
 * derivado de ese mismo acento (más claro arriba, más oscuro abajo, con
 * [lerp] hacia blanco/negro) para conservar el acabado "premium" de
 * degradado, y el color del texto se decide por la luminancia relativa real
 * de ese acento ([luminance]) -- oscuro sobre los tonos claros/neón
 * (la mayoría de [com.jvk.dwpcreator.ui.theme.OctaveColors]), claro sobre
 * los pocos que son oscuros -- para que la nota siga siendo legible sin
 * importar qué octava se esté tocando. Esto aplica a **todas** las teclas
 * activas de la octava, no solo al Do: al arrastrar el dedo entre teclas
 * (glissando) o al tocar un acorde, cada tecla que suena se pinta con el
 * color de su propia octava mientras sigue activa, y vuelve a su acabado
 * neutro de fábrica en cuanto se suelta. Si no se provee
 * [octaveAccentColor] (`Color.Unspecified`), se cae de vuelta al tono
 * "pulsada" genérico anterior en vez de fallar.
 */
@Composable
fun PianoKeyBadge(
    note: String,
    active: Boolean = false,
    isOctaveStart: Boolean = false,
    octaveAccentColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val isBlack = note.contains("#")
    val hasAccent = octaveAccentColor.isSpecified

    // Cuánto se aclara/oscurece el color de octava para formar el barniz
    // vertical de la tecla activa -- lo bastante para que se note el
    // relieve de "superficie física", sin diluir tanto el acento que dos
    // octavas vecinas se vuelvan difíciles de distinguir a simple vista.
    val activeGradientTop = if (hasAccent) lerp(octaveAccentColor, Color.White, 0.30f) else null
    val activeGradientBottom = if (hasAccent) lerp(octaveAccentColor, Color.Black, 0.30f) else null

    val gradientTop = when {
        active && activeGradientTop != null -> activeGradientTop
        active && isBlack -> BlackKeyPressedGradientTop
        active -> WhiteKeyPressedGradientTop
        isBlack -> BlackKeyGradientTop
        else -> WhiteKeyGradientTop
    }
    val gradientBottom = when {
        active && activeGradientBottom != null -> activeGradientBottom
        active && isBlack -> BlackKeyPressedGradientBottom
        active -> WhiteKeyPressedGradientBottom
        isBlack -> BlackKeyGradientBottom
        else -> WhiteKeyGradientBottom
    }
    val animatedTop by animateColorAsState(targetValue = gradientTop, label = "pianoKeyGradientTop")
    val animatedBottom by animateColorAsState(targetValue = gradientBottom, label = "pianoKeyGradientBottom")

    val textColor = when {
        // Umbral de luminancia relativa estándar (WCAG-ish, 0.5) para elegir
        // texto oscuro sobre acentos claros y texto claro sobre los pocos
        // acentos oscuros de la paleta -- calculado sobre el color real, no
        // fijado por caso, así que sigue siendo correcto si la paleta de
        // OctaveColors cambia.
        active && hasAccent -> if (octaveAccentColor.luminance() > 0.5f) ActiveAccentTextDark else ActiveAccentTextLight
        active && isBlack -> BlackKeyPressedText
        isBlack -> BlackKeyText
        else -> WhiteKeyText
    }
    val sheenColor = if (isBlack) BlackKeySheen else WhiteKeySheen
    val borderColor = if (isBlack) BlackKeyBorder else WhiteKeyBorder

    val showOctaveStripe = isOctaveStart && hasAccent

    // Redondeada solo por abajo: la cara frontal de una tecla física no
    // tiene esquinas curvas arriba, solo donde el dedo la toca.
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 6.dp, bottomEnd = 6.dp)

    Box(
        modifier = modifier
            .width(PianoKeyWidth)
            .height(PianoKeyHeight)
            .clip(shape)
            .background(Brush.verticalGradient(colors = listOf(animatedTop, animatedBottom)))
            .border(1.dp, borderColor, shape)
    ) {
        // Veta de brillo cerca del borde superior -- simula la luz rebotando
        // en el canto de una tecla física; se desvanece hacia los lados para
        // no verse como una barra sólida.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(SheenHeight)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, sheenColor, Color.Transparent)
                    )
                )
        )

        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (isOctaveStart) 13.sp else MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = if (isOctaveStart) FontWeight.ExtraBold else FontWeight.Medium
            ),
            color = textColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = if (showOctaveStripe) OctaveStripeWidth + LabelStartGap else LabelStartGap)
        )

        // La banda de octava: solo en el Do de cada octava, una franja
        // vertical a todo lo alto pegada al borde lateral *izquierdo* de la
        // tecla -- el lado por el que se entra a una octava nueva recorriendo
        // el teclado de grave a agudo (ver Doc/30; antes era horizontal y
        // pegada abajo, corregido tras feedback directo del usuario).
        if (showOctaveStripe) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(OctaveStripeWidth)
                    .background(octaveAccentColor)
            )
        }
    }
}
