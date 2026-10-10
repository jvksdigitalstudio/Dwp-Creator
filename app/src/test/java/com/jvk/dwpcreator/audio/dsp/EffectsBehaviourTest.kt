package com.jvk.dwpcreator.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class EffectsBehaviourTest {

    private val sr = 48_000f

    private fun impulse(frames: Int) = FloatArray(frames).also { it[0] = 1f }

    private fun rms(a: FloatArray, from: Int, to: Int): Float {
        var s = 0.0
        for (i in from until to) s += a[i] * a[i]
        return sqrt(s / (to - from)).toFloat()
    }

    // ---- DELAY -------------------------------------------------------------

    @Test
    fun delayEchoesAtConfiguredTimeAndDisabledIsSilent() {
        val delay = StereoDelayEffect(sr)
        delay.setParams(true, 100f, 0f, 20f, 20_000f, bounce = false, mix = 1f)
        val frames = (sr * 0.3f).toInt()
        val l = impulse(frames)
        val r = impulse(frames)
        // `mix` se suaviza: calentar con ceros no altera la prueba porque el impulso va en el frame 0
        // y su eco llega a 4800 frames, cuando el mix ya convergió.
        delay.process(l, r, frames)
        val echoAt = (100f * sr / 1000f).toInt()
        var peakIndex = 1
        for (i in 1 until frames) if (abs(l[i]) > abs(l[peakIndex])) peakIndex = i
        assertTrue("eco cerca de $echoAt, está en $peakIndex", abs(peakIndex - echoAt) <= 2)
        assertTrue(abs(l[peakIndex]) > 0.5f)

        val off = StereoDelayEffect(sr)
        off.setParams(false, 100f, 0.5f, 20f, 20_000f, false, 1f)
        val a = impulse(frames)
        val b = impulse(frames)
        off.process(a, b, frames)
        for (i in 1 until frames) assertEquals(0f, a[i], 0f)
    }

    @Test
    fun delayBounceAlternatesChannels() {
        val delay = StereoDelayEffect(sr)
        delay.setParams(true, 50f, 0.8f, 20f, 20_000f, bounce = true, mix = 1f)
        val frames = (sr * 0.25f).toInt()
        val l = impulse(frames)
        val r = FloatArray(frames)
        delay.process(l, r, frames)
        val t = (50f * sr / 1000f).toInt()
        assertTrue("1er eco en L", abs(l[t]) > 0.2f && abs(r[t]) < 1e-4f)
        assertTrue("2º eco en R", abs(r[2 * t]) > 0.05f && abs(l[2 * t]) < 1e-3f)
    }

    @Test
    fun delayFeedbackLoopDecaysAndStaysFinite() {
        val delay = StereoDelayEffect(sr)
        delay.setParams(true, 30f, 0.95f, 20f, 20_000f, bounce = false, mix = 1f)
        val frames = (sr * 6f).toInt()
        val l = impulse(frames)
        val r = impulse(frames)
        delay.process(l, r, frames)
        for (x in l) assertTrue(!x.isNaN() && abs(x) <= 1.5f)
        assertTrue(rms(l, frames - 4800, frames) < rms(l, 4800, 9600))
    }

    // ---- REVERB ------------------------------------------------------------

    @Test
    fun reverbTailMeasuresRoughlyTheRequestedRt60() {
        val rt60 = 1.0f
        val reverb = FdnReverbEffect(sr)
        reverb.setParams(true, 0.25f, 20_000f, 0.75f, rt60, 1f)
        val frames = (sr * 3f).toInt()
        val l = impulse(frames)
        val r = impulse(frames)
        reverb.process(l, r, frames)
        for (x in l) assertTrue(!x.isNaN() && abs(x) < 4f)
        val early = rms(l, (sr * 0.10f).toInt(), (sr * 0.20f).toInt())
        val late = rms(l, (sr * 1.10f).toInt(), (sr * 1.20f).toInt())
        // 1 s más tarde la energía debe haber caído ~60 dB -> amplitud /1000. Se tolera un margen amplio.
        val dropDb = 20f * log10(early / late)
        assertTrue("caída ${dropDb} dB en ~1 s para RT60=1 s", dropDb in 40f..90f)
    }

    @Test
    fun reverbLongerDecayRingsLonger() {
        fun tailLevel(decay: Float): Float {
            val reverb = FdnReverbEffect(sr)
            reverb.setParams(true, 0.5f, 20_000f, 0.75f, decay, 1f)
            val frames = (sr * 2f).toInt()
            val l = impulse(frames)
            val r = impulse(frames)
            reverb.process(l, r, frames)
            return rms(l, (sr * 1.5f).toInt(), frames)
        }
        assertTrue(tailLevel(8f) > tailLevel(0.5f) * 10f)
    }

    @Test
    fun reverbDisabledAddsNothingAndEnabledAddsATail() {
        val frames = 4800
        val off = FdnReverbEffect(sr)
        off.setParams(false, 0.25f, 11_250f, 0.75f, 1.29f, 0.3f)
        val a = impulse(frames)
        val b = impulse(frames)
        off.process(a, b, frames)
        for (i in 1 until frames) assertEquals(0f, a[i], 0f)

        val on = FdnReverbEffect(sr)
        on.setParams(true, 0.25f, 11_250f, 0.75f, 1.29f, 1f)
        val c = impulse(frames)
        val d = impulse(frames)
        on.process(c, d, frames)
        assertTrue(rms(c, 1000, frames) > 1e-4f)
    }

    // ---- CHORUS ------------------------------------------------------------

    @Test
    fun chorusAddsModulatedCopyAndStaysBounded() {
        val chorus = ChorusEffect(sr)
        chorus.setParams(true, 3.09f, 6f, 1.25f, 0.5f, 1f)
        val frames = (sr * 2f).toInt()
        val l = FloatArray(frames) { if (it % 100 == 0) 0.5f else 0f }
        val r = l.copyOf()
        chorus.process(l, r, frames)
        for (x in l) assertTrue(!x.isNaN() && abs(x) <= 2f)
        // L y R están desfasados 90° en el LFO, así que no pueden ser idénticos.
        var differs = false
        for (i in 0 until frames) if (abs(l[i] - r[i]) > 1e-4f) { differs = true; break }
        assertTrue(differs)
    }

    // ---- DELAY: cinta y cubica ----------------------------------------------

    @Test
    fun tapeModeStaysBoundedAndDiffersFromCleanMode() {
        fun run(tape: Boolean): FloatArray {
            val delay = StereoDelayEffect(sr)
            delay.setParams(true, 120f, 0.9f, 20f, 20_000f, bounce = false, mix = 1f, tape = tape, wow = 1f)
            val frames = (sr * 3f).toInt()
            val l = FloatArray(frames) { if (it < 2400) 0.8f * kotlin.math.sin(it * 0.15f) else 0f }
            val r = l.copyOf()
            delay.process(l, r, frames)
            return l
        }
        val clean = run(false)
        val tape = run(true)
        for (x in tape) assertTrue(!x.isNaN() && abs(x) <= 2f)
        var differs = false
        for (i in clean.indices) if (abs(clean[i] - tape[i]) > 1e-3f) { differs = true; break }
        assertTrue("el modo cinta debe sonar distinto", differs)
    }

    @Test
    fun wowIsIgnoredWhenTapeIsOff() {
        fun run(wow: Float): FloatArray {
            val delay = StereoDelayEffect(sr)
            delay.setParams(true, 100f, 0.5f, 20f, 20_000f, bounce = false, mix = 1f, tape = false, wow = wow)
            val frames = (sr * 0.5f).toInt()
            val l = impulse(frames)
            val r = impulse(frames)
            delay.process(l, r, frames)
            return l
        }
        val a = run(0f)
        val b = run(1f)
        for (i in a.indices) assertEquals(a[i], b[i], 0f)
    }

    // ---- REVERB: pre-delay, modulacion, width -------------------------------

    private fun firstNonZero(a: FloatArray): Int {
        for (i in a.indices) if (abs(a[i]) > 1e-9f) return i
        return -1
    }

    @Test
    fun reverbPreDelayShiftsTheTailByExactlyThatManySamples() {
        fun onset(preMs: Float): Int {
            val reverb = FdnReverbEffect(sr)
            reverb.setParams(true, 0.25f, 20_000f, 0.75f, 1.5f, 1f, preDelayMs = preMs)
            val frames = (sr * 0.5f).toInt()
            val l = impulse(frames)
            val r = impulse(frames)
            // El impulso original (frame 0) se suma a la salida: se compara sólo la cola.
            reverb.process(l, r, frames)
            l[0] = 0f
            return firstNonZero(l)
        }
        val without = onset(0f)
        val with20 = onset(20f)
        assertTrue(without > 0)
        assertEquals(960f, (with20 - without).toFloat(), 2f)
    }

    @Test
    fun reverbModulationKeepsTheTailFiniteAndChangesIt() {
        fun run(mod: Float): FloatArray {
            val reverb = FdnReverbEffect(sr)
            reverb.setParams(true, 0.6f, 12_000f, 0.8f, 6f, 1f, modulation = mod)
            val frames = (sr * 3f).toInt()
            val l = impulse(frames)
            val r = impulse(frames)
            reverb.process(l, r, frames)
            return l
        }
        val still = run(0f)
        val moving = run(1f)
        for (x in moving) assertTrue(!x.isNaN() && abs(x) < 4f)
        var differs = false
        for (i in 20_000 until still.size) if (abs(still[i] - moving[i]) > 1e-5f) { differs = true; break }
        assertTrue(differs)
        // La modulación no cambia la energía de la cola de forma apreciable (mismo RT60).
        val e0 = rms(still, 48_000, 96_000)
        val e1 = rms(moving, 48_000, 96_000)
        assertTrue("energía $e0 vs $e1", e1 > e0 * 0.5f && e1 < e0 * 2f)
    }

    @Test
    fun reverbWidthZeroGivesAMonoTailAndFullWidthDoesNot() {
        fun tails(width: Float): Pair<FloatArray, FloatArray> {
            val reverb = FdnReverbEffect(sr)
            reverb.setParams(true, 0.4f, 12_000f, 0.8f, 2f, 1f, width = width)
            val frames = (sr * 1f).toInt()
            val l = impulse(frames)
            val r = impulse(frames)
            reverb.process(l, r, frames)
            return l to r
        }
        val (ml, mr) = tails(0f)
        for (i in ml.indices) assertEquals(ml[i], mr[i], 1e-6f)
        val (wl, wr) = tails(1f)
        var differs = false
        for (i in 1000 until wl.size) if (abs(wl[i] - wr[i]) > 1e-4f) { differs = true; break }
        assertTrue(differs)
    }

    // ---- CHORUS: width ------------------------------------------------------

    @Test
    fun chorusWidthZeroKeepsLeftAndRightIdenticalForMonoInput() {
        val chorus = ChorusEffect(sr)
        chorus.setParams(true, 5f, 6f, 1f, 0f, 1f, width = 0f)
        val frames = (sr * 1f).toInt()
        val l = FloatArray(frames) { 0.5f * kotlin.math.sin(it * 0.05f) }
        val r = l.copyOf()
        chorus.process(l, r, frames)
        for (i in 0 until frames) assertEquals(l[i], r[i], 1e-6f)
    }

    @Test
    fun chorusFullWidthMakesTheChannelsDiffer() {
        val chorus = ChorusEffect(sr)
        chorus.setParams(true, 5f, 6f, 1f, 0f, 1f, width = 1f)
        val frames = (sr * 1f).toInt()
        val l = FloatArray(frames) { 0.5f * kotlin.math.sin(it * 0.05f) }
        val r = l.copyOf()
        chorus.process(l, r, frames)
        var differs = false
        for (i in 0 until frames) if (abs(l[i] - r[i]) > 1e-3f) { differs = true; break }
        assertTrue(differs)
    }

    // ---- CADENA ------------------------------------------------------------

    @Test
    fun defaultChainIsTransparentAtZeroDb() {
        val chain = MasterFxChain(sr)
        chain.apply(SamplerFxState())
        val frames = 2048
        val l = FloatArray(frames) { 0.4f * kotlin.math.sin(it * 0.05f) }
        val r = l.copyOf()
        val expected = l.copyOf()
        chain.process(l, r, frames)
        // El suavizado del volumen arranca en 1.0 = destino 1.0: la señal debe pasar idéntica.
        for (i in 0 until frames) assertEquals(expected[i], l[i], 1e-6f)
    }

    @Test
    fun masterVolumeAtMinimumMutesAndAllEffectsOnStayFinite() {
        val chain = MasterFxChain(sr)
        val everything = SamplerFxState(
            driveAEnabled = true, driveAAmount = 1f, driveBEnabled = true, driveBAmount = 1f,
            chorusEnabled = true, delayEnabled = true, reverbEnabled = true,
            delayFeedback = 0.95f, reverbDecaySec = 20f, reverbMix = 1f, delayMix = 1f, chorusMix = 1f,
            delayTape = true, delayWow = 1f, reverbModulation = 1f, reverbPreDelayMs = 250f, reverbWidth = 1.5f, chorusWidth = 1.5f
        )
        chain.apply(everything)
        val frames = (sr * 3f).toInt()
        val l = FloatArray(frames) { if (it < 4800) 0.9f * kotlin.math.sin(it * 0.1f) else 0f }
        val r = l.copyOf()
        chain.process(l, r, frames)
        for (x in l) assertTrue(!x.isNaN() && abs(x) <= 0.97f + 1e-4f)

        val mute = MasterFxChain(sr)
        mute.apply(SamplerFxState(masterVolumeDb = FxParam.MASTER_VOLUME.min))
        val a = FloatArray(sr.toInt()) { 0.5f }
        val b = a.copyOf()
        mute.process(a, b, a.size)
        assertEquals(0f, a[a.size - 1], 1e-3f)
    }
}
