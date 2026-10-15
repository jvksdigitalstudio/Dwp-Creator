package com.jvk.dwpcreator.sampler.engine

import com.jvk.dwpcreator.codec.wav.SampleLoop
import com.jvk.dwpcreator.sampler.engine.sample.CachedSample
import kotlin.math.abs
import kotlin.math.sin

/**
 * Utilidades de las pruebas del motor: muestras sintéticas, render a un arreglo
 * y medidas de señal. Un único sitio, compartido por todas las pruebas de audio.
 */
object EngineTestSupport {
    const val SAMPLE_RATE = 48_000

    private fun pcmOf(frames: Int, valueAt: (Int) -> Int): ByteArray {
        val pcm = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val v = valueAt(i)
            pcm[i * 2] = (v and 0xFF).toByte()
            pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    /** Muestra mono de 16 bits con un valor constante: la amplitud de salida es predecible. */
    fun constantSample(frames: Int, value: Int = 16_384, rate: Int = SAMPLE_RATE, loop: SampleLoop? = null) =
        CachedSample(pcmOf(frames) { value }, rate, 1, loop)

    /** Seno mono de [freq] Hz y amplitud [amplitude] (sobre 32768). */
    fun sineSample(
        frames: Int,
        rate: Int = SAMPLE_RATE,
        freq: Double,
        amplitude: Int = 12_000,
        loop: SampleLoop? = null
    ) = CachedSample(
        pcmOf(frames) { (sin(2.0 * Math.PI * freq * it / rate) * amplitude).toInt() },
        rate, 1, loop
    )

    /** Muestra mono definida por una función `frame -> valor int16`. */
    fun customSample(frames: Int, rate: Int = SAMPLE_RATE, loop: SampleLoop? = null, valueAt: (Int) -> Int) =
        CachedSample(pcmOf(frames, valueAt), rate, 1, loop)

    /** Rampa ascendente: el valor del frame `i` es `i * slope`, así la posición de lectura se deduce del valor. */
    fun rampSample(frames: Int, slope: Int = 1, rate: Int = SAMPLE_RATE, loop: SampleLoop? = null) =
        CachedSample(pcmOf(frames) { it * slope }, rate, 1, loop)

    /** Renderiza [frames] frames y devuelve el estéreo intercalado (L, R, L, R...). */
    fun render(engine: SamplerEngine, frames: Int): FloatArray {
        val out = FloatArray(frames * 2)
        engine.render(out, frames)
        return out
    }

    /** Canal izquierdo (0) o derecho (1) de un buffer intercalado. */
    fun channel(interleaved: FloatArray, channel: Int): FloatArray =
        FloatArray(interleaved.size / 2) { interleaved[it * 2 + channel] }

    fun rms(signal: FloatArray, from: Int = 0, to: Int = signal.size): Float {
        var sum = 0.0
        for (i in from until to) sum += signal[i].toDouble() * signal[i]
        return Math.sqrt(sum / (to - from)).toFloat()
    }

    /** Frecuencia estimada (Hz) por cruces por cero ascendentes en [from, to). */
    fun frequencyHz(signal: FloatArray, from: Int, to: Int, rate: Int = SAMPLE_RATE): Float {
        var crossings = 0
        for (i in from + 1 until to) if (signal[i - 1] < 0f && signal[i] >= 0f) crossings++
        return crossings * rate.toFloat() / (to - from)
    }

    fun peak(signal: FloatArray, from: Int = 0, to: Int = signal.size): Float {
        var p = 0f
        for (i in from until to) p = maxOf(p, abs(signal[i]))
        return p
    }
}
