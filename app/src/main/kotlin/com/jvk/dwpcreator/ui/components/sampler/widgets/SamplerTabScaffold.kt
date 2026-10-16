package com.jvk.dwpcreator.ui.components.sampler.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Andamiaje común de todas las pestañas del panel del sampler: las tarjetas
 * ([FxModuleCard]) fluyen en filas y saltan de línea según el ancho disponible
 * (móvil estrecho o tablet ancha); el conjunto se desplaza en vertical cuando no
 * cabe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SamplerTabScaffold(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
