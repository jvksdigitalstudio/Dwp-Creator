package com.jvk.dwpcreator.ui.components.sampler

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.FxParam
import com.jvk.dwpcreator.sampler.api.params.FxToggle
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxModuleCard
import com.jvk.dwpcreator.ui.components.sampler.widgets.FxPresetSelector
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerParamKnob
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerTabScaffold
import com.jvk.dwpcreator.ui.components.sampler.widgets.SamplerToggleSegmented
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Pestaña FX del panel SAMPLER: los módulos del bus maestro, organizados como la
 * pestaña PROGRAM de DirectWave (MASTER, FX DRIVE A, FX DRIVE B, FX DELAY, FX
 * REVERB, FX CHORUS).
 *
 * Todo el contenido es una función pura de [state]: la UI no guarda estado
 * propio de los efectos.
 */
@Composable
fun SamplerFxPanelContent(
    state: SamplerFxState,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    SamplerTabScaffold(modifier = modifier) {
        FxModuleCard(title = "MASTER", powered = null, onPoweredChange = {}) {
            SamplerToggleSegmented(FxToggle.MONOPHONIC, state, controller, "POLY", "MONO")
            SamplerParamKnob(FxParam.MASTER_VOLUME, state, controller, moduleEnabled = true)
        }

        FxModuleCard(
            title = "FX DRIVE A",
            powered = state.driveAEnabled,
            onPoweredChange = { controller.onToggle(FxToggle.DRIVE_A, it) },
            headerExtras = { FxPresetSelector(FxModule.DRIVE_A, state, controller) }
        ) {
            SamplerParamKnob(FxParam.DRIVE_A_AMOUNT, state, controller, state.driveAEnabled)
            SamplerParamKnob(FxParam.DRIVE_A_TONE, state, controller, state.driveAEnabled)
        }

        FxModuleCard(
            title = "FX DRIVE B",
            powered = state.driveBEnabled,
            onPoweredChange = { controller.onToggle(FxToggle.DRIVE_B, it) },
            headerExtras = { FxPresetSelector(FxModule.DRIVE_B, state, controller) }
        ) {
            SamplerParamKnob(FxParam.DRIVE_B_AMOUNT, state, controller, state.driveBEnabled)
            SamplerParamKnob(FxParam.DRIVE_B_TONE, state, controller, state.driveBEnabled)
        }

        FxModuleCard(
            title = "FX DELAY",
            powered = state.delayEnabled,
            onPoweredChange = { controller.onToggle(FxToggle.DELAY, it) },
            headerExtras = { FxPresetSelector(FxModule.DELAY, state, controller) }
        ) {
            // Con SYNC el tiempo lo fijan BPM + división; sin SYNC, el knob TIME en ms.
            if (state.delaySync) {
                SamplerParamKnob(FxParam.DELAY_DIVISION, state, controller, state.delayEnabled)
                SamplerParamKnob(FxParam.DELAY_BPM, state, controller, state.delayEnabled)
            } else {
                SamplerParamKnob(FxParam.DELAY_TIME, state, controller, state.delayEnabled)
            }
            SamplerParamKnob(FxParam.DELAY_FEEDBACK, state, controller, state.delayEnabled)
            SamplerParamKnob(FxParam.DELAY_LOW_CUT, state, controller, state.delayEnabled)
            SamplerParamKnob(FxParam.DELAY_HIGH_CUT, state, controller, state.delayEnabled)
            // WOW sólo actúa en modo cinta: se atenúa cuando no.
            SamplerParamKnob(FxParam.DELAY_WOW, state, controller, state.delayEnabled && state.delayTape)
            SamplerParamKnob(FxParam.DELAY_MIX, state, controller, state.delayEnabled)
            SamplerToggleSegmented(FxToggle.DELAY_BOUNCE, state, controller, "NORMAL", "BOUNCE")
            SamplerToggleSegmented(FxToggle.DELAY_SYNC, state, controller, "FREE", "SYNC")
            SamplerToggleSegmented(FxToggle.DELAY_TAPE, state, controller, "CLEAN", "TAPE")
        }

        FxModuleCard(
            title = "FX REVERB",
            powered = state.reverbEnabled,
            onPoweredChange = { controller.onToggle(FxToggle.REVERB, it) },
            headerExtras = { FxPresetSelector(FxModule.REVERB, state, controller) }
        ) {
            SamplerParamKnob(FxParam.REVERB_ROOM, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_DAMP, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_DIFFUSION, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_DECAY, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_PREDELAY, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_MODULATION, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_WIDTH, state, controller, state.reverbEnabled)
            SamplerParamKnob(FxParam.REVERB_MIX, state, controller, state.reverbEnabled)
        }

        FxModuleCard(
            title = "FX CHORUS",
            powered = state.chorusEnabled,
            onPoweredChange = { controller.onToggle(FxToggle.CHORUS, it) },
            headerExtras = { FxPresetSelector(FxModule.CHORUS, state, controller) }
        ) {
            SamplerParamKnob(FxParam.CHORUS_DELAY, state, controller, state.chorusEnabled)
            SamplerParamKnob(FxParam.CHORUS_DEPTH, state, controller, state.chorusEnabled)
            SamplerParamKnob(FxParam.CHORUS_RATE, state, controller, state.chorusEnabled)
            SamplerParamKnob(FxParam.CHORUS_FEEDBACK, state, controller, state.chorusEnabled)
            SamplerParamKnob(FxParam.CHORUS_WIDTH, state, controller, state.chorusEnabled)
            SamplerParamKnob(FxParam.CHORUS_MIX, state, controller, state.chorusEnabled)
        }
    }
}
