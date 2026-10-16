package com.jvk.dwpcreator.sampler.engine

import com.jvk.dwpcreator.codec.wav.SampleLoop
import com.jvk.dwpcreator.sampler.api.params.FxModule
import com.jvk.dwpcreator.sampler.api.preset.FxPresets
import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.FilterMode
import com.jvk.dwpcreator.sampler.api.state.FilterState
import com.jvk.dwpcreator.sampler.api.state.LfoDestination
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import com.jvk.dwpcreator.sampler.api.state.LfoState
import com.jvk.dwpcreator.sampler.api.state.LoopMode
import com.jvk.dwpcreator.sampler.api.state.ModulationEnvelopeState
import com.jvk.dwpcreator.sampler.api.state.SampleState
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.sampler.api.state.VoiceState
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.channel
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.constantSample
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.frequencyHz
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.peak
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.render
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.rms
import com.jvk.dwpcreator.sampler.engine.EngineTestSupport.sineSample
import com.jvk.dwpcreator.sampler.engine.sample.CachedSample
import com.jvk.dwpcreator.sampler.engine.voice.VoiceToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * La síntesis por voz, de extremo a extremo a través del motor: afinación, filtro,
 * envolventes, bucle, legato/glide, pitch bend y LFO, medidas sobre el audio real que
 * sale del motor (no sobre sus piezas por separado).
 */
class SamplerEngineVoiceTest {
    private val sr = EngineTestSupport.SAMPLE_RATE

    private fun engineWith(
        bus: (SamplerFxState) -> SamplerFxState = { it },
        voice: (VoiceState) -> VoiceState = { it }
    ) = SamplerEngine(sr).also { it.fxState = bus(SamplerFxState().withVoice(voice)) }

    private fun sampleState(change: (SampleState) -> SampleState): (VoiceState) -> VoiceState =
        { it.copy(sample = change(it.sample)) }

    private fun play(
        engine: SamplerEngine,
        sample: CachedSample,
        velocity: Int = 127,
        key: Int = -1,
        rootKey: Int = -1
    ): VoiceToken {
        val token = VoiceToken()
        engine.noteOn(token, sample, velocity, 1f, 0f, key, rootKey)
        return token
    }

    /** Canal izquierdo de [seconds] de audio. */
    private fun left(engine: SamplerEngine, seconds: Float): FloatArray =
        channel(render(engine, (seconds * sr).toInt()), 0)

    private fun frames(seconds: Float) = (seconds * sr).toInt()

    private fun assertFrequency(expected: Float, signal: FloatArray, fromSeconds: Float, toSeconds: Float, tolerance: Float = 0.03f) {
        val measured = frequencyHz(signal, frames(fromSeconds), frames(toSeconds))
        assertEquals("esperado $expected Hz, medido $measured Hz", expected, measured, expected * tolerance)
    }

    // ---- transparencia del estado de fábrica ---------------------------------------------

    @Test
    fun theFactoryVoiceReproducesTheSampleExactly() {
        val engine = engineWith()
        play(engine, sineSample(sr * 2, freq = 1000.0), key = 60, rootKey = 60)
        val out = left(engine, 0.15f)
        for (n in 2_000 until 5_000) {
            val expected = (sin(2.0 * PI * 1000.0 * n / sr) * 12_000).toInt() / 32768f * 0.70710678f
            assertEquals("frame $n", expected, out[n], 3e-4f)
        }
    }

    // ---- afinación ------------------------------------------------------------------------

    @Test
    fun pitchParameterTransposesBySemitones() {
        val sample = sineSample(sr * 2, freq = 1000.0)
        for ((semitones, expected) in listOf(12f to 2000f, -12f to 500f, 7f to 1498.3f)) {
            val engine = engineWith(voice = sampleState { it.copy(pitchSemitones = semitones) })
            play(engine, sample)
            assertFrequency(expected, left(engine, 1f), 0.2f, 0.8f)
        }
    }

    @Test
    fun fineTuneShiftsByCents() {
        val engine = engineWith(voice = sampleState { it.copy(fineCents = 100f) })
        play(engine, sineSample(sr * 2, freq = 1000.0))
        assertFrequency(1059.5f, left(engine, 1f), 0.2f, 0.8f, tolerance = 0.015f)
    }

    @Test
    fun theTouchedKeyTransposesTheSampleAccordingToKeyTracking() {
        val sample = sineSample(sr * 2, freq = 1000.0)
        for ((track, expected) in listOf(1f to 2000f, 0f to 1000f, 0.5f to 1414.2f)) {
            val engine = engineWith(voice = sampleState { it.copy(pitchTrack = track) })
            play(engine, sample, key = 72, rootKey = 60)
            assertFrequency(expected, left(engine, 1f), 0.2f, 0.8f)
        }
    }

    @Test
    fun anUnknownKeyNeverTransposes() {
        val engine = engineWith()
        play(engine, sineSample(sr * 2, freq = 1000.0), key = 80, rootKey = -1)
        assertFrequency(1000f, left(engine, 1f), 0.2f, 0.8f)
    }

    @Test
    fun pitchBendUsesTheConfiguredRange() {
        val engine = engineWith(voice = sampleState { it.copy(bendRangeSemitones = 12f) })
        engine.pitchBend = 1f
        play(engine, sineSample(sr * 2, freq = 1000.0))
        assertFrequency(2000f, left(engine, 1f), 0.2f, 0.8f)
    }

    @Test
    fun pitchBendMovesAlreadySoundingNotesSmoothly() {
        val engine = engineWith()
        play(engine, sineSample(sr * 3, freq = 1000.0))
        assertFrequency(1000f, left(engine, 0.5f), 0.1f, 0.4f)
        engine.pitchBend = -1f // rango de fábrica: 2 semitonos abajo
        val after = left(engine, 0.5f)
        assertFrequency(890.9f, after, 0.2f, 0.45f)
        assertTrue("sin saltos", peak(after) < 0.5f)
    }

    // ---- filtro ---------------------------------------------------------------------------

    private fun filterVoice(filter: FilterState): (VoiceState) -> VoiceState = { it.copy(filter = filter) }

    @Test
    fun aLowpassRemovesHighFrequenciesWhileTheFactoryFilterPassesThem() {
        val sample = sineSample(sr * 2, freq = 6000.0)
        val open = engineWith().also { play(it, sample) }
        val closed = engineWith(voice = filterVoice(FilterState(mode = FilterMode.LOWPASS_24, cutoff = 0.3f))).also { play(it, sample) }
        val reference = rms(left(open, 1f), frames(0.5f), frames(1f))
        val filtered = rms(left(closed, 1f), frames(0.5f), frames(1f))
        assertTrue("el filtro de fábrica debe pasar la señal ($reference)", reference > 0.15f)
        assertTrue("LP24 a 160 Hz debe silenciar 6 kHz ($filtered)", filtered < reference * 0.001f)
    }

    @Test
    fun aHighpassRemovesLowFrequencies() {
        val sample = sineSample(sr * 2, freq = 100.0)
        val open = engineWith().also { play(it, sample) }
        val filtered = engineWith(voice = filterVoice(FilterState(mode = FilterMode.HIGHPASS_12, cutoff = 0.6f))).also { play(it, sample) }
        val reference = rms(left(open, 1f), frames(0.5f), frames(1f))
        val out = rms(left(filtered, 1f), frames(0.5f), frames(1f))
        assertTrue("HP a 1,28 kHz debe atenuar 100 Hz ($out vs $reference)", out < reference * 0.02f)
    }

    @Test
    fun theFilterEnvelopeSweepsTheCutoffOverTime() {
        val envelope = ModulationEnvelopeState(AdsrState(1f, 100f, 0f, 300f), amount = 1f)
        val engine = engineWith(voice = {
            it.copy(filter = FilterState(cutoff = 0.4f), filterEnvelope = envelope)
        })
        play(engine, sineSample(sr * 3, freq = 4000.0))
        val out = left(engine, 1.5f)
        val early = rms(out, frames(0.010f), frames(0.020f))
        val late = rms(out, frames(1f), frames(1.5f))
        assertTrue("el barrido abre el filtro al principio ($early) y lo cierra después ($late)", early > late * 10f)
    }

    @Test
    fun filterKeyTrackingFollowsTheNoteNumber() {
        fun level(key: Int): Float {
            val engine = engineWith(voice = filterVoice(FilterState(cutoff = 0.5f, keyTrack = 1f)))
            play(engine, sineSample(sr * 2, freq = 3000.0), key = key, rootKey = key)
            return rms(left(engine, 1f), frames(0.5f), frames(1f))
        }
        assertTrue(level(84) > level(60) * 3f)
    }

    @Test
    fun filterVelocityTrackingDarkensSoftNotes() {
        fun level(velocity: Int): Float {
            val engine = engineWith(
                voice = {
                    it.copy(
                        filter = FilterState(cutoff = 0.5f, velocityTrack = 1f),
                        sample = it.sample.copy(velocitySensitivity = 0f)
                    )
                }
            )
            play(engine, sineSample(sr * 2, freq = 1500.0), velocity = velocity)
            return rms(left(engine, 1f), frames(0.5f), frames(1f))
        }
        assertTrue(level(127) > level(20) * 5f)
    }

    @Test
    fun changingTheFilterWhileANoteSoundsAppliesLive() {
        val engine = engineWith()
        play(engine, sineSample(sr * 3, freq = 4000.0))
        val before = rms(left(engine, 0.5f), frames(0.25f), frames(0.5f))
        engine.fxState = engine.fxState.withVoice { it.copy(filter = FilterState(cutoff = 0.1f)) }
        val after = rms(left(engine, 0.5f), frames(0.25f), frames(0.5f))
        assertTrue("antes $before, después $after", after < before * 0.05f)
    }

    @Test
    fun enablingTheFilterMidControlBlockDoesNotCreateAGap() {
        // Regresión: al encender el filtro con 12 muestras aún por correr del bloque de control, los
        // coeficientes sin inicializar daban silencio hasta el siguiente punto de control.
        fun segmentRms(enableFilter: Boolean): Float {
            val engine = engineWith()
            play(engine, sineSample(sr * 3, freq = 2000.0))
            render(engine, 16 * 3_000 + 4)
            if (enableFilter) engine.fxState = engine.fxState.withVoice { it.copy(filter = FilterState(cutoff = 0.95f)) }
            return rms(channel(render(engine, 8), 0))
        }
        val reference = segmentRms(false)
        val filtered = segmentRms(true)
        assertTrue("hueco al activar el filtro: $filtered frente a $reference", filtered > reference * 0.3f)
    }

    // ---- envolvente de amplitud -----------------------------------------------------------

    private fun ampEnvelope(adsr: AdsrState): (VoiceState) -> VoiceState = { it.copy(ampEnvelope = adsr) }

    @Test
    fun aLongAttackFadesTheNoteIn() {
        val engine = engineWith(voice = ampEnvelope(AdsrState(200f, 20_000f, 1f, 15f)))
        play(engine, constantSample(sr * 3))
        val out = left(engine, 0.5f)
        assertTrue(out[frames(0.010f)] < out[frames(0.4f)] * 0.1f)
        assertEquals(0.3536f, out[frames(0.45f)], 0.002f)
    }

    @Test
    fun decayFallsToTheSustainLevel() {
        val engine = engineWith(voice = ampEnvelope(AdsrState(3f, 100f, 0.25f, 15f)))
        play(engine, constantSample(sr * 3))
        val out = left(engine, 1.1f)
        assertEquals(0.3536f * 0.25f, out[frames(1f)], 0.3536f * 0.25f * 0.05f)
    }

    @Test
    fun aLongReleaseKeepsTheTailAudibleAndEndsOnItsOwn() {
        val engine = engineWith(voice = ampEnvelope(AdsrState(3f, 20_000f, 1f, 2_000f)))
        val token = play(engine, constantSample(sr * 8))
        render(engine, frames(0.1f))
        token.released = true
        val tail = channel(render(engine, frames(0.5f)), 0)
        assertTrue("la cola sigue audible a 0,5 s: ${peak(tail, frames(0.45f), frames(0.5f))}", peak(tail, frames(0.45f), frames(0.5f)) > 0.03f)
        render(engine, frames(3.5f))
        assertEquals(0, engine.activeVoiceCount)
    }

    @Test
    fun zeroSustainLetsTheNoteDieByItself() {
        val engine = engineWith(voice = ampEnvelope(AdsrState(3f, 50f, 0f, 15f)))
        play(engine, constantSample(sr * 10))
        render(engine, frames(1f))
        assertEquals(0, engine.activeVoiceCount)
    }

    @Test
    fun velocitySensitivityScalesTheLevel() {
        fun peakFor(sensitivity: Float, velocity: Int): Float {
            val engine = engineWith(voice = sampleState { it.copy(velocitySensitivity = sensitivity) })
            play(engine, constantSample(sr), velocity = velocity)
            return peak(left(engine, 0.3f))
        }
        assertEquals(peakFor(0f, 127), peakFor(0f, 1), 1e-3f)
        assertEquals(0.3536f * 64f / 127f, peakFor(1f, 64), 0.003f)
        assertTrue(peakFor(0.5f, 1) > peakFor(1f, 1))
    }

    // ---- bucle, one-shot, reverse, inicio --------------------------------------------------

    @Test
    fun aLoopedSampleKeepsSoundingBeyondItsLength() {
        val looped = constantSample(4_800, loop = SampleLoop(1_000, 4_000))
        val engine = engineWith()
        play(engine, looped)
        val out = left(engine, 1f)
        assertEquals(1, engine.activeVoiceCount)
        assertEquals(0.3536f, peak(out, frames(0.9f), frames(1f)), 0.002f)

        val off = engineWith(voice = sampleState { it.copy(loopMode = LoopMode.OFF) })
        play(off, looped)
        render(off, frames(0.3f))
        assertEquals(0, off.activeVoiceCount)
    }

    @Test
    fun sustainModeLoopsWhileHeldAndPlaysPastTheLoopAfterRelease() {
        // Rampa creciente con el bucle en un tramo bajo: si tras soltar la nota la lectura sigue más allá
        // del bucle, el nivel crece mucho; si siguiera dentro del bucle, quedaría por debajo de 0,05.
        fun peakAfterRelease(mode: LoopMode): Float {
            val engine = engineWith(
                voice = {
                    it.copy(
                        sample = it.sample.copy(loopMode = mode),
                        ampEnvelope = AdsrState(3f, 20_000f, 1f, 5_000f)
                    )
                }
            )
            val ramp = EngineTestSupport.rampSample(30_000, slope = 1, loop = SampleLoop(1_000, 2_000))
            val token = play(engine, ramp)
            render(engine, frames(0.3f))
            token.released = true
            return peak(channel(render(engine, frames(0.4f)), 0))
        }
        assertTrue(peakAfterRelease(LoopMode.SUSTAIN) > 0.1f)
        assertTrue(peakAfterRelease(LoopMode.FORWARD) < 0.05f)
    }

    @Test
    fun oneShotIgnoresTheNoteOffAndPlaysToTheEnd() {
        val engine = engineWith(voice = sampleState { it.copy(loopMode = LoopMode.ONE_SHOT) })
        val token = play(engine, constantSample(24_000))
        render(engine, frames(0.01f))
        token.released = true
        val during = channel(render(engine, frames(0.4f)), 0)
        assertEquals(1, engine.activeVoiceCount)
        assertTrue(peak(during, frames(0.3f), frames(0.4f)) > 0.3f)
        render(engine, frames(0.2f))
        assertEquals(0, engine.activeVoiceCount)

        val normal = engineWith(voice = sampleState { it.copy(loopMode = LoopMode.OFF) })
        val normalToken = play(normal, constantSample(24_000))
        render(normal, frames(0.01f))
        normalToken.released = true
        render(normal, frames(0.1f))
        assertEquals(0, normal.activeVoiceCount)
    }

    @Test
    fun reverseReadsTheSampleBackwards() {
        val ramp = EngineTestSupport.rampSample(10_000, slope = 1)
        val forward = engineWith().also { play(it, ramp) }
        val reversed = engineWith(voice = sampleState { it.copy(reverse = true) }).also { play(it, ramp) }
        val f = left(forward, 0.15f)
        val r = left(reversed, 0.15f)
        assertTrue(f[2_000] < f[4_000])
        assertTrue(r[2_000] > r[4_000])
    }

    @Test
    fun startFractionSkipsTheBeginningOfTheSample() {
        val engine = engineWith(voice = sampleState { it.copy(startFraction = 0.5f) })
        play(engine, EngineTestSupport.rampSample(20_000, slope = 1))
        val out = left(engine, 0.1f)
        assertEquals((10_000 + 300) / 32768f * 0.70710678f, out[300], 1e-3f)
    }

    // ---- legato, glide, mono -------------------------------------------------------------------

    private val monoLegato = { s: SamplerFxState -> s.copy(monophonic = true) }

    @Test
    fun legatoContinuesTheHeldVoiceAndSlidesToTheNewPitch() {
        val sample = sineSample(sr * 4, freq = 1000.0)
        val engine = engineWith(bus = monoLegato, voice = sampleState { it.copy(legato = true) })
        val first = play(engine, sample, key = 60, rootKey = 60)
        render(engine, frames(0.1f))
        val second = play(engine, sample, key = 64, rootKey = 64)
        val out = left(engine, 0.3f)
        assertEquals(1, engine.activeVoiceCount)
        assertTrue(first.finished)
        assertFalse(second.finished)
        assertFrequency(1259.9f, out, 0.05f, 0.25f)
    }

    @Test
    fun withoutLegatoMonoStartsANewVoiceAtTheOriginalPitch() {
        val sample = sineSample(sr * 4, freq = 1000.0)
        val engine = engineWith(bus = monoLegato)
        val first = play(engine, sample, key = 60, rootKey = 60)
        render(engine, frames(0.1f))
        play(engine, sample, key = 64, rootKey = 64)
        val out = left(engine, 0.3f)
        assertEquals(1, engine.activeVoiceCount)
        assertTrue(first.finished)
        assertFrequency(1000f, out, 0.05f, 0.25f)
    }

    @Test
    fun releasingTheOriginalKeyDoesNotStopALegatoVoiceHeldByTheNewOne() {
        val sample = sineSample(sr * 4, freq = 1000.0)
        val engine = engineWith(bus = monoLegato, voice = sampleState { it.copy(legato = true) })
        val first = play(engine, sample, key = 60, rootKey = 60)
        render(engine, frames(0.1f))
        play(engine, sample, key = 64, rootKey = 64)
        render(engine, frames(0.1f))
        first.released = true
        render(engine, frames(0.5f))
        assertEquals(1, engine.activeVoiceCount)
    }

    @Test
    fun glideSlidesFromThePreviousNoteWhenGlideIsAlways() {
        val sample = sineSample(sr * 5, freq = 500.0)
        val engine = engineWith(voice = sampleState { it.copy(glideMs = 500f, glideOnlyLegato = false) })
        val first = play(engine, sample, key = 60, rootKey = 60)
        render(engine, frames(0.05f))
        first.released = true
        render(engine, frames(1f))
        play(engine, sample, key = 72, rootKey = 72)
        val out = left(engine, 1.5f)
        val start = frequencyHz(out, 0, frames(0.05f))
        assertTrue("debe arrancar cerca de la nota anterior (250 Hz): $start", start in 230f..320f)
        assertFrequency(500f, out, 1f, 1.4f)
    }

    @Test
    fun glideOnlyLegatoDoesNotSlideSeparatedNotes() {
        val sample = sineSample(sr * 5, freq = 500.0)
        val engine = engineWith(voice = sampleState { it.copy(glideMs = 500f, glideOnlyLegato = true) })
        val first = play(engine, sample, key = 60, rootKey = 60)
        render(engine, frames(0.05f))
        first.released = true
        render(engine, frames(1f))
        play(engine, sample, key = 72, rootKey = 72)
        val start = frequencyHz(left(engine, 0.5f), 0, frames(0.1f))
        assertTrue("sin glide, la nota suena a su altura desde el principio: $start", start in 450f..550f)
    }

    // ---- modulación ----------------------------------------------------------------------------

    @Test
    fun thePitchEnvelopeBendsTheStartOfTheNote() {
        val envelope = ModulationEnvelopeState(AdsrState(1f, 200f, 0f, 100f), amount = 0.5f)
        val engine = engineWith(voice = { it.copy(pitchEnvelope = envelope) })
        play(engine, sineSample(sr * 3, freq = 1000.0))
        val out = left(engine, 1.6f)
        val early = frequencyHz(out, frames(0.005f), frames(0.025f))
        val late = frequencyHz(out, frames(1f), frames(1.5f))
        assertTrue("al inicio $early Hz, después $late Hz", early > late * 1.4f)
        assertEquals(1000f, late, 30f)
    }

    @Test
    fun anLfoOnTheLevelProducesTremolo() {
        val lfo = LfoState(rateHz = 5f, shape = LfoShape.SINE, amount = 1f, destination = LfoDestination.LEVEL)
        val engine = engineWith(voice = { it.copy(lfo1 = lfo) })
        play(engine, constantSample(sr * 3))
        val out = left(engine, 1f)
        var min = Float.MAX_VALUE
        for (i in frames(0.1f) until frames(1f)) min = minOf(min, abs(out[i]))
        assertTrue("el tremolo llega a casi cero: $min", min < 0.01f)
        assertTrue(peak(out, frames(0.1f), frames(1f)) > 0.3f)
    }

    @Test
    fun anLfoOnThePitchProducesVibrato() {
        val lfo = LfoState(rateHz = 5f, shape = LfoShape.SINE, amount = 0.5f, destination = LfoDestination.PITCH)
        val engine = engineWith(voice = { it.copy(lfo1 = lfo) })
        play(engine, sineSample(sr * 3, freq = 1000.0))
        val out = left(engine, 0.3f)
        val atPeak = frequencyHz(out, frames(0.03f), frames(0.07f))
        val atTrough = frequencyHz(out, frames(0.13f), frames(0.17f))
        assertTrue("pico $atPeak Hz, valle $atTrough Hz", atPeak > atTrough * 1.15f)
    }

    @Test
    fun anLfoOnThePanSwingsTheImageBetweenTheChannels() {
        val lfo = LfoState(rateHz = 2f, shape = LfoShape.SINE, amount = 1f, destination = LfoDestination.PAN)
        val engine = engineWith(voice = { it.copy(lfo1 = lfo) })
        play(engine, constantSample(sr * 3))
        val out = render(engine, frames(0.5f))
        val l = channel(out, 0)
        val r = channel(out, 1)
        assertTrue(rms(r, frames(0.10f), frames(0.15f)) > rms(l, frames(0.10f), frames(0.15f)) * 3f)
        assertTrue(rms(l, frames(0.35f), frames(0.40f)) > rms(r, frames(0.35f), frames(0.40f)) * 3f)
    }

    // ---- determinismo y robustez ------------------------------------------------------------

    private fun richVoice(): (VoiceState) -> VoiceState = {
        it.copy(
            sample = it.sample.copy(glideMs = 80f, glideOnlyLegato = false),
            filter = FilterState(mode = FilterMode.LOWPASS_24, cutoff = 0.6f, resonance = 0.3f, drive = 0.2f),
            filterEnvelope = ModulationEnvelopeState(AdsrState(5f, 200f, 0.3f, 200f), 0.4f),
            pitchEnvelope = ModulationEnvelopeState(AdsrState(1f, 100f, 0f, 100f), 0.2f),
            lfo1 = LfoState(rateHz = 6f, amount = 0.4f, destination = LfoDestination.PITCH),
            lfo2 = LfoState(rateHz = 3f, shape = LfoShape.SMOOTH_RANDOM, amount = 0.5f, destination = LfoDestination.FILTER)
        )
    }

    @Test
    fun renderingIsIndependentOfTheBlockSizeEvenWithEveryModulationActive() {
        fun run(block: Int): FloatArray {
            val engine = engineWith(voice = richVoice())
            play(engine, sineSample(sr * 2, freq = 700.0), key = 60, rootKey = 60)
            val total = 20_000
            val result = FloatArray(total * 2)
            var done = 0
            while (done < total) {
                val n = minOf(block, total - done)
                val chunk = FloatArray(n * 2)
                engine.render(chunk, n)
                chunk.copyInto(result, done * 2)
                done += n
            }
            return result
        }
        val a = run(137)
        val b = run(512)
        for (i in a.indices) assertEquals("muestra $i", a[i], b[i], 1e-4f)
    }

    @Test
    fun everyVoicePresetRendersFiniteBoundedAudio() {
        for (module in listOf(FxModule.FILTER, FxModule.LFO_1, FxModule.LFO_2)) {
            for (preset in FxPresets.forModule(module)) {
                val engine = SamplerEngine(sr).also { it.fxState = FxPresets.apply(module, preset, SamplerFxState()) }
                play(engine, sineSample(sr * 2, freq = 800.0), key = 60, rootKey = 60)
                val out = render(engine, frames(1.2f))
                for (x in out) assertTrue("${module.name}/${preset.name}", x.isFinite() && abs(x) <= 0.97f + 1e-4f)
            }
        }
    }

    @Test
    fun extremeSettingsNeverProduceNaNOrRunaway() {
        val extreme = engineWith(
            voice = {
                it.copy(
                    sample = it.sample.copy(pitchSemitones = 24f, fineCents = 100f, bendRangeSemitones = 24f),
                    filter = FilterState(mode = FilterMode.LOWPASS_24, cutoff = 0.05f, resonance = 1f, drive = 1f, keyTrack = 1f, velocityTrack = 1f),
                    filterEnvelope = ModulationEnvelopeState(AdsrState(1f, 10f, 1f, 5f), 1f),
                    pitchEnvelope = ModulationEnvelopeState(AdsrState(1f, 10f, 1f, 5f), -1f),
                    lfo1 = LfoState(rateHz = 30f, shape = LfoShape.SAMPLE_HOLD, amount = 1f, destination = LfoDestination.PITCH),
                    lfo2 = LfoState(rateHz = 30f, shape = LfoShape.SQUARE, amount = 1f, destination = LfoDestination.RESONANCE)
                )
            }
        )
        extreme.pitchBend = 1f
        for (key in listOf(0, 60, 127)) play(extreme, sineSample(sr * 2, freq = 440.0), velocity = 1 + key, key = key, rootKey = 60)
        val out = render(extreme, frames(1.5f))
        for (x in out) assertTrue(x.isFinite() && abs(x) <= 0.97f + 1e-4f)
    }
}
