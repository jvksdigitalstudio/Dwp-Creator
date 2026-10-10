package com.jvk.dwpcreator.audio.dsp

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FX REVERB: red de retardos con realimentación (**FDN**, *Feedback Delay
 * Network*) de 8 líneas, la arquitectura de reverb algorítmica de calidad
 * profesional más usada (Jot/Chaigne), con los cuatro controles de la
 * pestaña de DirectWave:
 *
 * - **Room**: escala la longitud de las 8 líneas (0,5x..2x de la base) --
 *   tamaño aparente de la sala. Los cambios de longitud se deslizan como
 *   máximo un 1 % por bloque (sin clics; sólo un leve "glissando" de la cola
 *   mientras se mueve el knob).
 * - **Damp**: frecuencia de corte de un pasa-bajos de un polo dentro de cada
 *   línea -- las altas frecuencias mueren antes que las graves, como en una
 *   sala real.
 * - **Diffusion**: coeficiente de dos cadenas de 4 difusores all-pass
 *   (una por canal de entrada) que espesan los ecos iniciales.
 * - **Decay**: tiempo RT60. La ganancia de cada línea es
 *   `g_i = 10^(-3 * L_i / (RT60 * fs))`, de modo que **todas** las líneas
 *   pierden 60 dB en exactamente RT60 segundos sea cual sea su longitud.
 *
 * Añadidos de calidad profesional:
 *
 * - **Pre-delay** (0..250 ms): retardo previo a la cola, por canal. Separa el
 *   sonido directo de la reverb -- la nota conserva su ataque y la sala
 *   "aparece" después --, el control más usado para dar claridad.
 * - **Modulation**: cada línea de la FDN modula su longitud con un LFO lento
 *   propio (0,11..0,61 Hz, fases repartidas, hasta +-12 muestras) leído con
 *   interpolación cúbica. Rompe los modos de resonancia fijos que dan el
 *   timbre metálico/"anillado" de las colas largas y vuelve la cola densa y
 *   suave. Las fases distintas evitan que se modulen al unísono.
 * - **Width** (0..150 %): escala la componente lado (L-R) de la salida
 *   húmeda (mid/side). 100 % = estéreo natural de la FDN; 0 % = cola mono.
 *
 * **Estabilidad.** La mezcla entre líneas usa una matriz de Hadamard
 * normalizada (8x8, ortonormal: conserva la energía) y cada línea aplica una
 * ganancia `g_i < 1` y un pasa-bajos con ganancia <= 1: el bucle completo
 * tiene ganancia < 1 para toda frecuencia, así que no puede divergir.
 *
 * Es un *send*: la señal limpia no se toca; se le suma la cola × `mix`. Apagado
 * -> `mix` baja a 0 en ~30 ms y, una vez inaudible, se vacía el estado y se deja
 * de procesar (coste cero).
 */
internal class FdnReverbEffect(private val sampleRate: Float) {

    private val rateScale = sampleRate / REFERENCE_RATE
    private val maxLengths = IntArray(LINES) { (BASE_LENGTHS[it] * MAX_ROOM_SCALE * rateScale).toInt() + 8 + MOD_HEADROOM }
    private val lines = Array(LINES) { DelayLine(maxLengths[it]) }

    private val lengthTarget = FloatArray(LINES)
    private val lengthCurrent = FloatArray(LINES)
    private val lineGain = FloatArray(LINES)
    private val mixed = FloatArray(LINES)
    private val tapped = FloatArray(LINES)
    private val dampState = FloatArray(LINES)

    private val diffusersL = Array(DIFFUSERS) { AllpassDiffuser(((DIFFUSER_LENGTHS[it]) * rateScale).toInt()) }
    private val diffusersR = Array(DIFFUSERS) { AllpassDiffuser(((DIFFUSER_LENGTHS[it] + 11) * rateScale).toInt()) }

    private val smooth = Dsp.smoothingCoef(30f, sampleRate)
    private val preSmooth = Dsp.smoothingCoef(60f, sampleRate)

    private val preLineL = DelayLine((sampleRate * MAX_PREDELAY_SECONDS).toInt() + 8)
    private val preLineR = DelayLine((sampleRate * MAX_PREDELAY_SECONDS).toInt() + 8)
    private val modPhase = FloatArray(LINES) { it / LINES.toFloat() }
    private val modIncrement = FloatArray(LINES) { MOD_RATES_HZ[it] / sampleRate }

    private var decaySec = 1.29f
    private var dampCoef = Dsp.lowPassCoef(11_250f, sampleRate)
    private var diffusionGain = 0.75f * MAX_DIFFUSER_GAIN
    private var mixTarget = 0f
    private var mix = 0f
    private var preTargetSamples = 0f
    private var preSamples = 0f
    private var modTarget = 0.2f
    private var modAmount = 0.2f
    private var widthTarget = 1f
    private var width = 1f
    private var dirty = false
    private var configured = false

    init {
        applyRoom(0.25f)
        for (i in 0 until LINES) lengthCurrent[i] = lengthTarget[i]
        recomputeGains()
    }

    fun setParams(
        enabled: Boolean,
        room: Float,
        dampHz: Float,
        diffusion: Float,
        decaySec: Float,
        mix: Float,
        preDelayMs: Float = 0f,
        modulation: Float = 0f,
        width: Float = 1f
    ) {
        applyRoom(room.coerceIn(0f, 1f))
        preTargetSamples = preDelayMs.coerceIn(0f, MAX_PREDELAY_SECONDS * 1000f) * sampleRate * 0.001f
        modTarget = modulation.coerceIn(0f, 1f)
        widthTarget = width.coerceIn(0f, 1.5f)
        dampCoef = Dsp.lowPassCoef(dampHz.coerceIn(1000f, 20000f), sampleRate)
        diffusionGain = diffusion.coerceIn(0f, 1f) * MAX_DIFFUSER_GAIN
        this.decaySec = decaySec.coerceIn(0.1f, 20f)
        mixTarget = if (enabled) mix.coerceIn(0f, 1f) else 0f
        if (!configured) {
            // Primera configuración: longitudes directamente en su destino (sin glissando inicial).
            for (i in 0 until LINES) lengthCurrent[i] = lengthTarget[i]
            recomputeGains()
            preSamples = preTargetSamples
            modAmount = modTarget
            this.width = widthTarget
            configured = true
        }
    }

    private fun applyRoom(room: Float) {
        val scale = 0.5f + (MAX_ROOM_SCALE - 0.5f) * room
        for (i in 0 until LINES) {
            lengthTarget[i] = (BASE_LENGTHS[i] * scale * rateScale).coerceIn(4f, (maxLengths[i] - 4 - MOD_HEADROOM).toFloat())
        }
    }

    private fun recomputeGains() {
        for (i in 0 until LINES) {
            lineGain[i] = 10f.pow(-3f * lengthCurrent[i] / (decaySec * sampleRate))
        }
    }

    fun process(left: FloatArray, right: FloatArray, frames: Int) {
        if (mixTarget == 0f && mix < 1e-4f) {
            mix = 0f
            if (dirty) {
                clear()
                dirty = false
            }
            return
        }
        dirty = true

        // Deslizamiento de longitudes: como máximo ~1 % del bloque por bloque.
        val maxStep = 0.01f * frames
        for (i in 0 until LINES) {
            val diff = lengthTarget[i] - lengthCurrent[i]
            lengthCurrent[i] += diff.coerceIn(-maxStep, maxStep)
        }
        recomputeGains()

        for (n in 0 until frames) {
            mix += (mixTarget - mix) * smooth
            width += (widthTarget - width) * smooth
            modAmount += (modTarget - modAmount) * smooth
            preSamples += (preTargetSamples - preSamples) * preSmooth

            // Pre-delay: se escribe primero y se lee con retardo `pre + 1`, de modo que
            // pre = 0 es paso directo exacto (read(1) = la muestra recién escrita).
            preLineL.write(left[n])
            preLineR.write(right[n])
            var inL = preLineL.read(preSamples + 1f) * INPUT_GAIN
            var inR = preLineR.read(preSamples + 1f) * INPUT_GAIN
            for (d in 0 until DIFFUSERS) {
                inL = diffusersL[d].process(inL, diffusionGain)
                inR = diffusersR[d].process(inR, diffusionGain)
            }

            val modDepth = modAmount * MOD_DEPTH_SAMPLES * rateScale
            for (i in 0 until LINES) {
                var p = modPhase[i] + modIncrement[i]
                if (p >= 1f) p -= 1f
                modPhase[i] = p
                val raw = lines[i].readCubic(lengthCurrent[i] + modDepth * Dsp.sin2Pi(p))
                dampState[i] = Dsp.flush(dampState[i] + dampCoef * (raw - dampState[i]))
                tapped[i] = dampState[i] * lineGain[i]
                mixed[i] = tapped[i]
            }

            var outL = 0f
            var outR = 0f
            for (i in 0 until LINES) {
                outL += tapped[i] * SIGN_L[i]
                outR += tapped[i] * SIGN_R[i]
            }

            hadamard8(mixed)
            for (i in 0 until LINES) {
                val injected = if ((i and 1) == 0) inL else inR
                lines[i].write(Dsp.flush(mixed[i] + injected))
            }

            val mid = 0.5f * (outL + outR)
            val side = 0.5f * (outL - outR) * width
            left[n] += (mid + side) * OUTPUT_GAIN * mix
            right[n] += (mid - side) * OUTPUT_GAIN * mix
        }
    }

    /** Transformada rápida de Walsh-Hadamard de 8 puntos, normalizada (ortonormal). */
    private fun hadamard8(v: FloatArray) {
        var h = 1
        while (h < LINES) {
            var i = 0
            while (i < LINES) {
                for (j in i until i + h) {
                    val a = v[j]
                    val b = v[j + h]
                    v[j] = a + b
                    v[j + h] = a - b
                }
                i += h * 2
            }
            h *= 2
        }
        for (i in 0 until LINES) v[i] *= NORMALIZATION
    }

    private fun clear() {
        for (line in lines) line.clear()
        preLineL.clear()
        preLineR.clear()
        for (d in diffusersL) d.clear()
        for (d in diffusersR) d.clear()
        dampState.fill(0f)
        mixed.fill(0f)
        tapped.fill(0f)
    }

    companion object {
        private const val LINES = 8
        private const val DIFFUSERS = 4
        private const val REFERENCE_RATE = 48_000f
        private const val MAX_ROOM_SCALE = 2f
        private const val MAX_DIFFUSER_GAIN = 0.7f
        const val MAX_PREDELAY_SECONDS = 0.25f

        /** Excursión máxima de la modulación (muestras a 48 kHz) y holgura de búfer que debe reservarse para ella. */
        private const val MOD_DEPTH_SAMPLES = 12f
        private const val MOD_HEADROOM = 32

        /** Frecuencias de LFO distintas por línea (Hz): ninguna modulación coincide con otra. */
        private val MOD_RATES_HZ = floatArrayOf(0.11f, 0.17f, 0.23f, 0.29f, 0.37f, 0.43f, 0.53f, 0.61f)
        private const val INPUT_GAIN = 0.5f
        private const val OUTPUT_GAIN = 0.5f
        private val NORMALIZATION = (1.0 / sqrt(8.0)).toFloat()

        /** Longitudes base (a 48 kHz), primos entre sí para no reforzar modos de resonancia comunes. */
        private val BASE_LENGTHS = intArrayOf(1087, 1283, 1489, 1699, 1877, 2063, 2273, 2467)
        private val DIFFUSER_LENGTHS = intArrayOf(142, 107, 379, 277)

        /** Filas ortogonales de Walsh: decorrelacionan las salidas L y R. */
        private val SIGN_L = floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f)
        private val SIGN_R = floatArrayOf(1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f)
    }
}
