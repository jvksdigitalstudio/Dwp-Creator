package com.jvk.dwpcreator.sampler.engine.dsp

import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.preset.FxPresets
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Prueba de integración presets -> cadena maestra real: ningún preset de bus
 * debe producir NaN ni superar el techo del limitador. Vive junto al motor
 * (y no con [FxPresetsTest], que solo conoce el modelo de parámetros) porque
 * ejercita [MasterFxChain].
 */
class PresetChainRenderTest {

    private val modulesWithPresets = FxModule.entries.filter { FxPresets.forModule(it).isNotEmpty() }

    @Test
    fun everyBusPresetRendersFiniteAudioThroughTheRealChain() {
        for (module in modulesWithPresets) {
            for (preset in FxPresets.forModule(module)) {
                val chain = MasterFxChain(48_000f)
                chain.apply(FxPresets.apply(module, preset, SamplerFxState()))
                val frames = 48_000 * 2
                val l = FloatArray(frames) { if (it < 4800) 0.7f * kotlin.math.sin(it * 0.09f) else 0f }
                val r = l.copyOf()
                chain.process(l, r, frames)
                for (x in l) assertTrue("${module.name}/${preset.name}", !x.isNaN() && abs(x) <= 0.97f + 1e-4f)
            }
        }
    }
}
