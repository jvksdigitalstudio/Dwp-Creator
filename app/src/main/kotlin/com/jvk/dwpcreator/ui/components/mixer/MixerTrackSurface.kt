package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientBottom
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientTop
import com.jvk.dwpcreator.ui.theme.MixerTrackSheen

/** Alto de los rieles de Pan/Volumen -- fino, a juego con el resto de controles compactos de esta franja. */
private val MixerControlTrackHeight = 14.dp

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
internal fun MixerTrackSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
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
