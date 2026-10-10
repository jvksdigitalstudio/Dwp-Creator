package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvk.dwpcreator.ui.state.SampleMixerState
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextDark
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextLight
import com.jvk.dwpcreator.ui.theme.MixerCaptionText
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerControlFill
import com.jvk.dwpcreator.ui.theme.MixerEndLabelText
import com.jvk.dwpcreator.ui.theme.MixerMuteActive
import com.jvk.dwpcreator.ui.theme.MixerSoloActive
import com.jvk.dwpcreator.ui.theme.MixerThumbHighlight
import com.jvk.dwpcreator.ui.theme.MixerThumbShadowTint
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientBottom
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientTop
import com.jvk.dwpcreator.ui.theme.MixerTrackSheen
import kotlin.math.roundToInt

/**
 * Franja de mini-controles de mezcla de una muestra -- Mute, Solo, Pan y
 * Volumen -- junto al **nombre** de cada muestra (lado izquierdo de la fila,
 * lejos de la tecla de piano -- corrección explícita tras feedback: la
 * primera versión la colocaba pegada a la tecla, a la derecha, y el usuario
 * pidió moverla al lado del nombre). Ver el KDoc de [SampleMixerState] para
 * el alcance exacto (solo afecta la previsualización en vivo, no se exporta
 * al `.dwp`).
 *
 * **Lenguaje visual: el mismo que las teclas del piano, no uno inventado
 * aparte.** Cada pieza (botón Mute/Solo, riel de Pan/Volumen, "thumb"
 * arrastrable) usa la misma receta que
 * [PianoKeyBadge][com.jvk.dwpcreator.ui.components.PianoKeyBadge]: un
 * degradado vertical de superficie (nunca un relleno plano) más una veta de
 * brillo horizontal cerca del borde superior que simula luz rebotando en un
 * canto físico, y el color de "activo" se deriva del tono base con `lerp`
 * (más claro arriba, más oscuro abajo) en vez de ser un color fijo aparte.
 * Mute/Solo, además, imitan un botón físico de consola: **elevados** (con
 * sombra) en reposo y **hundidos** (sin sombra, superficie más oscura) al
 * activarse -- así se "sienten" pulsados, no solo "coloreados". Usan iconos
 * reales (`VolumeOff`/`VolumeUp` para Mute, `Headset` para Solo -- el icono
 * estándar de "escuchar en solitario" en cualquier DAW) en vez de una letra
 * suelta. El "thumb" de Pan/Volumen brilla y crece ligeramente mientras se
 * arrastra, como retroalimentación táctil de que el dedo lo tiene agarrado.
 */
@Composable
fun SampleMixerStrip(
    state: SampleMixerState,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onPanChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MixerControlGap)
    ) {
        MixerToggleButton(
            icon = if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            active = state.muted,
            activeColor = MixerMuteActive,
            contentDescription = if (state.muted) "Quitar silencio a la muestra" else "Silenciar muestra",
            onClick = onToggleMute
        )
        MixerToggleButton(
            icon = Icons.Filled.Headset,
            active = state.solo,
            activeColor = MixerSoloActive,
            contentDescription = if (state.solo) "Quitar solo a la muestra" else "Poner la muestra en solo",
            onClick = onToggleSolo
        )
        MixerPanControl(
            pan = state.pan,
            onPanChange = onPanChange,
            modifier = Modifier.width(MixerPanWidth)
        )
        MixerVolumeControl(
            volume = state.volume,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.width(MixerVolumeWidth)
        )
    }
}

/** Diámetro de los botones circulares Mute/Solo -- objetivo táctil pequeño pero real (no un icono decorativo), pensado para un dedo en una franja compacta. */
private val MixerToggleDiameter = 24.dp

/** Alto de los rieles de Pan/Volumen -- fino, a juego con el resto de controles compactos de esta franja. */
private val MixerControlTrackHeight = 14.dp

/** Diámetro del "thumb" arrastrable de Pan/Volumen en reposo -- crece ligeramente mientras se arrastra, ver [DraggingThumbScale]. */
private val MixerThumbDiameter = 13.dp

/** Cuánto crece el "thumb" (factor de escala) mientras el dedo lo tiene agarrado -- retroalimentación táctil, no solo visual. */
private const val DraggingThumbScale = 1.35f

private val MixerPanWidth = 42.dp
private val MixerVolumeWidth = 50.dp
private val MixerControlGap = 6.dp

/** Cuánto se aclara/oscurece un tono base para formar su propio degradado vertical -- misma fracción que usa PianoKeyBadge para la tecla activa, por consistencia visual entre ambos. */
private const val GradientLightenFraction = 0.30f
private const val GradientDarkenFraction = 0.30f

/**
 * Elevación (en la animación de Mute/Solo) a la que corresponde el halo
 * "de fábrica" (botón en reposo) -- ver [softGlow]. Sustituye lo que antes
 * era el argumento de `Modifier.shadow(...)`; se conserva como `Dp`
 * animable para no perder la sensación de "se hunde al presionar" (el halo
 * se atenúa proporcionalmente, en vez de desaparecer de golpe).
 */
private val MixerToggleIdleElevation = 3.dp

/** Opacidad máxima del halo de Mute/Solo en reposo (100% de [MixerToggleIdleElevation]). */
private const val MixerToggleGlowAlpha = 0.5f

/** Opacidad del halo del "thumb" de Pan/Volumen -- constante (no depende de un estado presionado/reposo como Mute/Solo, solo de si se está arrastrando, ver [MixerThumb]). */
private const val MixerThumbGlowAlpha = 0.55f

/** Cuánto más grande que el propio control es el halo dibujado -- lo bastante para leerse como "luz alrededor", sin invadir controles vecinos en una franja tan compacta. */
private const val GlowExtraRadiusFraction = 0.65f

/**
 * Sustituto de una sombra dinámica real ([androidx.compose.ui.draw.shadow])
 * para el brillo "premium" de Mute/Solo y de los "thumbs" de Pan/Volumen.
 *
 * **Por qué existe esta función -- motivo real de rendimiento, no un
 * capricho estilístico.** El diseño original (`Doc/42`) usaba
 * `Modifier.shadow(elevation, CircleShape, clip = false, ambientColor =
 * ..., spotColor = ...)` en estos cuatro controles. Una sombra de Compose
 * con `clip = false` + `ambientColor`/`spotColor` PROPIOS (no el negro por
 * defecto) obliga a Android a componer esa sombra en una capa aparte,
 * calculada por software -- mucho más cara que la sombra de elevación
 * nativa barata que usa la plataforma normalmente. Esta franja monta CUATRO
 * de esos controles por fila (Mute, Solo, thumb de Pan, thumb de Volumen);
 * en una lista de 48 filas, con ~12-14 filas visibles/pre-cargadas a la vez
 * durante un scroll rápido (`LazyColumn`), eso son decenas de esas capas
 * recalculándose en cada frame de scroll -- la causa real y reproducible
 * del lag/retardo reportado por el usuario al deslizar el dedo por la
 * lista, no un problema difuso de "rendimiento en general".
 *
 * La corrección real (bajar el costo, no la calidad visual) es sustituir
 * esa sombra dinámica por un halo dibujado A MANO con
 * [androidx.compose.ui.draw.drawBehind]: un degradado radial que se
 * desvanece hacia fuera, pintado directamente en el mismo `DrawScope` del
 * control -- una operación de dibujo pura, sin capa de composición aparte,
 * sin que Android tenga que recalcular nada propio en cada frame. El
 * resultado percibido (un halo de color alrededor del control) es
 * prácticamente el mismo; el costo real de renderizado es una fracción del
 * de una sombra dinámica.
 */
private fun Modifier.softGlow(
    color: Color,
    alpha: Float,
    extraRadiusFraction: Float = GlowExtraRadiusFraction
): Modifier = this.drawBehind {
    if (alpha <= 0f) return@drawBehind
    val glowRadius = (size.minDimension / 2f) * (1f + extraRadiusFraction)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            radius = glowRadius,
            center = center
        ),
        radius = glowRadius,
        center = center
    )
}

@Composable
private fun MixerToggleButton(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    val baseTop = if (active) lerp(activeColor, Color.White, GradientLightenFraction) else MixerTrackGradientTop
    val baseBottom = if (active) lerp(activeColor, Color.Black, GradientDarkenFraction) else MixerTrackGradientBottom
    val animatedTop by animateColorAsState(baseTop, label = "mixerToggleTop")
    val animatedBottom by animateColorAsState(baseBottom, label = "mixerToggleBottom")
    // Botón físico de consola: elevado en reposo, hundido (halo atenuado) al
    // activarse -- se "siente" pulsado, no solo coloreado. La elevación ya
    // no alimenta una sombra real (ver KDoc de [softGlow]): se usa solo como
    // fracción para atenuar la opacidad del halo dibujado a mano.
    val elevation by animateDpAsState(if (active) 0.dp else MixerToggleIdleElevation, label = "mixerToggleElevation")
    val glowAlpha = (elevation / MixerToggleIdleElevation) * MixerToggleGlowAlpha
    val iconTint = when {
        active && activeColor.luminance() > 0.5f -> ActiveAccentTextDark
        active -> ActiveAccentTextLight
        else -> MixerCaptionText
    }

    Box(
        modifier = Modifier
            .size(MixerToggleDiameter)
            .softGlow(color = MixerThumbShadowTint, alpha = glowAlpha)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(animatedTop, animatedBottom)))
            .border(1.dp, if (active) activeColor else MixerControlBorder, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        // Veta de brillo superior -- misma receta que PianoKeyBadge (luz rebotando en un canto físico).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(MixerToggleDiameter / 3)
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, MixerTrackSheen, Color.Transparent))
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Riel horizontal con un "thumb" arrastrable que representa el paneo
 * (-1f..1f, centrado en 0f) -- arrastrar en cualquier punto del riel mueve
 * el thumb a esa posición X (no es un `delta` relativo: se salta
 * directamente a donde se toca/arrastra, más intuitivo en un control tan
 * pequeño). La marca vertical del centro es una referencia visual fija de
 * "pan = 0"; "L"/"R" al fondo del riel son una referencia de extremos, no
 * texto interactivo.
 */
/**
 * Gesto compartido de [MixerPanControl] y [MixerVolumeControl]: reacciona ya
 * en el primer toque (antes solo `detectHorizontalDragGestures`, que exige
 * superar el umbral de movimiento del sistema para siquiera empezar -- un
 * toque suelto y corto, sin arrastrar, no hacía nada) y sigue el dedo
 * mientras se mantenga presionado, igual que un fader real.
 */
private suspend fun PointerInputScope.detectTapAndHorizontalDrag(
    onDraggingChange: (Boolean) -> Unit,
    onPositionChange: (xPx: Float, widthPx: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val widthPx = size.width.toFloat()
        onDraggingChange(true)
        if (widthPx > 0f) onPositionChange(down.position.x, widthPx)
        val pointerId = down.id
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) break
            if (widthPx > 0f) onPositionChange(change.position.x, widthPx)
            change.consume()
        }
        onDraggingChange(false)
    }
}

@Composable
private fun MixerPanControl(pan: Float, onPanChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    // `pointerInput(Unit)` no se reinicia nunca: sin esto capturaría el PRIMER
    // `onPanChange` recibido y seguiría llamándolo aunque la fila recomponga con
    // otro (mismo patrón que ya usa `PianoKeyGestureOverlay` en MainScreen).
    val currentOnPanChange by rememberUpdatedState(onPanChange)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { MixerThumbDiameter.toPx() }
    val fraction = ((pan.coerceIn(-1f, 1f) + 1f) / 2f)
    val maxOffsetPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    val thumbScale by animateFloatAsState(if (dragging) DraggingThumbScale else 1f, label = "panThumbScale")

    MixerTrackSurface(
        modifier = modifier
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapAndHorizontalDrag(
                    onDraggingChange = { dragging = it },
                    onPositionChange = { xPx, widthPx -> currentOnPanChange(xToPan(xPx, widthPx)) }
                )
            }
            .semantics { contentDescription = "Paneo de la muestra" }
    ) {
        Text("L", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
        Text("R", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
        // Marca fija del centro (pan = 0), de referencia visual.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(MixerControlBorder)
        )
        MixerThumb(offsetPx = fraction * maxOffsetPx, scale = thumbScale, glowColor = MixerControlFill)
    }
}

/**
 * Riel horizontal con un "thumb" arrastrable y un relleno progresivo con
 * degradado (estilo fader de consola real, no una barra sólida) que
 * representa el volumen (0f..1f). Igual que [MixerPanControl], arrastrar
 * salta directamente a la posición X tocada.
 */
@Composable
private fun MixerVolumeControl(volume: Float, onVolumeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    // Ver el comentario equivalente en [MixerPanControl].
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbSizePx = with(density) { MixerThumbDiameter.toPx() }
    val fraction = volume.coerceIn(0f, 1f)
    val maxOffsetPx = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    val thumbScale by animateFloatAsState(if (dragging) DraggingThumbScale else 1f, label = "volumeThumbScale")

    MixerTrackSurface(
        modifier = modifier
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapAndHorizontalDrag(
                    onDraggingChange = { dragging = it },
                    onPositionChange = { xPx, widthPx -> currentOnVolumeChange(xToFraction(xPx, widthPx)) }
                )
            }
            .semantics { contentDescription = "Volumen de la muestra" }
    ) {
        Text("−", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
        Text("+", style = microLabelStyle(), color = MixerEndLabelText, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
        // Relleno tipo fader: degradado, no un color sólido -- se nota más
        // "cargado" hacia el extremo derecho a medida que sube el volumen,
        // igual que un fader iluminado de una consola real.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(MixerControlFill, Color.Black, 0.35f), MixerControlFill)
                    )
                )
        )
        MixerThumb(offsetPx = fraction * maxOffsetPx, scale = thumbScale, glowColor = MixerControlFill)
    }
}

/**
 * Superficie compartida de los rieles de Pan/Volumen: degradado vertical +
 * veta de brillo superior, misma receta que las teclas del piano y los
 * botones Mute/Solo -- una sola definición para que los tres controles de
 * esta franja envejezcan visualmente juntos si el degradado cambia más
 * adelante. `content` recibe [BoxScope] -- el mismo receptor que tendría el
 * cuerpo de un `Box { ... }` normal -- para que Pan/Volumen puedan seguir
 * usando `Modifier.align(...)` en sus hijos sin ninguna indirección extra.
 */
@Composable
private fun MixerTrackSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .height(MixerControlTrackHeight)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(MixerTrackGradientTop, MixerTrackGradientBottom)))
            .border(1.dp, MixerControlBorder, RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(MixerControlTrackHeight / 3)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, MixerTrackSheen, Color.Transparent)))
        )
        content()
    }
}

/**
 * El "thumb" arrastrable compartido por Pan y Volumen: un pequeño disco con
 * relieve (degradado radial claro -> color) y un halo propio ([softGlow] --
 * ver su KDoc para el porqué de un halo dibujado a mano en vez de una
 * sombra real) en vez de un círculo plano de un solo color, que además
 * crece ([scale]) mientras el dedo lo arrastra -- la retroalimentación
 * táctil de "lo tengo agarrado" de cualquier control profesional. Siempre
 * anclado a [Alignment.CenterStart] de su [BoxScope] contenedor (Pan y
 * Volumen miden la posición desde el borde izquierdo del riel) y
 * desplazado por [offsetPx].
 */
@Composable
private fun BoxScope.MixerThumb(offsetPx: Float, scale: Float, glowColor: Color) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(offsetPx.roundToInt(), 0) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(MixerThumbDiameter)
            .softGlow(color = glowColor, alpha = MixerThumbGlowAlpha)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(MixerThumbHighlight, glowColor),
                    radius = with(density) { MixerThumbDiameter.toPx() }
                )
            )
    )
}

@Composable
private fun microLabelStyle() = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold)

/** Posición X (px, dentro del propio riel) -> fracción 0f..1f, saturada a los extremos del riel. */
private fun xToFraction(xPx: Float, widthPx: Float): Float =
    if (widthPx <= 0f) 0f else (xPx / widthPx).coerceIn(0f, 1f)

/** Posición X (px, dentro del propio riel) -> pan -1f..1f, saturada a los extremos del riel. */
private fun xToPan(xPx: Float, widthPx: Float): Float = (xToFraction(xPx, widthPx) * 2f) - 1f
