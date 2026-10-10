package com.jvk.dwpcreator.audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.pow

/**
 * Utilidades numéricas comunes de la cadena DSP. Kotlin/JVM puro, sin ninguna
 * dependencia de Android -- igual que `WavDecoder`/`PcmConverter`, y por la
 * misma razón: todo lo que hay en `audio/dsp` se puede probar en JVM.
 */
internal object Dsp {

    /**
     * Umbral bajo el cual un valor de estado de un filtro/bucle de
     * realimentación se fuerza a 0. Evita **números denormales**: en ARM, los
     * flotantes subnormales que genera una cola de reverb/delay al
     * extinguirse hacen que cada operación cueste mucho más, y en un hilo de
     * audio eso es una causa clásica de "xruns" justo cuando la cola se apaga.
     */
    private const val DENORMAL_FLOOR = 1e-15f

    @JvmStatic
    fun flush(x: Float): Float = if (x > -DENORMAL_FLOOR && x < DENORMAL_FLOOR) 0f else x

    @JvmStatic
    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    /**
     * Coeficiente `a` de un suavizado de un polo (`y += a * (x - y)`) con
     * constante de tiempo [tauMs]. Usado para que ningún parámetro salte en
     * escalón (ruido "zipper") cuando el usuario mueve un knob.
     */
    @JvmStatic
    fun smoothingCoef(tauMs: Float, sampleRate: Float): Float =
        1f - exp(-1f / (tauMs.coerceAtLeast(0.01f) * 0.001f * sampleRate))

    /** Coeficiente de un pasa-bajos de un polo con frecuencia de corte [freqHz]. */
    @JvmStatic
    fun lowPassCoef(freqHz: Float, sampleRate: Float): Float {
        val f = freqHz.coerceIn(1f, sampleRate * 0.49f)
        return 1f - exp((-2.0 * PI * f / sampleRate).toFloat())
    }

    @JvmStatic
    fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /**
     * Seno de un ciclo completo, `sin(2*pi*phase)`, para `phase` en `[0, 1]`,
     * por aproximación parabólica refinada (error máx. ~1e-3, sin
     * llamadas trascendentes). Los LFO de chorus, reverb y cinta lo evalúan
     * varias veces por muestra en el hilo de audio, donde `Math.sin` es el
     * coste dominante.
     */
    @JvmStatic
    fun sin2Pi(phase: Float): Float {
        val x = phase * 2f - 1f
        val y = 4f * (x - x * abs(x))
        // La parábola aproxima sin(pi*x) = -sin(2*pi*phase); se corrige la amplitud y se invierte.
        return -(0.225f * (y * abs(y) - y) + y)
    }

    /**
     * `ln(cosh(x))` numéricamente estable (sin desbordar `cosh` para |x| grande).
     * Es la antiderivada de `tanh`, base del anti-aliasing ADAA de [DriveStage].
     */
    @JvmStatic
    fun logCosh(x: Double): Double {
        val a = abs(x)
        return a + ln1p(exp(-2.0 * a)) - LN2
    }

    private const val LN2 = 0.6931471805599453
}
