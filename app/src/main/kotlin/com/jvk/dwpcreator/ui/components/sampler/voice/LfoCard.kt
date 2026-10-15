package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.LfoControls
import com.jvk.dwpcreator.sampler.api.params.LfoParams
import com.jvk.dwpcreator.sampler.api.state.LfoRateMode
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxModuleCard
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxPresetSelector
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerChoiceChip
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerParamKnob
import com.jvk.dwpcreator.ui.state.SamplerFxController

@Composable
internal fun LfoCard(
    module: FxModule,
    params: LfoParams,
    lfo: LfoState,
    state: SamplerFxState,
    controller: SamplerFxController
) {
    FxModuleCard(
        title = module.title,
        powered = null,
        onPoweredChange = {},
        headerExtras = { FxPresetSelector(module, state, controller) },
        preview = { LfoWavePreview(lfo) }
    ) {
        SamplerChoiceChip(params.mode, state, controller)
        SamplerChoiceChip(params.shape, state, controller)
        SamplerChoiceChip(params.destination, state, controller)
        if (lfo.rateMode == LfoRateMode.TEMPO) {
            SamplerParamKnob(params.division, state, controller)
            SamplerParamKnob(LfoControls.TEMPO, state, controller)
        } else {
            SamplerParamKnob(params.rate, state, controller)
        }
        SamplerParamKnob(params.phase, state, controller)
        SamplerParamKnob(params.fade, state, controller)
        SamplerParamKnob(params.amount, state, controller)
    }
}
