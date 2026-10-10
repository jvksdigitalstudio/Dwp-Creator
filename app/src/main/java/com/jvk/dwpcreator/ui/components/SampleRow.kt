package com.jvk.dwpcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.domain.dwp.SampleInfo
import com.jvk.dwpcreator.ui.state.SampleMixerState
import com.jvk.dwpcreator.ui.theme.NameMixerColumnDivider
import com.jvk.dwpcreator.ui.theme.SampleRowDivider
import com.jvk.dwpcreator.ui.theme.SurfacePurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt

/**
 * Padding horizontal de cada fila -- fuente única de verdad, reutilizada
 * por [MainScreen][com.jvk.dwpcreator.ui.screens.MainScreen] para calcular
 * el ancho exacto de la columna de teclas (banda derecha) al implementar
 * el overlay de gestos multi-touch. No duplicar este número.
 */
val SampleRowEndPadding = 16.dp

/**
 * Grosor del separador entre filas ([SampleRowDivider]) -- fino a propósito
 * ("delgada, no gruesa", pedido explícito del usuario), suficiente para
 * marcar el límite entre dos muestras sin competir visualmente con el
 * degradado de las teclas.
 */
private val DividerThickness = 1.dp

/** Separación horizontal a ambos lados de la franja de mezclador (entre el nombre y el mezclador, y entre este y la tecla de piano). */
private val MixerStripSpacing = 8.dp

/** Grosor de la línea vertical divisoria entre el nombre y la franja de mezclador -- fina, a juego con [DividerThickness] (la horizontal entre filas). */
private val ColumnDividerThickness = 1.dp

/**
 * Alto real de la línea divisoria vertical.
 *
 * OJO -- no usar `Modifier.fillMaxHeight()` aquí: esta fila vive dentro de
 * un `LazyColumn` (ver `MainScreen`), que mide cada item con una altura
 * MÁXIMA NO ACOTADA (crece según su propio contenido, no hay un alto de
 * pantalla fijo que "llenar"). Con constraints de altura infinitos,
 * `fillMaxHeight()` no "falla" de forma ruidosa -- se resuelve
 * silenciosamente al alto MÍNIMO (0dp), y la línea, aunque compile y se vea
 * bien en un preview aislado con altura acotada, sería invisible en la app
 * real. Por eso el alto se ancla a [PianoKeyHeight] -- la misma fuente
 * única de verdad que ya fija el alto real de esta fila a través de
 * [PianoKeyBadge] -- menos el margen vertical a cada lado
 * ([ColumnDividerVerticalInset]).
 */
private val ColumnDividerVerticalInset = 6.dp

/**
 * Ancho de la columna del nombre -- **fijo e idéntico en todas las filas**,
 * nunca calculado fila a fila a partir del propio texto.
 *
 * Historial del bug real (capturas anotadas del usuario, layout "roto"):
 * primero se usó `Modifier.weight(1f)` en el nombre, que reclamaba TODO el
 * espacio sobrante de la fila y empujaba el mezclador contra la tecla de
 * piano. La corrección siguiente cambió a `Modifier.widthIn(max = 150.dp)`
 * -- un tope fijo "adivinado" que:
 *  1. Cortaba con "…" cualquier nombre real más largo que 150dp (p. ej.
 *     `INSTRUMENT_C3_127` en mayúsculas/negrita, la etiqueta de inicio de
 *     octava, que a 16sp monospace en negrita ya no entra en ese ancho).
 *  2. Al ser un *máximo* y no un ancho fijo, cada fila con un nombre más
 *     corto encogía su columna a su propio contenido -- por eso el
 *     mezclador (pegado inmediatamente después del nombre) empezaba en una
 *     posición X distinta en cada fila, y la lista se veía "desalineada"
 *     verticalmente en vez de en columnas rectas.
 *
 * La corrección real (no un parche sobre el número 150) es medir con
 * [androidx.compose.ui.text.TextMeasurer] el ancho que de verdad necesita el
 * nombre más largo del instrumento cargado -- en las DOS tipografías reales
 * que puede usar esta fila (ver `isOctaveStart` en [SampleRow]) -- y pasar
 * ese resultado como [Dp] fijo a **todas** las filas via [nameColumnWidth].
 * Así ningún nombre real se corta y la columna queda perfectamente alineada
 * en vertical, fila tras fila, sin volver a adivinar un número.
 *
 * [NameColumnMinWidth]/[NameColumnMaxWidth] acotan ese cálculo: el mínimo
 * evita una columna ridículamente angosta si todos los nombres son cortos,
 * y el máximo es la última red de seguridad para un nombre personalizado
 * patológicamente largo (tras un renombrado manual) -- ahí, y solo ahí,
 * sigue aplicando `TextOverflow.Ellipsis` como resguardo, nunca como
 * mecanismo principal.
 */
val NameColumnMinWidth = 110.dp
val NameColumnMaxWidth = 220.dp

/** Ancho de columna de nombre usado solo como valor por defecto del parámetro `nameColumnWidth` de [SampleRow] -- antes de que [MainScreen][com.jvk.dwpcreator.ui.screens.MainScreen] calcule el ancho real, o en un preview aislado de esta fila. */
private val NameColumnFallbackWidth = 150.dp

@Composable
fun SampleRow(
    sample: SampleInfo,
    accentColor: Color,
    isPlaying: Boolean = false,
    mixerState: SampleMixerState = SampleMixerState.DEFAULT,
    onPreview: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleSolo: () -> Unit = {},
    onPanChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    // Ancho FIJO de columna, igual para todas las filas de la lista --
    // calculado una sola vez en `MainScreen` a partir del nombre real más
    // largo del instrumento cargado (ver KDoc de [NameColumnMinWidth]).
    // El valor por defecto solo cubre previews aislados de esta fila.
    nameColumnWidth: Dp = NameColumnFallbackWidth,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (sample.isOctaveStart) SurfacePurpleAlt else SurfacePurple)
            // Se dibuja ANTES de aplicar el padding horizontal de más abajo
            // -- en este punto de la cadena, `size` todavía es el ancho
            // COMPLETO de la fila (borde a borde de la pantalla), que es
            // exactamente el separador "de extremo a extremo" pedido, sin
            // que el padding interno del contenido (número/nombre/tecla) lo
            // recorte por los costados.
            .drawWithContent {
                drawContent()
                val strokePx = DividerThickness.toPx()
                drawLine(
                    color = SampleRowDivider,
                    start = Offset(0f, size.height - strokePx / 2f),
                    end = Offset(size.width, size.height - strokePx / 2f),
                    strokeWidth = strokePx
                )
            }
            .clickable(onClick = onPreview)
            .padding(horizontal = SampleRowEndPadding, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (sample.index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.width(34.dp)
        )

        Text(
            text = sample.name.uppercase().takeIf { sample.isOctaveStart } ?: sample.name,
            style = if (sample.isOctaveStart) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (sample.isOctaveStart) FontWeight.ExtraBold else FontWeight.Normal,
            color = accentColor,
            // Ancho FIJO (no `weight`, no `widthIn(max = ...)`) -- ver KDoc
            // de [NameColumnMinWidth]. `weight` le daría todo el espacio
            // sobrante de la fila y empujaría el mezclador contra la tecla
            // de piano; un tope variable (`widthIn(max = ...)`) encoge la
            // columna al contenido de cada fila y desalinea el mezclador
            // fila a fila. Con `width()` fijo, todas las filas reservan
            // exactamente el mismo ancho -- calculado en `MainScreen` para
            // que el nombre real más largo entre sin recortarse -- así que
            // `TextOverflow.Ellipsis` queda solo como resguardo ante un
            // renombrado manual patológicamente largo, no como mecanismo
            // principal.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(nameColumnWidth)
        )

        // Línea vertical fina que separa el NOMBRE de la franja de
        // mini-mezclador -- pedido explícito del usuario (captura anotada a
        // mano): sin ella, nombre y controles se leían como un solo bloque
        // pegado. Alto FIJO ([PianoKeyHeight] menos el margen), nunca
        // `fillMaxHeight()` -- ver el KDoc completo de
        // [ColumnDividerVerticalInset] sobre por qué eso se colapsaría a
        // invisible dentro del `LazyColumn` real. Igual de discreta que el
        // resto de líneas divisorias de la app pero un poco más visible (ver
        // KDoc de [NameMixerColumnDivider]) por ser corta y necesitar
        // notarse como límite de columna de un vistazo.
        Box(
            modifier = Modifier
                .padding(horizontal = MixerStripSpacing / 2)
                .width(ColumnDividerThickness)
                .height(PianoKeyHeight - ColumnDividerVerticalInset * 2)
                .background(NameMixerColumnDivider)
        )

        // Franja de mezclador (Mute/Solo/Pan/Volumen): junto al nombre --
        // separada por la línea divisoria de arriba, no por un hueco vacío
        // -- "al lado de los nombres", pedido explícito del usuario. El
        // espacio realmente sobrante de la fila lo absorbe el `Spacer` de
        // más abajo, no el nombre ni el mezclador.
        SampleMixerStrip(
            state = mixerState,
            onToggleMute = onToggleMute,
            onToggleSolo = onToggleSolo,
            onPanChange = onPanChange,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.padding(start = MixerStripSpacing / 2)
        )

        // Absorbe TODO el espacio sobrante de la fila -- es este Spacer, y
        // no el nombre, el que empuja la tecla de piano a su columna fija del
        // borde derecho (la misma en todas las filas, ver `PianoKeyWidth` +
        // `SampleRowEndPadding` en `MainScreen`). El nombre y el mezclador
        // quedan así agrupados y pegados entre sí del lado izquierdo, sin
        // importar cuánto (o poco) mida el nombre de cada muestra.
        Spacer(modifier = Modifier.weight(1f))

        PianoKeyBadge(
            note = sample.note,
            active = isPlaying,
            isOctaveStart = sample.isOctaveStart,
            octaveAccentColor = accentColor
        )
    }
}
