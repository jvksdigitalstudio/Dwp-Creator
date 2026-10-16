package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.params.FilterControls
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxModuleCard
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxPresetSelector
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerChoiceChip
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerParamKnob
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerTabScaffold
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Pestaña FILTER: el filtro de voz con su curva de respuesta en vivo, modo,
 * corte, resonancia, drive y seguimiento de teclado y velocidad.
 */
@Composable
fun FilterTabContent(state: SamplerFxState, controller: SamplerFxController, modifier: Modifier = Modifier) {
    val filter = state.voice.filter
    SamplerTabScaffold(modifier = modifier) {
        FxModuleCard(
            title = "FILTER",
            powered = filter.enabled,
            onPoweredChange = { controller.onToggle(FilterControls.ENABLED, it) },
            headerExtras = { FxPresetSelector(FxModule.FILTER, state, controller) },
            preview = { FilterResponseCurve(filter) }
        ) {
            SamplerChoiceChip(FilterControls.MODE, state, controller)
            SamplerParamKnob(FilterControls.CUTOFF, state, controller, filter.enabled)
            SamplerParamKnob(FilterControls.RESONANCE, state, controller, filter.enabled)
            SamplerParamKnob(FilterControls.DRIVE, state, controller, filter.enabled)
            SamplerParamKnob(FilterControls.KEY_TRACK, state, controller, filter.enabled)
            SamplerParamKnob(FilterControls.VELOCITY_TRACK, state, controller, filter.enabled)
        }
    }
}
