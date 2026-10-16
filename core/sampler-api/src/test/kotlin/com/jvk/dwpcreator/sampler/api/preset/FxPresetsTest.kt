package com.jvk.dwpcreator.sampler.api.preset

import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.params.FxToggle
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FxPresetsTest {

    private val modulesWithPresets = FxModule.entries.filter { FxPresets.forModule(it).isNotEmpty() }

    @Test
    fun theBusAndTheFilterAndLfoModulesHavePresets() {
        for (module in listOf(FxModule.DRIVE_A, FxModule.DRIVE_B, FxModule.CHORUS, FxModule.DELAY, FxModule.REVERB, FxModule.FILTER, FxModule.LFO_1, FxModule.LFO_2)) {
            assertTrue("${module.name} sin presets", FxPresets.forModule(module).isNotEmpty())
        }
        for (module in listOf(FxModule.SAMPLE, FxModule.AMP_ENV, FxModule.FILTER_ENV, FxModule.PITCH_ENV)) {
            assertTrue("${module.name} no debería tener presets", FxPresets.forModule(module).isEmpty())
        }
    }

    @Test
    fun presetNamesAreUniquePerModuleAndNeverCustom() {
        for (module in modulesWithPresets) {
            val presets = FxPresets.forModule(module)
            assertEquals("${module.name} nombres repetidos", presets.size, presets.map { it.name }.toSet().size)
            assertFalse(presets.any { it.name == FxPresets.CUSTOM })
        }
    }

    @Test
    fun presetsOnlyReferenceTheirOwnModulesParamsAndAreInRange() {
        for (module in modulesWithPresets) {
            for (preset in FxPresets.forModule(module)) {
                for ((param, value) in preset.values) {
                    assertTrue("${preset.name}: ${param.key} no pertenece a ${module.name}", param in module.params)
                    assertTrue(
                        "${preset.name}: ${param.key}=$value fuera de [${param.scale.min}, ${param.scale.max}]",
                        value in param.scale.min..param.scale.max
                    )
                }
                for (toggle in preset.toggles.keys) {
                    assertTrue("${preset.name}: ${toggle.key}", toggle in module.toggles)
                }
            }
        }
    }

    @Test
    fun applyingAPresetIsDetectedAndEnablesTheModule() {
        for (module in modulesWithPresets) {
            for (preset in FxPresets.forModule(module)) {
                val state = FxPresets.apply(module, preset, SamplerFxState())
                assertTrue("${module.name}/${preset.name} debe quedar encendido", module.power?.read(state) ?: true)
                assertEquals(preset.name, FxPresets.activeName(module, state))
            }
        }
    }

    @Test
    fun touchingAnyKnobTurnsTheActivePresetIntoCustom() {
        for (module in modulesWithPresets) {
            val preset = FxPresets.forModule(module).first()
            val base = FxPresets.apply(module, preset, SamplerFxState())
            for (param in module.params) {
                val mid = (param.scale.min + param.scale.max) / 2f
                val moved = param.write(base, if (param.read(base) < mid) param.scale.max else param.scale.min)
                if (param.read(moved) == param.read(base)) continue
                assertEquals("${module.name}/${param.key}", FxPresets.CUSTOM, FxPresets.activeName(module, moved))
            }
            for (toggle in module.toggles) {
                val flipped = toggle.write(base, !toggle.read(base))
                assertEquals("${module.name}/${toggle.key}", FxPresets.CUSTOM, FxPresets.activeName(module, flipped))
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
        assertEquals(before.voice, after.voice)
    }

    @Test
    fun aVoicePresetOnlyTouchesItsOwnModuleAndNotTheBus() {
        val filterPreset = FxPresets.forModule(FxModule.FILTER).first()
        val after = FxPresets.apply(FxModule.FILTER, filterPreset, SamplerFxState())
        assertEquals(SamplerFxState().copy(voice = after.voice), after)
        assertEquals(SamplerFxState().voice.lfo1, after.voice.lfo1)
        assertEquals(SamplerFxState().voice.sample, after.voice.sample)
    }

    @Test
    fun theTwoLfoModulesShareTheSamePresetNamesButEditTheirOwnLfo() {
        val names1 = FxPresets.forModule(FxModule.LFO_1).map { it.name }
        val names2 = FxPresets.forModule(FxModule.LFO_2).map { it.name }
        assertEquals(names1, names2)
        val after = FxPresets.apply(FxModule.LFO_2, FxPresets.forModule(FxModule.LFO_2).first(), SamplerFxState())
        assertEquals(SamplerFxState().voice.lfo1, after.voice.lfo1)
        assertTrue(after.voice.lfo2 != SamplerFxState().voice.lfo2)
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
        for (param in module.params) assertEquals(param.key, param.read(factory), param.read(reset), 1e-6f)
        for (toggle in module.toggles) assertEquals(toggle.key, toggle.read(factory), toggle.read(reset))
        assertTrue("el reset no debe apagar el módulo", reset.delayEnabled)
    }

    @Test
    fun everyModuleResetsToFactoryWithoutTouchingTheOthers() {
        val factory = SamplerFxState()
        for (module in FxModule.entries) {
            var tweaked = factory
            for (param in module.params) tweaked = param.write(tweaked, param.fromNormalized(0.8f))
            for (toggle in module.toggles) tweaked = toggle.write(tweaked, !toggle.default)
            val reset = FxPresets.reset(module, tweaked)
            for (param in module.params) assertEquals("${module.name}/${param.key}", param.read(factory), param.read(reset), 1e-4f)
            for (toggle in module.toggles) assertEquals("${module.name}/${toggle.key}", toggle.read(factory), toggle.read(reset))
            // Otro módulo cualquiera no se ha movido.
            val other = FxModule.entries.first { it !== module }
            for (param in other.params) assertEquals("${module.name} tocó ${param.key}", param.read(factory), param.read(reset), 1e-6f)
        }
    }

    @Test
    fun noParameterBelongsToTwoModules() {
        val seen = HashMap<String, FxModule>()
        for (module in FxModule.entries) {
            for (param in module.params) {
                val previous = seen.put(param.key, module)
                assertTrue("${param.key} está en ${previous?.name} y ${module.name}", previous == null)
            }
            for (toggle in module.toggles) {
                val previous = seen.put(toggle.key, module)
                assertTrue("${toggle.key} está en ${previous?.name} y ${module.name}", previous == null)
            }
        }
    }

    @Test
    fun factoryStateMatchesNoPresetSoItReadsAsCustom() {
        for (module in FxModule.entries) {
            assertEquals(module.name, FxPresets.CUSTOM, FxPresets.activeName(module, SamplerFxState()))
        }
    }

    @Test
    fun matchingToleratesTinyNumericDriftButNotRealChanges() {
        val module = FxModule.FILTER
        val preset = FxPresets.forModule(module).first()
        val applied = FxPresets.apply(module, preset, SamplerFxState())
        val cutoff = com.jvk.dwpcreator.sampler.api.params.FilterControls.CUTOFF
        val drifted = cutoff.write(applied, cutoff.read(applied) + 1e-4f)
        assertEquals(preset.name, FxPresets.activeName(module, drifted))
        val changed = cutoff.write(applied, cutoff.read(applied) + 0.05f)
        assertTrue(abs(cutoff.read(changed) - cutoff.read(applied)) > 0.04f)
        assertEquals(FxPresets.CUSTOM, FxPresets.activeName(module, changed))
    }
}
