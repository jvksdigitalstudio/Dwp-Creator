package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import com.jvk.dwpcreator.sampler.api.params.EnvelopeParams
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxModuleCard
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxPresetSelector
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerParamKnob
import com.jvk.dwpcreator.ui.state.SamplerFxController

/** Tarjeta de una envolvente. [amount] es `null` en la de amplitud (siempre al 100 %). */
@Composable
internal fun EnvelopeCard(
    module: FxModule,
    params: EnvelopeParams,
    adsr: AdsrState,
    amount: Float?,
    state: SamplerFxState,
    controller: SamplerFxController
) {
    FxModuleCard(
        title = module.title,
        powered = null,
        onPoweredChange = {},
        headerExtras = { FxPresetSelector(module, state, controller) },
        preview = { AdsrCurve(adsr, amount = amount ?: 1f) }
    ) {
        for (param in params.params) SamplerParamKnob(param, state, controller)
    }
}
