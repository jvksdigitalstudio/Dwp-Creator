package com.jvk.dwpcreator.audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Etapa de saturación (módulos FX DRIVE A / FX DRIVE B).
 *
 * `amount` 0..1 mezcla la señal limpia con una versión saturada por `tanh`
 * cuya ganancia previa sube de 1x a 40x (~32 dB); con `amount = 0` la etapa es
 * un bypass **exacto**. La rama saturada se compensa para conservar el nivel
 * RMS aproximado de una señal típica (pico 0,5) -- sin esa compensación,
 * subir el drive sería principalmente subir el volumen.
 *
 * - [asymmetric] = `false` (DRIVE A): `tanh` simétrico -- armónicos impares,
 *   carácter "tape".
 * - [asymmetric] = `true` (DRIVE B): se desplaza el punto de trabajo antes
 *   del `tanh` -- aparecen armónicos pares, carácter "tube" -- y un filtro
 *   anti-DC (pasa-altos de ~10 Hz) elimina el desplazamiento resultante.
 *
 * **Anti-aliasing ADAA.** Una no linealidad aplicada muestra a muestra genera
 * armónicos por encima de Nyquist que se pliegan como inarmónicos "cristalinos"
 * (aliasing) -- el rasgo que delata a un drive barato. Aquí se usa
 * *Antiderivative Anti-Aliasing* de primer orden (Parker et al., DAFx 2016):
 * en vez de evaluar `tanh(u[n])`, se promedia `tanh` sobre el tramo
 * `u[n-1]..u[n]` mediante su antiderivada exacta `ln cosh`:
 * `y[n] = (F(u[n]) - F(u[n-1])) / (u[n] - u[n-1])`, con `F = ln cosh`. Es
 * equivalente a una interpolación lineal previa a la saturación y un filtrado
 * posterior, sin sobremuestrear (coste: ~2 funciones trascendentes por canal y
 * muestra). Cuando `|du|` es ínfimo se usa el límite analítico
 * `tanh((u[n] + u[n-1]) / 2)`. Introduce medio retardo de muestra (inaudible).
 * La salida queda acotada en `[-1, 1]` porque `F' = tanh`.
 *
 * **Tone**: pasa-bajos de un polo sobre la rama saturada (500 Hz..20 kHz);
 * en 20 kHz se desactiva y el camino es el de siempre. Oscurece la
 * distorsión sin tocar la señal limpia.
 *
 * `amount` se suaviza (15 ms) para que moverlo en vivo no produzca clics.
 */
internal class DriveStage(private val sampleRate: Float, private val asymmetric: Boolean) {

    /** Un canal de ADAA de primer orden para `tanh`. Estado en doble precisión: el cociente de diferencias lo exige. */
    private class AdaaTanh {
        private var uPrev = 0.0
        private var fPrev = 0.0

        fun process(u: Double): Double {
            val f = Dsp.logCosh(u)
            val du = u - uPrev
            val y = if (abs(du) < ILL_CONDITIONED) tanh(0.5 * (u + uPrev)) else (f - fPrev) / du
            uPrev = u
            fPrev = f
            return y
        }

        fun clear() {
            uPrev = 0.0
            fPrev = 0.0
        }
    }

    private val smoothing = Dsp.smoothingCoef(15f, sampleRate)
    private val dcR = (1.0 - 2.0 * PI * 10.0 / sampleRate).toFloat()

    private var target = 0f
    private var amount = 0f

    private val adaaL = AdaaTanh()
    private val adaaR = AdaaTanh()
    private val toneL = OnePoleLowPass()
    private val toneR = OnePoleLowPass()
    private var toneCoef = 1f
    private var toneActive = false

    private var dcPrevInL = 0f
    private var dcPrevOutL = 0f
    private var dcPrevInR = 0f
    private var dcPrevOutR = 0f

    fun setAmount(value: Float) {
        target = value.coerceIn(0f, 1f)
    }

    /** Frecuencia de corte del filtro de tono; [TONE_OPEN_HZ] o más lo desactiva. */
    fun setToneHz(hz: Float) {
        toneActive = hz < TONE_OPEN_HZ
        toneCoef = Dsp.lowPassCoef(hz.coerceIn(TONE_MIN_HZ, TONE_OPEN_HZ), sampleRate)
    }

    private fun clearState() {
        adaaL.clear()
        adaaR.clear()
        toneL.clear()
        toneR.clear()
        dcPrevInL = 0f
        dcPrevOutL = 0f
        dcPrevInR = 0f
        dcPrevOutR = 0f
    }

    fun process(left: FloatArray, right: FloatArray, frames: Int) {
        if (target == 0f && amount < 1e-4f) {
            amount = 0f
            clearState()
            return
        }
        for (i in 0 until frames) {
            amount += (target - amount) * smoothing
            if (amount < 1e-5f) continue

            val gain = 1f + 39f * amount
            val comp = 0.5f / tanh(0.5f * gain)
            val dryL = left[i]
            val dryR = right[i]
            var wetL: Float
            var wetR: Float
            if (asymmetric) {
                val bias = 0.2f * amount
                val offset = tanh(gain * bias)
                wetL = (adaaL.process((gain * (dryL + bias)).toDouble()).toFloat() - offset) * comp
                wetR = (adaaR.process((gain * (dryR + bias)).toDouble()).toFloat() - offset) * comp
            } else {
                wetL = adaaL.process((gain * dryL).toDouble()).toFloat() * comp
                wetR = adaaR.process((gain * dryR).toDouble()).toFloat() * comp
            }
            if (toneActive) {
                wetL = toneL.process(wetL, toneCoef)
                wetR = toneR.process(wetR, toneCoef)
            }
            val outL = dryL + (wetL - dryL) * amount
            val outR = dryR + (wetR - dryR) * amount
            if (asymmetric) {
                val yL = Dsp.flush(outL - dcPrevInL + dcR * dcPrevOutL)
                val yR = Dsp.flush(outR - dcPrevInR + dcR * dcPrevOutR)
                dcPrevInL = outL
                dcPrevOutL = yL
                dcPrevInR = outR
                dcPrevOutR = yR
                left[i] = yL
                right[i] = yR
            } else {
                left[i] = outL
                right[i] = outR
            }
        }
    }

    companion object {
        /** Por debajo de este salto de entrada, el cociente de diferencias se sustituye por su límite. */
        private const val ILL_CONDITIONED = 1e-5
        const val TONE_MIN_HZ = 500f
        const val TONE_OPEN_HZ = 19_500f
    }
}
