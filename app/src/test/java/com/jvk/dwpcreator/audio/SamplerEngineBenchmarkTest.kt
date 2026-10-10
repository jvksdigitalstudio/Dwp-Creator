package com.jvk.dwpcreator.audio

import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache.CachedSample
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * **Benchmark del motor de audio** con el código real de producción
 * ([SamplerEngine] + la cadena de efectos completa), medido en cada corrida de CI.
 *
 * Mide cuánto tarda en generar un bloque de [BLOCK_FRAMES] frames (una ráfaga típica
 * de Android a 48 kHz = 5,33 ms) y lo expresa como *carga* (% del tiempo real
 * disponible) y *factor de tiempo real* (RTF = tiempo disponible / tiempo usado; RTF 1
 * = justo al límite, RTF 10 = el motor usa un 10 % del tiempo).
 *
 * Escenarios:
 *  1. **Reposo** -- sin voces ni efectos (coste base de la cadena).
 *  2. **32 voces mono, sin efectos** -- remuestreo cúbico 44,1->48 kHz de la polifonía máxima.
 *  3. **32 voces estéreo + TODOS los efectos** -- el peor caso real (drive A/B, chorus,
 *     delay con feedback alto, reverb con RT60 de 20 s).
 *  4. **Cola decayendo** -- una nota corta con todos los efectos y 30 s de cola: detecta
 *     regresiones de números denormales (el coste no debe dispararse al apagarse la cola).
 *
 * Esto es una medición en el JVM del runner de CI, **no en el teléfono**: sirve para
 * detectar regresiones y comparar escenarios entre sí (mismo código, misma máquina), no
 * como cifra absoluta de un dispositivo. El monitor del panel SAMPLER (`AudioStats`)
 * da la cifra real en el teléfono. Las aserciones son deliberadamente holgadas (sólo
 * saltan ante un empeoramiento de orden de magnitud). El informe se escribe en
 * `build/reports/sampler-benchmark/report.txt` y el workflow lo publica en el resumen.
 */
class SamplerEngineBenchmarkTest {

    private class Timing(val name: String, val avgUs: Double, val p99Us: Double, val worstUs: Double) {
        val loadPercent: Double get() = avgUs / BLOCK_PERIOD_US * 100.0
        val realtimeFactor: Double get() = BLOCK_PERIOD_US / avgUs
    }

    private val everythingOn = SamplerFxState(
        driveAEnabled = true, driveAAmount = 0.5f,
        driveBEnabled = true, driveBAmount = 0.5f,
        chorusEnabled = true, chorusFeedback = 0.5f, chorusMix = 0.5f,
        delayEnabled = true, delayFeedback = 0.9f, delayMix = 0.5f,
        reverbEnabled = true, reverbDecaySec = 20f, reverbMix = 0.5f
    )

    private fun sineSample(seconds: Int, rate: Int, channels: Int): CachedSample {
        val frames = rate * seconds
        val pcm = ByteArray(frames * channels * 2)
        for (i in 0 until frames) {
            val v = (sin(2.0 * Math.PI * 220.0 * i / rate) * 10_000).toInt()
            for (c in 0 until channels) {
                val o = (i * channels + c) * 2
                pcm[o] = (v and 0xFF).toByte()
                pcm[o + 1] = ((v shr 8) and 0xFF).toByte()
            }
        }
        return CachedSample(pcm, rate, channels)
    }

    private fun renderBlocks(engine: SamplerEngine, blocks: Int, times: LongArray? = null): FloatArray {
        val out = FloatArray(BLOCK_FRAMES * 2)
        for (i in 0 until blocks) {
            val t0 = System.nanoTime()
            engine.render(out, BLOCK_FRAMES)
            if (times != null) times[i] = System.nanoTime() - t0
        }
        return out
    }

    private fun measure(name: String, engine: SamplerEngine, seconds: Int): Timing {
        renderBlocks(engine, WARMUP_SECONDS * SAMPLE_RATE / BLOCK_FRAMES)
        val blocks = seconds * SAMPLE_RATE / BLOCK_FRAMES
        val times = LongArray(blocks)
        val last = renderBlocks(engine, blocks, times)
        for (x in last) assertTrue("$name: salida no finita", !x.isNaN() && abs(x) <= 1f)
        val sorted = times.sortedArray()
        val p99 = sorted[(blocks * 0.99).toInt().coerceAtMost(blocks - 1)]
        return Timing(name, times.average() / 1000.0, p99 / 1000.0, sorted.last() / 1000.0)
    }

    private fun engineWithVoices(count: Int, sample: CachedSample, fx: SamplerFxState): SamplerEngine {
        val engine = SamplerEngine(SAMPLE_RATE)
        engine.fxState = fx
        repeat(count) { engine.noteOn(SamplerEngine.VoiceToken(), sample, 127, 1f, 0f) }
        return engine
    }

    @Test
    fun engineStaysFarBelowTheRealtimeDeadlineInTheWorstRealisticCase() {
        val results = mutableListOf<Timing>()

        results += measure("1. Reposo (sin voces, sin FX)", SamplerEngine(SAMPLE_RATE), MEASURE_SECONDS)

        val mono = sineSample(30, 44_100, 1)
        results += measure(
            "2. 32 voces mono, sin FX",
            engineWithVoices(32, mono, SamplerFxState()), MEASURE_SECONDS
        )

        val stereo = sineSample(30, 44_100, 2)
        val worst = measure(
            "3. 32 voces estereo + TODOS los FX",
            engineWithVoices(32, stereo, everythingOn), MEASURE_SECONDS
        )
        results += worst

        // 4. Cola decayendo: una nota corta y 30 s de cola; ventanas de 5 s.
        val tailEngine = engineWithVoices(1, sineSample(1, 44_100, 2), everythingOn)
        val windowBlocks = 5 * SAMPLE_RATE / BLOCK_FRAMES
        val windows = DoubleArray(6) {
            val t = LongArray(windowBlocks)
            renderBlocks(tailEngine, windowBlocks, t)
            t.average() / 1000.0
        }

        val report = buildReport(results, windows)
        println(report)
        writeReport(report)

        assertTrue(
            "Peor caso: RTF ${"%.1f".format(worst.realtimeFactor)} (carga ${"%.1f".format(worst.loadPercent)}%) -- esperado > $MIN_REALTIME_FACTOR",
            worst.realtimeFactor >= MIN_REALTIME_FACTOR
        )
        val slowest = windows.max()
        val fastest = windows.min()
        assertTrue(
            "Coste de la cola variable x${"%.1f".format(slowest / fastest)} entre ventanas: posible regresion de denormales",
            slowest <= fastest * MAX_TAIL_COST_SPREAD
        )
    }

    private fun buildReport(results: List<Timing>, tailWindows: DoubleArray): String {
        val sb = StringBuilder()
        sb.appendLine("Sampler engine benchmark -- bloque de $BLOCK_FRAMES frames @ $SAMPLE_RATE Hz (= ${"%.2f".format(Locale.US, BLOCK_PERIOD_US / 1000.0)} ms de tiempo real)")
        sb.appendLine("Medido en el JVM del runner de CI (no en un telefono): sirve para comparar escenarios y detectar regresiones.")
        sb.appendLine()
        sb.appendLine(String.format(Locale.US, "%-38s %9s %9s %10s %8s %7s", "Escenario", "avg us", "p99 us", "peor us", "carga %", "RTF"))
        for (r in results) {
            sb.appendLine(
                String.format(
                    Locale.US, "%-38s %9.1f %9.1f %10.1f %8.2f %7.1f",
                    r.name, r.avgUs, r.p99Us, r.worstUs, r.loadPercent, r.realtimeFactor
                )
            )
        }
        sb.appendLine()
        sb.appendLine("4. Cola decayendo (1 nota + todos los FX), coste medio por bloque en ventanas de 5 s:")
        tailWindows.forEachIndexed { i, us ->
            sb.appendLine(String.format(Locale.US, "   %2d-%2d s: %8.1f us  (%.2f %% de carga)", i * 5, i * 5 + 5, us, us / BLOCK_PERIOD_US * 100.0))
        }
        return sb.toString()
    }

    private fun writeReport(report: String) {
        try {
            val dir = File("build/reports/sampler-benchmark")
            dir.mkdirs()
            File(dir, "report.txt").writeText(report)
        } catch (e: Exception) {
            println("No se pudo escribir el informe del benchmark: $e")
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK_FRAMES = 256
        const val BLOCK_PERIOD_US = BLOCK_FRAMES * 1_000_000.0 / SAMPLE_RATE
        const val WARMUP_SECONDS = 3
        const val MEASURE_SECONDS = 10

        /** Holgura enorme a propósito: en el peor caso el motor debería usar una fracción pequeña del tiempo. */
        const val MIN_REALTIME_FACTOR = 3.0

        /** Un coste de cola que varíe más de esto entre ventanas indica denormales / trabajo no acotado. */
        const val MAX_TAIL_COST_SPREAD = 8.0
    }
}
