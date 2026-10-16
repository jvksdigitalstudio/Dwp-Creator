package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Propiedades que debe cumplir **todo** parámetro del registro [SamplerParams], sea del bus o de la voz. */
class SamplerParamsTest {
    private val defaults = SamplerFxState()

    @Test
    fun keysAreUnique() {
        val keys = SamplerParams.params.map { it.key } + SamplerParams.toggles.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun theRegistryCoversBothTheBusAndTheVoice() {
        val keys = SamplerParams.params.map { it.key }.toSet()
        for (expected in listOf("MASTER_VOLUME", "DELAY_TIME", "SAMPLE_PITCH", "FILTER_CUTOFF", "AMP_ATTACK", "FLT_ENV_AMOUNT", "PIT_ENV_DECAY", "LFO1_RATE", "LFO2_DEST", "LFO_TEMPO")) {
            assertTrue("falta $expected", expected in keys)
        }
        val toggles = SamplerParams.toggles.map { it.key }.toSet()
        for (expected in listOf("MONOPHONIC", "DELAY_TAPE", "SAMPLE_REVERSE", "SAMPLE_LEGATO", "FILTER_ENABLED")) {
            assertTrue("falta $expected", expected in toggles)
        }
    }

    @Test
    fun theFactoryStateReadsEveryParamAtItsDeclaredDefault() {
        for (param in SamplerParams.params) {
            assertEquals(param.key, param.scale.default, param.read(defaults), 1e-4f)
        }
        for (toggle in SamplerParams.toggles) {
            assertEquals(toggle.key, toggle.default, toggle.read(defaults))
        }
    }

    @Test
    fun writingTheExtremesReadsThemBack() {
        for (param in SamplerParams.params) {
            assertEquals(param.key, param.scale.min, param.read(param.write(defaults, param.scale.min)), 1e-3f)
            assertEquals(param.key, param.scale.max, param.read(param.write(defaults, param.scale.max)), 1e-3f)
        }
    }

    @Test
    fun writingOutOfRangeValuesSaturates() {
        for (param in SamplerParams.params) {
            assertEquals(param.key, param.scale.max, param.read(param.write(defaults, param.scale.max * 100f + 1000f)), 1e-3f)
            assertEquals(param.key, param.scale.min, param.read(param.write(defaults, param.scale.min - 1000f)), 1e-3f)
        }
    }

    @Test
    fun normalizedPositionsRoundTripForContinuousParamsAndSnapForSteppedOnes() {
        for (param in SamplerParams.params) {
            for (n in listOf(0f, 0.3f, 0.7f, 1f)) {
                val value = param.fromNormalized(n)
                if (param.scale.steps > 0) {
                    assertEquals(param.key, value, param.fromNormalized(param.toNormalized(value)), 1e-3f)
                } else {
                    assertEquals(param.key, n, param.toNormalized(value), 1e-3f)
                }
            }
        }
    }

    @Test
    fun everyParamChangesOnlyItsOwnField() {
        // Detecta una lente de lectura/escritura equivocada: escribir un parámetro no puede mover a otro.
        for (param in SamplerParams.params) {
            val target = param.fromNormalized(0.7f).let { if (it == param.read(defaults)) param.fromNormalized(0.3f) else it }
            val changed = param.write(defaults, target)
            for (other in SamplerParams.params) {
                if (other === param) continue
                assertEquals("${param.key} movió a ${other.key}", other.read(defaults), other.read(changed), 0f)
            }
            for (toggle in SamplerParams.toggles) {
                assertEquals("${param.key} movió a ${toggle.key}", toggle.read(defaults), toggle.read(changed))
            }
        }
    }

    @Test
    fun everyToggleFlipsOnlyItself() {
        for (toggle in SamplerParams.toggles) {
            val flipped = toggle.write(defaults, !toggle.default)
            assertEquals(toggle.key, !toggle.default, toggle.read(flipped))
            for (other in SamplerParams.toggles) {
                if (other === toggle) continue
                assertEquals("${toggle.key} movió a ${other.key}", other.read(defaults), other.read(flipped))
            }
            for (param in SamplerParams.params) {
                assertEquals("${toggle.key} movió a ${param.key}", param.read(defaults), param.read(flipped), 0f)
            }
        }
    }

    @Test
    fun choiceParamsExposeAsManyLabelsAsPositions() {
        val choices = SamplerParams.params.filter { it.unit == FxUnit.CHOICE }
        assertTrue(choices.size >= 10)
        for (param in choices) {
            val labels = param.labels
            assertNotNull(param.key, labels)
            assertEquals(param.key, labels!!.size - 1, param.scale.steps)
            for ((index, label) in labels.withIndex()) {
                assertEquals(param.key, label, param.format(index.toFloat()))
            }
        }
    }

    @Test
    fun everyParamFormatsToANonEmptyString() {
        for (param in SamplerParams.params) {
            assertTrue(param.key, param.format(param.read(defaults)).isNotEmpty())
            assertTrue(param.key, param.label.isNotBlank())
        }
    }
}
