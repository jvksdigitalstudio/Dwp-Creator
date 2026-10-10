package com.jvk.dwpcreator.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxPresetsTest {

    @Test
    fun everyModuleHasPresetsWithUniqueNames() {
        for (module in FxModule.entries) {
            val presets = FxPresets.forModule(module)
            assertTrue("${module.name} sin presets", presets.isNotEmpty())
            assertEquals("${module.name} nombres repetidos", presets.size, presets.map { it.name }.toSet().size)
            assertFalse(presets.any { it.name == FxPresets.CUSTOM })
        }
    }

    @Test
    fun presetsOnlyReferenceTheirOwnModulesParamsAndAreInRange() {
        for (module in FxModule.entries) {
            for (preset in FxPresets.forModule(module)) {
                for ((param, value) in preset.values) {
                    assertTrue("${preset.name}: ${param.name} no pertenece a ${module.name}", param in module.params)
                    assertTrue("${preset.name}: ${param.name}=$value fuera de [${param.min}, ${param.max}]", value in param.min..param.max)
                }
                for (toggle in preset.toggles.keys) {
                    assertTrue("${preset.name}: ${toggle.name}", toggle in module.toggles)
                }
            }
        }
    }

    @Test
    fun applyingAPresetIsDetectedAndEnablesTheModule() {
        for (module in FxModule.entries) {
            for (preset in FxPresets.forModule(module)) {
                val state = FxPresets.apply(module, preset, SamplerFxState())
                assertTrue("${module.name}/${preset.name} debe quedar encendido", module.power.read(state))
                assertEquals(preset.name, FxPresets.activeName(module, state))
            }
        }
    }

    @Test
    fun touchingAnyKnobTurnsTheActivePresetIntoCustom() {
        for (module in FxModule.entries) {
            val preset = FxPresets.forModule(module).first()
            val base = FxPresets.apply(module, preset, SamplerFxState())
            for (param in module.params) {
                val moved = param.write(base, if (param.read(base) < (param.min + param.max) / 2f) param.max else param.min)
                if (param.read(moved) == param.read(base)) continue
                assertEquals("${module.name}/${param.name}", FxPresets.CUSTOM, FxPresets.activeName(module, moved))
            }
            for (toggle in module.toggles) {
                val flipped = toggle.write(base, !toggle.read(base))
                assertEquals("${module.name}/${toggle.name}", FxPresets.CUSTOM, FxPresets.activeName(module, flipped))
            }
        }
    }

    @Test
    fun powerStateDoesNotAffectDetection() {
        val module = FxModule.REVERB
        val preset = FxPresets.forModule(module).first()
        val on = FxPresets.apply(module, preset, SamplerFxState())
        val off = FxToggle.REVERB.write(on, false)
        assertEquals(preset.name, FxPresets.activeName(module, off))
    }

    @Test
    fun applyOnlyTouchesItsOwnModule() {
        val before = SamplerFxState(masterVolumeDb = -6f, monophonic = true, reverbMix = 0.77f, chorusEnabled = true)
        val after = FxPresets.apply(FxModule.DELAY, FxPresets.forModule(FxModule.DELAY).first(), before)
        assertEquals(-6f, after.masterVolumeDb, 0f)
        assertTrue(after.monophonic)
        assertEquals(0.77f, after.reverbMix, 0f)
        assertTrue(after.chorusEnabled)
        assertTrue(after.delayEnabled)
    }

    @Test
    fun resetRestoresFactoryValuesAndKeepsPowerState() {
        val module = FxModule.DELAY
        val tweaked = SamplerFxState(
            delayEnabled = true, delayTimeMs = 900f, delayFeedback = 0.8f, delaySync = true,
            delayTape = true, delayWow = 0.9f, delayBounce = false, delayDivision = 7, delayBpm = 77f
        )
        val reset = FxPresets.reset(module, tweaked)
        val factory = SamplerFxState()
        for (param in module.params) assertEquals(param.name, param.read(factory), param.read(reset), 1e-6f)
        for (toggle in module.toggles) assertEquals(toggle.name, toggle.read(factory), toggle.read(reset))
        assertTrue("el reset no debe apagar el módulo", reset.delayEnabled)
    }

    @Test
    fun factoryStateMatchesNoPresetSoItReadsAsCustom() {
        // Los ajustes de fábrica no coinciden con ningún preset a propósito: "Custom" hasta elegir uno.
        for (module in FxModule.entries) {
            assertEquals(module.name, FxPresets.CUSTOM, FxPresets.activeName(module, SamplerFxState()))
        }
    }

    @Test
    fun everyPresetRendersFiniteAudioThroughTheRealChain() {
        for (module in FxModule.entries) {
            for (preset in FxPresets.forModule(module)) {
                val chain = MasterFxChain(48_000f)
                chain.apply(FxPresets.apply(module, preset, SamplerFxState()))
                val frames = 48_000 * 2
                val l = FloatArray(frames) { if (it < 4800) 0.7f * kotlin.math.sin(it * 0.09f) else 0f }
                val r = l.copyOf()
                chain.process(l, r, frames)
                for (x in l) assertTrue("${module.name}/${preset.name}", !x.isNaN() && kotlin.math.abs(x) <= 0.97f + 1e-4f)
            }
        }
    }
}
