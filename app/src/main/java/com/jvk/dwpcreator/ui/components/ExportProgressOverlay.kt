package com.jvk.dwpcreator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvk.dwpcreator.ui.state.DwpUiState
import com.jvk.dwpcreator.ui.theme.BgDark
import com.jvk.dwpcreator.ui.theme.NeonCyan
import com.jvk.dwpcreator.ui.theme.NeonPurple
import com.jvk.dwpcreator.ui.theme.SurfacePurpleAlt
import com.jvk.dwpcreator.ui.theme.TextDim

/**
 * Overlay de progreso mostrado mientras `exportTo`/`exportMonolithicTo`
 * están en curso ([DwpCreatorViewModel][com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel]).
 *
 * Antes de este componente, `isExporting` era un `Boolean` que **nunca se
 * renderizaba en ningún sitio** -- pulsar exportar no mostraba nada hasta
 * que el picker de guardado volvía a aparecer; en un proyecto de 48
 * muestras (el fixture real de este proyecto) eso podían ser varios
 * segundos de pantalla aparentemente congelada.
 *
 * El progreso que muestra es real, no una animación simulada: cada avance
 * de la barra corresponde a una muestra que `ZipProjectExporter`/
 * `MonolithicDwpProjectBuilder` acaba de terminar de procesar de verdad
 * (ver el parámetro `onProgress` de ambos). La barra se suaviza con
 * `animateFloatAsState` únicamente para que, cuando el procesamiento real
 * de una muestra es más rápido que un frame, el salto entre valores se vea
 * como un movimiento fluido en vez de un parpadeo -- no oculta ni
 * ralentiza artificialmente el trabajo real.
 */
@Composable
fun ExportProgressOverlay(progress: DwpUiState.ExportProgress, modifier: Modifier = Modifier) {
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(durationMillis = 260, easing = EqualizerEase),
        label = "exportProgressFraction"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EqualizerBars()

        Spacer(Modifier.height(28.dp))
        Text(
            text = progress.stageLabel,
            style = MaterialTheme.typography.titleMedium,
            color = NeonPurple
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = progress.sampleName.ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfacePurpleAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(NeonPurple, NeonCyan)))
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "${progress.current} / ${progress.total} · ${(progress.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}
