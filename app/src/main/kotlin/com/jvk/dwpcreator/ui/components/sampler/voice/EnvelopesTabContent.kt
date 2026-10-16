package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.params.EnvelopeControls
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerTabScaffold
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Pestaña ENVELOPES: las tres envolventes de voz (amplitud, filtro y afinación)
 * como tarjetas idénticas -- curva ADSR en vivo y sus controles --; las dos de
 * modulación añaden su cantidad bipolar.
 */
@Composable
fun EnvelopesTabContent(state: SamplerFxState, controller: SamplerFxController, modifier: Modifier = Modifier) {
    val voice = state.voice
    SamplerTabScaffold(modifier = modifier) {
        EnvelopeCard(FxModule.AMP_ENV, EnvelopeControls.AMP, voice.ampEnvelope, null, state, controller)
        EnvelopeCard(
            FxModule.FILTER_ENV, EnvelopeControls.FILTER,
            voice.filterEnvelope.adsr, voice.filterEnvelope.amount, state, controller
        )
        EnvelopeCard(
            FxModule.PITCH_ENV, EnvelopeControls.PITCH,
            voice.pitchEnvelope.adsr, voice.pitchEnvelope.amount, state, controller
        )
    }
}
