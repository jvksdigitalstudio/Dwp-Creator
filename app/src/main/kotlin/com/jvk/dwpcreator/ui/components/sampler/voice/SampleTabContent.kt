package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.FxToggle
import com.jvk.dwpcreator.sampler.api.params.SampleControls
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxModuleCard
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxPresetSelector
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerChoiceChip
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerParamKnob
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerTabScaffold
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerToggleSegmented
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Pestaña SAMPLE: cómo se reproduce la muestra de cada nota -- afinación, inicio,
 * bucle, dirección, sensibilidad a la velocidad y modo de ejecución (poly, mono,
 * legato y glide). Un único módulo ([FxModule.SAMPLE]) con un único reinicio.
 */
@Composable
fun SampleTabContent(state: SamplerFxState, controller: SamplerFxController, modifier: Modifier = Modifier) {
    SamplerTabScaffold(modifier = modifier) {
        FxModuleCard(
            title = "SAMPLE",
            powered = null,
            onPoweredChange = {},
            headerExtras = { FxPresetSelector(FxModule.SAMPLE, state, controller) }
        ) {
            SamplerParamKnob(SampleControls.PITCH, state, controller)
            SamplerParamKnob(SampleControls.FINE, state, controller)
            SamplerParamKnob(SampleControls.PITCH_TRACK, state, controller)
            SamplerParamKnob(SampleControls.BEND_RANGE, state, controller)
            SamplerParamKnob(SampleControls.START, state, controller)
            SamplerParamKnob(SampleControls.VELOCITY_SENSITIVITY, state, controller)
            SamplerChoiceChip(SampleControls.LOOP_MODE, state, controller)
            SamplerToggleSegmented(SampleControls.REVERSE, state, controller, "FORWARD", "REVERSE")
            SamplerToggleSegmented(FxToggle.MONOPHONIC, state, controller, "POLY", "MONO")
            SamplerToggleSegmented(SampleControls.LEGATO, state, controller, "RETRIGGER", "LEGATO")
            SamplerParamKnob(SampleControls.GLIDE, state, controller)
            SamplerToggleSegmented(SampleControls.GLIDE_ONLY_LEGATO, state, controller, "ALWAYS", "LEGATO ONLY")
        }
    }
}
