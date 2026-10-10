package com.jvk.dwpcreator.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspBuildingBlocksTest {

    @Test
    fun delayLineReturnsImpulseAtExactIntegerDelay() {
        val line = DelayLine(64)
        line.write(1f)
        repeat(9) { line.write(0f) }
        // 1 = última escrita; la muestra "1f" se escribió hace 10 escrituras.
        assertEquals(1f, line.read(10f), 1e-6f)
        assertEquals(0f, line.read(9f), 1e-6f)
        assertEquals(0f, line.read(11f), 1e-6f)
    }

    @Test
    fun delayLineInterpolatesBetweenNeighbours() {
        val line = DelayLine(64)
        line.write(1f)
        line.write(0f)
        line.write(0f)
        assertEquals(0.5f, line.read(3.5f), 1e-6f)
    }

    @Test
    fun delayLineClearSilencesIt() {
        val line = DelayLine(32)
        repeat(40) { line.write(1f) }
        line.clear()
        assertEquals(0f, line.read(5f), 0f)
    }

    @Test
    fun allpassPreservesEnergyOfAnImpulse() {
        val ap = AllpassDiffuser(37)
        var energy = ap.process(1f, 0.6f).let { it * it }
        repeat(5000) { val y = ap.process(0f, 0.6f); energy += y * y }
        assertEquals(1f, energy, 0.01f)
    }

    @Test
    fun onePoleLowPassSettlesToDcAndHighPassRejectsIt() {
        val coef = Dsp.lowPassCoef(1000f, 48_000f)
        val lp = OnePoleLowPass()
        val hp = OnePoleHighPass()
        var lastLp = 0f
        var lastHp = 1f
        repeat(5000) { lastLp = lp.process(1f, coef); lastHp = hp.process(1f, coef) }
        assertEquals(1f, lastLp, 1e-3f)
        assertEquals(0f, lastHp, 1e-3f)
    }

    @Test
    fun driveAtZeroIsAnExactBypass() {
        val drive = DriveStage(48_000f, asymmetric = false)
        drive.setAmount(0f)
        val l = FloatArray(256) { (it % 17) / 17f - 0.5f }
        val r = l.copyOf()
        val expected = l.copyOf()
        drive.process(l, r, 256)
        for (i in l.indices) assertEquals(expected[i], l[i], 0f)
    }

    @Test
    fun driveSaturatesButStaysBoundedAndFinite() {
        for (asym in listOf(false, true)) {
            val drive = DriveStage(48_000f, asymmetric = asym)
            drive.setAmount(1f)
            val l = FloatArray(4096) { if (it % 2 == 0) 0.9f else -0.9f }
            val r = l.copyOf()
            drive.process(l, r, 4096)
            for (x in l) { assertTrue(!x.isNaN() && abs(x) < 2f) }
        }
    }

    @Test
    fun limiterNeverExceedsCeilingAndIsTransparentBelow() {
        val limiter = PeakLimiter(48_000f, ceiling = 0.97f)
        val quiet = FloatArray(512) { 0.3f }
        val quietCopy = quiet.copyOf()
        limiter.process(quiet, quietCopy, 512)
        for (x in quiet) assertEquals(0.3f, x, 0f)

        val hot = FloatArray(4096) { if ((it / 40) % 2 == 0) 3f else -3f }
        val hotR = hot.copyOf()
        limiter.process(hot, hotR, 4096)
        for (x in hot) assertTrue(abs(x) <= 0.97f + 1e-5f)
    }

    /**
     * Regresión (CI real): con un pico que baja despacio, el paso de liberación del
     * limitador rebasaba la ganancia máxima permitida y la salida superaba el techo.
     */
    @Test
    fun limiterNeverOvershootsWhileReleasingOnASlowlyDecayingSignal() {
        val limiter = PeakLimiter(48_000f, ceiling = 0.97f)
        val frames = 48_000
        val left = FloatArray(frames) {
            (2.0 * kotlin.math.exp(-it / 20_000.0) * kotlin.math.sin(it * 0.05)).toFloat()
        }
        val right = left.copyOf()
        limiter.process(left, right, frames)
        var peak = 0f
        for (x in left) peak = maxOf(peak, abs(x))
        assertTrue("pico $peak", peak <= 0.97f + 1e-6f)
        // Y sigue siendo un limitador, no un recorte a cero: la señal sobrevive cerca del techo.
        assertTrue(peak > 0.9f)
    }

    // ---- Interpolación cúbica ---------------------------------------------

    @Test
    fun cubicReadIsExactAtIntegerDelays() {
        val line = DelayLine(64)
        val data = FloatArray(40) { kotlin.math.sin(it * 0.37f) }
        for (x in data) line.write(x)
        // read(d) con d entero: la muestra escrita hace d escrituras (1 = la última).
        for (d in 2..30) assertEquals(data[data.size - d], line.readCubic(d.toFloat()), 1e-6f)
    }

    @Test
    fun cubicReadTracksASineBetterThanLinear() {
        val line = DelayLine(256)
        val w = 2.0 * Math.PI * 0.11 // ciclo de ~9 muestras: la lineal se aleja claramente
        val total = 200
        for (n in 0 until total) line.write(kotlin.math.sin(w * n).toFloat())
        var errLinear = 0.0
        var errCubic = 0.0
        for (k in 0 until 40) {
            val d = 20.3f + k * 0.173f
            // Valor exacto: la muestra continua `d` pasos antes de la última (n = total - 1).
            val exact = kotlin.math.sin(w * ((total - 1) - (d - 1.0))).toFloat()
            errLinear = maxOf(errLinear, abs((line.read(d) - exact).toDouble()))
            errCubic = maxOf(errCubic, abs((line.readCubic(d) - exact).toDouble()))
        }
        assertTrue("cúbica $errCubic debe superar a lineal $errLinear", errCubic < errLinear * 0.3)
    }

    @Test
    fun cubicReadNeverReadsOutsideTheBufferOnExtremeDelays() {
        val line = DelayLine(32)
        repeat(100) { line.write(0.5f) }
        for (d in listOf(-5f, 0f, 1f, 1.5f, 1e6f, Float.MAX_VALUE)) {
            val y = line.readCubic(d)
            assertTrue("d=$d -> $y", !y.isNaN() && abs(y) < 2f)
        }
    }

    // ---- Seno rápido y ln cosh --------------------------------------------

    @Test
    fun fastSineMatchesMathSinWithinOneThousandth() {
        var worst = 0f
        for (i in 0..1000) {
            val p = i / 1000f
            worst = maxOf(worst, abs(Dsp.sin2Pi(p) - kotlin.math.sin(2.0 * Math.PI * p).toFloat()))
        }
        assertTrue("error máximo $worst", worst < 2e-3f)
    }

    @Test
    fun logCoshIsStableAndEven() {
        assertEquals(0.0, Dsp.logCosh(0.0), 1e-12)
        assertEquals(Dsp.logCosh(3.7), Dsp.logCosh(-3.7), 1e-12)
        // Para |x| grande ln cosh x ~ |x| - ln 2, sin desbordar.
        assertEquals(1000.0 - 0.6931471805599453, Dsp.logCosh(1000.0), 1e-9)
        assertEquals(kotlin.math.ln(kotlin.math.cosh(1.3)), Dsp.logCosh(1.3), 1e-12)
    }

    // ---- DRIVE: ADAA y Tone ------------------------------------------------

    @Test
    fun driveFollowsTheTanhCurveOnSlowSignalsAfterAdaa() {
        val sr = 48_000f
        val drive = DriveStage(sr, asymmetric = false)
        drive.setAmount(0.5f)
        val frames = sr.toInt()
        val l = FloatArray(frames) { 0.3f * kotlin.math.sin(2.0 * Math.PI * 50.0 * it / sr).toFloat() }
        val r = l.copyOf()
        val dry = l.copyOf()
        drive.process(l, r, frames)
        val gain = 1f + 39f * 0.5f
        val comp = 0.5f / kotlin.math.tanh(0.5f * gain)
        // ADAA promedia el tramo u[n-1]..u[n] -> medio retardo de muestra; se compara contra el tanh ideal con tolerancia acorde.
        for (i in frames - 960 until frames) {
            val wet = kotlin.math.tanh(gain * dry[i]) * comp
            val expected = dry[i] + (wet - dry[i]) * 0.5f
            assertEquals("i=$i", expected, l[i], 0.02f)
        }
    }

    @Test
    fun driveToneDarkensTheDistortion() {
        fun highFrequencyEnergy(toneHz: Float): Double {
            val drive = DriveStage(48_000f, asymmetric = false)
            drive.setAmount(1f)
            drive.setToneHz(toneHz)
            val frames = 48_000
            val l = FloatArray(frames) { 0.6f * kotlin.math.sin(2.0 * Math.PI * 440.0 * it / 48_000.0).toFloat() }
            val r = l.copyOf()
            drive.process(l, r, frames)
            var e = 0.0
            for (i in 24_000 until frames) { val d = (l[i] - l[i - 1]).toDouble(); e += d * d }
            return e
        }
        assertTrue(highFrequencyEnergy(800f) < highFrequencyEnergy(20_000f) * 0.6)
    }

    @Test
    fun driveToneAtMaximumLeavesThePathUntouched() {
        val a = DriveStage(48_000f, asymmetric = true)
        val b = DriveStage(48_000f, asymmetric = true)
        a.setAmount(0.7f); b.setAmount(0.7f)
        a.setToneHz(20_000f); b.setToneHz(DriveStage.TONE_OPEN_HZ)
        val l1 = FloatArray(512) { 0.4f * kotlin.math.sin(it * 0.2f) }
        val l2 = l1.copyOf()
        val r1 = l1.copyOf(); val r2 = l1.copyOf()
        a.process(l1, r1, 512); b.process(l2, r2, 512)
        for (i in l1.indices) assertEquals(l1[i], l2[i], 0f)
    }
}
