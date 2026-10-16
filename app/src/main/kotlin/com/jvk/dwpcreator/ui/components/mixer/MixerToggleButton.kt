package com.jvk.dwpcreator.ui.components.mixer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextDark
import com.jvk.dwpcreator.ui.theme.ActiveAccentTextLight
import com.jvk.dwpcreator.ui.theme.MixerCaptionText
import com.jvk.dwpcreator.ui.theme.MixerControlBorder
import com.jvk.dwpcreator.ui.theme.MixerThumbShadowTint
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientBottom
import com.jvk.dwpcreator.ui.theme.MixerTrackGradientTop
import com.jvk.dwpcreator.ui.theme.MixerTrackSheen

/** Diámetro de los botones circulares Mute/Solo -- objetivo táctil pequeño pero real (no un icono decorativo), pensado para un dedo en una franja compacta. */
private val MixerToggleDiameter = 24.dp

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

@Composable
internal fun MixerToggleButton(
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
