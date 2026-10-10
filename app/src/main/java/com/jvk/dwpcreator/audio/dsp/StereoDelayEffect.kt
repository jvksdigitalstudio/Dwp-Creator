package com.jvk.dwpcreator.audio.dsp

import kotlin.math.tanh

/**
 * FX DELAY estéreo con **realimentación filtrada** (pasa-altos "Low cut" y
 * pasa-bajos "High cut" dentro del bucle: cada repetición suena más fina y
 * más oscura que la anterior, como en DirectWave) y dos modos de reparto:
 *
 * - **Normal**: cada canal repite el suyo.
 * - **Bounce** (ping-pong): la entrada se suma a mono y entra por la
 *   izquierda; la salida de cada línea alimenta a la *otra*, así que las
 *   repeticiones rebotan izquierda-derecha.
 *
 * **Modo cinta (`tape`).** Emula un eco de cinta con dos fenómenos reales:
 * 1. *Saturación en el bucle de realimentación*: cada repetición pasa por una
 *    curva `tanh(1,5·x)/1,5` (ganancia 1 para señal pequeña, compresión suave
 *    en los picos): las repeticiones fuertes se aplanan y las colas se
 *    redondean, y el bucle gana estabilidad adicional.
 * 2. *Wow y flutter*: variación lenta (~0,55 Hz, "wow") y rápida (~6,3 Hz,
 *    "flutter") de la velocidad de la cinta, modelada como modulación del
 *    tiempo de retardo (±1,2 ms y ±0,12 ms a `wow` = 100 %, con fase distinta
 *    por canal). Produce la ligera desafinación flotante característica. La
 *    modulación se lee con interpolación cúbica de Hermite.
 *
 * El tiempo se desliza hacia su destino (~40 ms) en vez de saltar -- al
 * mover el knob se obtiene el "cambio de tono" de un delay analógico en
 * lugar de un chasquido --, y se lee con **interpolación cúbica**
 * ([DelayLine.readCubic]), que no oscurece el eco durante ese deslizamiento
 * como haría la lineal. El tiempo ya llega resuelto en ms: la sincronía con
 * el tempo la resuelve [DelaySync] antes de llamar a [setParams].
 *
 * Es un *send* (la señal limpia no se toca; se le suma la repetición ×
 * `mix`). La realimentación se limita a 0,95 y los filtros del bucle nunca
 * ganan > 1, así que el bucle es estable por construcción.
 */
internal class StereoDelayEffect(private val sampleRate: Float) {

    private val capacity = (sampleRate * MAX_DELAY_SECONDS).toInt()
    private val lineL = DelayLine(capacity)
    private val lineR = DelayLine(capacity)
    private val timeSmooth = Dsp.smoothingCoef(40f, sampleRate)
    private val paramSmooth = Dsp.smoothingCoef(30f, sampleRate)

    private val hpL = OnePoleHighPass()
    private val hpR = OnePoleHighPass()
    private val lpL = OnePoleLowPass()
    private val lpR = OnePoleLowPass()

    private var timeTargetSamples = 375f * sampleRate * 0.001f
    private var timeSamples = timeTargetSamples
    private var feedbackTarget = 0.5f
    private var feedback = feedbackTarget
    private var mixTarget = 0f
    private var mix = 0f
    private var wowTarget = 0f
    private var wow = 0f
    private var lowCutCoef = Dsp.lowPassCoef(120f, sampleRate)
    private var highCutCoef = Dsp.lowPassCoef(8000f, sampleRate)
    private var bounce = true
    private var tape = false
    private var wowPhase = 0f
    private var flutterPhase = 0f
    private var dirty = false
    private var configured = false

    fun setParams(
        enabled: Boolean,
        timeMs: Float,
        feedback: Float,
        lowCutHz: Float,
        highCutHz: Float,
        bounce: Boolean,
        mix: Float,
        tape: Boolean = false,
        wow: Float = 0f
    ) {
        timeTargetSamples = (timeMs.coerceIn(10f, MAX_DELAY_SECONDS * 1000f - 10f)) * sampleRate * 0.001f
        feedbackTarget = feedback.coerceIn(0f, 0.95f)
        lowCutCoef = Dsp.lowPassCoef(lowCutHz.coerceIn(20f, 2000f), sampleRate)
        highCutCoef = Dsp.lowPassCoef(highCutHz.coerceIn(500f, 20000f), sampleRate)
        this.bounce = bounce
        this.tape = tape
        wowTarget = if (tape) wow.coerceIn(0f, 1f) else 0f
        mixTarget = if (enabled) mix.coerceIn(0f, 1f) else 0f
        if (!configured) {
            // Primera configuración: sin deslizamiento desde los valores de construcción.
            timeSamples = timeTargetSamples
            this.feedback = feedbackTarget
            this.wow = wowTarget
            configured = true
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
        val msToSamples = sampleRate * 0.001f
        for (i in 0 until frames) {
            timeSamples += (timeTargetSamples - timeSamples) * timeSmooth
            feedback += (feedbackTarget - feedback) * paramSmooth
            mix += (mixTarget - mix) * paramSmooth
            wow += (wowTarget - wow) * paramSmooth

            var readL = timeSamples
            var readR = timeSamples
            if (wow > 1e-4f) {
                var wowR = wowPhase + WOW_STEREO_OFFSET
                if (wowR >= 1f) wowR -= 1f
                var flutterR = flutterPhase + FLUTTER_STEREO_OFFSET
                if (flutterR >= 1f) flutterR -= 1f
                val depth = wow * msToSamples
                readL += depth * (WOW_DEPTH_MS * Dsp.sin2Pi(wowPhase) + FLUTTER_DEPTH_MS * Dsp.sin2Pi(flutterPhase))
                readR += depth * (WOW_DEPTH_MS * Dsp.sin2Pi(wowR) + FLUTTER_DEPTH_MS * Dsp.sin2Pi(flutterR))
                wowPhase += WOW_RATE_HZ / sampleRate
                if (wowPhase >= 1f) wowPhase -= 1f
                flutterPhase += FLUTTER_RATE_HZ / sampleRate
                if (flutterPhase >= 1f) flutterPhase -= 1f
            }

            val outL = lineL.readCubic(readL)
            val outR = lineR.readCubic(readR)

            if (bounce) {
                val mono = 0.5f * (left[i] + right[i])
                val fbL = loop(lpL.process(hpL.process(outR, lowCutCoef), highCutCoef)) * feedback
                val fbR = loop(lpR.process(hpR.process(outL, lowCutCoef), highCutCoef)) * feedback
                lineL.write(mono + fbL)
                lineR.write(fbR)
            } else {
                val fbL = loop(lpL.process(hpL.process(outL, lowCutCoef), highCutCoef)) * feedback
                val fbR = loop(lpR.process(hpR.process(outR, lowCutCoef), highCutCoef)) * feedback
                lineL.write(left[i] + fbL)
                lineR.write(right[i] + fbR)
            }

            left[i] += outL * mix
            right[i] += outR * mix
        }
    }

    /** Elemento no lineal del bucle: identidad en modo limpio, saturación de cinta en modo `tape`. */
    private fun loop(x: Float): Float = if (tape) tanh(TAPE_DRIVE * x) * (1f / TAPE_DRIVE) else x

    private fun clear() {
        lineL.clear()
        lineR.clear()
        hpL.clear()
        hpR.clear()
        lpL.clear()
        lpR.clear()
        wowPhase = 0f
        flutterPhase = 0f
    }

    companion object {
        const val MAX_DELAY_SECONDS = 2.0f

        private const val TAPE_DRIVE = 1.5f
        private const val WOW_RATE_HZ = 0.55f
        private const val FLUTTER_RATE_HZ = 6.3f
        private const val WOW_DEPTH_MS = 1.2f
        private const val FLUTTER_DEPTH_MS = 0.12f
        private const val WOW_STEREO_OFFSET = 0.31f
        private const val FLUTTER_STEREO_OFFSET = 0.17f
    }
}
