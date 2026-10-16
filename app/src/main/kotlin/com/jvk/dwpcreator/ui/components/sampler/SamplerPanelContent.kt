package com.jvk.dwpcreator.ui.components.sampler

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.voice.EnvelopesTabContent
import com.jvk.dwpcreator.ui.components.sampler.voice.FilterTabContent
import com.jvk.dwpcreator.ui.components.sampler.voice.LfoTabContent
import com.jvk.dwpcreator.ui.components.sampler.voice.SampleTabContent
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Cuerpo del panel SAMPLER: barra de pestañas y el contenido de la pestaña
 * elegida. La única pieza de estado de la UI es la pestaña visible (se conserva
 * al rotar); todo lo demás es función pura de [state].
 */
@Composable
fun SamplerPanelContent(
    state: SamplerFxState,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val tab = SamplerPanelTab.entries[selectedIndex.coerceIn(0, SamplerPanelTab.entries.size - 1)]
    Column(modifier = modifier) {
        SamplerPanelTabBar(selected = tab, onSelect = { selectedIndex = it.ordinal })
        when (tab) {
            SamplerPanelTab.SAMPLE -> SampleTabContent(state, controller, Modifier.weight(1f))
            SamplerPanelTab.FILTER -> FilterTabContent(state, controller, Modifier.weight(1f))
            SamplerPanelTab.ENVELOPES -> EnvelopesTabContent(state, controller, Modifier.weight(1f))
            SamplerPanelTab.LFO -> LfoTabContent(state, controller, Modifier.weight(1f))
            SamplerPanelTab.FX -> SamplerFxPanelContent(state, controller, Modifier.weight(1f))
        }
    }
}
