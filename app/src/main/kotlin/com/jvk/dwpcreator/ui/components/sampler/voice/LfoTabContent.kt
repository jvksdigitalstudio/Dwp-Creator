package com.jvk.dwpcreator.ui.components.sampler.voice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.LfoControls
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerTabScaffold
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Pestaña LFO: dos LFO por voz, cada uno con su forma de onda en vivo, modo de
 * velocidad (Hz, tempo o one-shot), forma, destino, fase, fundido de entrada y
 * profundidad bipolar. Con el modo tempo, el knob de frecuencia se sustituye por
 * la división rítmica y el BPM compartido.
 */
@Composable
fun LfoTabContent(state: SamplerFxState, controller: SamplerFxController, modifier: Modifier = Modifier) {
    SamplerTabScaffold(modifier = modifier) {
        LfoCard(FxModule.LFO_1, LfoControls.LFO_1, state.voice.lfo1, state, controller)
        LfoCard(FxModule.LFO_2, LfoControls.LFO_2, state.voice.lfo2, state, controller)
    }
}
