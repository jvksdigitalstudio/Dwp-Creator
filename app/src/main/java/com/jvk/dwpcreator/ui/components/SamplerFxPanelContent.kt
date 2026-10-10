package com.jvk.dwpcreator.ui.components

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
import com.jvk.dwpcreator.audio.dsp.FxModule
import com.jvk.dwpcreator.audio.dsp.FxParam
import com.jvk.dwpcreator.audio.dsp.FxToggle
import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.ui.state.SamplerFxController

/**
 * Cuerpo del panel SAMPLER: los módulos del bus maestro, organizados como la
 * pestaña PROGRAM de DirectWave (MASTER, FX DRIVE A, FX DRIVE B, FX DELAY, FX
 * REVERB, FX CHORUS). Las tarjetas fluyen en filas y saltan de línea según el
 * ancho disponible (móvil estrecho o tablet ancha); el conjunto se desplaza
 * en vertical cuando no cabe.
 *
 * Todo el contenido es una función pura de [state]: la UI no guarda estado
 * propio de los efectos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SamplerFxPanelContent(
    state: SamplerFxState,
    controller: SamplerFxController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FxModuleCard(title = "MASTER", powered = null, onPoweredChange = {}) {
                FxSegmented(
                    options = listOf("POLY", "MONO"),
                    selectedIndex = if (state.monophonic) 1 else 0,
                    onSelect = { controller.onToggle(FxToggle.MONOPHONIC, it == 1) }
                )
                FxParamKnob(FxParam.MASTER_VOLUME, state, controller, moduleEnabled = true)
            }

            FxModuleCard(
                title = "FX DRIVE A",
                powered = state.driveAEnabled,
                onPoweredChange = { controller.onToggle(FxToggle.DRIVE_A, it) },
                headerExtras = { FxPresetSelector(FxModule.DRIVE_A, state, controller) }
            ) {
                FxParamKnob(FxParam.DRIVE_A_AMOUNT, state, controller, state.driveAEnabled)
                FxParamKnob(FxParam.DRIVE_A_TONE, state, controller, state.driveAEnabled)
            }

            FxModuleCard(
                title = "FX DRIVE B",
                powered = state.driveBEnabled,
                onPoweredChange = { controller.onToggle(FxToggle.DRIVE_B, it) },
                headerExtras = { FxPresetSelector(FxModule.DRIVE_B, state, controller) }
            ) {
                FxParamKnob(FxParam.DRIVE_B_AMOUNT, state, controller, state.driveBEnabled)
                FxParamKnob(FxParam.DRIVE_B_TONE, state, controller, state.driveBEnabled)
            }

            FxModuleCard(
                title = "FX DELAY",
                powered = state.delayEnabled,
                onPoweredChange = { controller.onToggle(FxToggle.DELAY, it) },
                headerExtras = { FxPresetSelector(FxModule.DELAY, state, controller) }
            ) {
                // Con SYNC el tiempo lo fijan BPM + división; sin SYNC, el knob TIME en ms.
                if (state.delaySync) {
                    FxParamKnob(FxParam.DELAY_DIVISION, state, controller, state.delayEnabled)
                    FxParamKnob(FxParam.DELAY_BPM, state, controller, state.delayEnabled)
                } else {
                    FxParamKnob(FxParam.DELAY_TIME, state, controller, state.delayEnabled)
                }
                FxParamKnob(FxParam.DELAY_FEEDBACK, state, controller, state.delayEnabled)
                FxParamKnob(FxParam.DELAY_LOW_CUT, state, controller, state.delayEnabled)
                FxParamKnob(FxParam.DELAY_HIGH_CUT, state, controller, state.delayEnabled)
                // WOW sólo actúa en modo cinta: se atenúa cuando no.
                FxParamKnob(FxParam.DELAY_WOW, state, controller, state.delayEnabled && state.delayTape)
                FxParamKnob(FxParam.DELAY_MIX, state, controller, state.delayEnabled)
                FxSegmented(
                    options = listOf("NORMAL", "BOUNCE"),
                    selectedIndex = if (state.delayBounce) 1 else 0,
                    onSelect = { controller.onToggle(FxToggle.DELAY_BOUNCE, it == 1) }
                )
                FxSegmented(
                    options = listOf("FREE", "SYNC"),
                    selectedIndex = if (state.delaySync) 1 else 0,
                    onSelect = { controller.onToggle(FxToggle.DELAY_SYNC, it == 1) }
                )
                FxSegmented(
                    options = listOf("CLEAN", "TAPE"),
                    selectedIndex = if (state.delayTape) 1 else 0,
                    onSelect = { controller.onToggle(FxToggle.DELAY_TAPE, it == 1) }
                )
            }

            FxModuleCard(
                title = "FX REVERB",
                powered = state.reverbEnabled,
                onPoweredChange = { controller.onToggle(FxToggle.REVERB, it) },
                headerExtras = { FxPresetSelector(FxModule.REVERB, state, controller) }
            ) {
                FxParamKnob(FxParam.REVERB_ROOM, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_DAMP, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_DIFFUSION, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_DECAY, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_PREDELAY, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_MODULATION, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_WIDTH, state, controller, state.reverbEnabled)
                FxParamKnob(FxParam.REVERB_MIX, state, controller, state.reverbEnabled)
            }

            FxModuleCard(
                title = "FX CHORUS",
                powered = state.chorusEnabled,
                onPoweredChange = { controller.onToggle(FxToggle.CHORUS, it) },
                headerExtras = { FxPresetSelector(FxModule.CHORUS, state, controller) }
            ) {
                FxParamKnob(FxParam.CHORUS_DELAY, state, controller, state.chorusEnabled)
                FxParamKnob(FxParam.CHORUS_DEPTH, state, controller, state.chorusEnabled)
                FxParamKnob(FxParam.CHORUS_RATE, state, controller, state.chorusEnabled)
                FxParamKnob(FxParam.CHORUS_FEEDBACK, state, controller, state.chorusEnabled)
                FxParamKnob(FxParam.CHORUS_WIDTH, state, controller, state.chorusEnabled)
                FxParamKnob(FxParam.CHORUS_MIX, state, controller, state.chorusEnabled)
            }
        }
    }
}
