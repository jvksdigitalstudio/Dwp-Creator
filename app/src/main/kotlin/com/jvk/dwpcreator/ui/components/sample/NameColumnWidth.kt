package com.jvk.dwpcreator.ui.components.sample

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import com.jvk.dwpcreator.dwp.SampleInfo

/**
 * Ancho fijo de la columna de nombre, **idéntico para todas las filas** de
 * la lista de muestras -- calculado, no adivinado. Ver el KDoc completo de
 * [com.jvk.dwpcreator.ui.components.sample.NameColumnMinWidth] para el porqué
 * (bug real de texto cortado + mezclador desalineado fila a fila que este
 * cálculo reemplaza).
 *
 * Mide con [rememberTextMeasurer] -- la tipografía REAL de cada tipo de
 * fila (negrita/mayúscula en el Do de cada octava vs. normal en el resto,
 * exactamente como las renderiza [SampleRow][com.jvk.dwpcreator.ui.components.sample.SampleRow])
 * -- el ancho en píxeles que ocupa cada nombre del instrumento cargado, se
 * queda con el más ancho de todos, y lo acota entre
 * [NameColumnMinWidth]/[NameColumnMaxWidth] para no dejar la columna
 * ridículamente angosta (todos los nombres cortos) ni permitir que un
 * nombre patológicamente largo (renombrado manual) le robe demasiado
 * espacio al mezclador y a la tecla de piano en un teléfono angosto.
 *
 * Recalcula solo cuando la LISTA de muestras cambia (`remember(samples)`)
 * -- no en cada recomposición por otros motivos (nota sonando, mezclador
 * arrastrado, etc.) -- porque los nombres de un instrumento ya cargado no
 * cambian salvo un renombrado explícito, y ese renombrado sí reemplaza la
 * lista (`samples`), disparando el recálculo correctamente.
 */
@Composable
internal fun rememberNameColumnWidth(samples: List<SampleInfo>): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val octaveStartStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold)
    val regularStyle = MaterialTheme.typography.bodyMedium

    return remember(samples, octaveStartStyle, regularStyle, density) {
        val widestNamePx = samples.maxOfOrNull { sample ->
            val text = if (sample.isOctaveStart) sample.name.uppercase() else sample.name
            val style = if (sample.isOctaveStart) octaveStartStyle else regularStyle
            textMeasurer.measure(text = text, style = style, maxLines = 1).size.width
        } ?: 0
        with(density) { widestNamePx.toDp() }.coerceIn(NameColumnMinWidth, NameColumnMaxWidth)
    }
}
