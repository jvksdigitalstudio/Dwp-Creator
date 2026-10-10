package com.jvk.dwpcreator.audio.dsp

/**
 * FX CHORUS: una línea de retardo modulada por canal. El retardo de cada
 * canal recorre `delay .. delay + depth` (ms) con un LFO senoidal; el LFO
 * del canal derecho va desfasado 90° respecto al izquierdo para abrir la
 * imagen estéreo. Es un *send*: la señal limpia pasa intacta y se le suma la
 * señal modulada multiplicada por `mix` (comportamiento de los efectos "send"
 * de DirectWave).
 *
 * **Calidad.**
 * - La línea se lee con **interpolación cúbica de Hermite**
 *   ([DelayLine.readCubic]): la lineal es un pasa-bajos variable que apaga
 *   el brillo del chorus en las posiciones fraccionarias y lo hace sonar
 *   "mate" y con zumbido.
 * - El LFO usa [Dsp.sin2Pi] (sin llamadas trascendentes por muestra).
 * - **Width** (0..1,5): escala la componente *lado* (L-R) del efecto en una
 *   codificación mid/side. 100 % = el estéreo natural del desfase de LFO;
 *   0 % = efecto mono (compatible con altavoz único: un chorus muy abierto
 *   puede cancelarse al sumar a mono); >100 % = ensanchado.
 *
 * Los parámetros continuos se suavizan por muestra. Con el módulo apagado,
 * `mix` baja a 0 en ~30 ms (sin clic); una vez inaudible, se vacían las
 * líneas y se deja de procesar (coste cero) hasta que se vuelva a encender.
 */
internal class ChorusEffect(private val sampleRate: Float) {

    private val lineL = DelayLine((sampleRate * 0.05f).toInt())
    private val lineR = DelayLine((sampleRate * 0.05f).toInt())
    private val smooth = Dsp.smoothingCoef(30f, sampleRate)

    private var delayTarget = 3.09f
    private var depthTarget = 6f
    private var rateTarget = 1.25f
    private var feedbackTarget = 0f
    private var mixTarget = 0f
    private var widthTarget = 1f

    private var delayMs = delayTarget
    private var depthMs = depthTarget
    private var rateHz = rateTarget
    private var feedback = feedbackTarget
    private var mix = 0f
    private var width = widthTarget

    private var phase = 0.0
    private var dirty = false
    private var configured = false

    fun setParams(
        enabled: Boolean,
        delayMs: Float,
        depthMs: Float,
        rateHz: Float,
        feedback: Float,
        mix: Float,
        width: Float = 1f
    ) {
        delayTarget = delayMs.coerceIn(0.5f, 20f)
        depthTarget = depthMs.coerceIn(0f, 15f)
        rateTarget = rateHz.coerceIn(0.05f, 10f)
        feedbackTarget = feedback.coerceIn(0f, 0.9f)
        widthTarget = width.coerceIn(0f, 1.5f)
        mixTarget = if (enabled) mix.coerceIn(0f, 1f) else 0f
        if (!configured) {
            // Primera configuración: partir directamente de los valores pedidos
            // (el deslizamiento es para movimientos del usuario, no para el arranque).
            this.delayMs = delayTarget
            this.depthMs = depthTarget
            this.rateHz = rateTarget
            this.feedback = feedbackTarget
            this.width = widthTarget
            configured = true
        }
    }

    fun process(left: FloatArray, right: FloatArray, frames: Int) {
        if (mixTarget == 0f && mix < 1e-4f) {
            mix = 0f
            if (dirty) {
                lineL.clear()
                lineR.clear()
                dirty = false
            }
            return
        }
        dirty = true
        val msToSamples = sampleRate * 0.001f
        for (i in 0 until frames) {
            delayMs += (delayTarget - delayMs) * smooth
            depthMs += (depthTarget - depthMs) * smooth
            rateHz += (rateTarget - rateHz) * smooth
            feedback += (feedbackTarget - feedback) * smooth
            mix += (mixTarget - mix) * smooth
            width += (widthTarget - width) * smooth

            val p = phase.toFloat()
            var pR = p + 0.25f
            if (pR >= 1f) pR -= 1f
            val lfoL = 0.5f + 0.5f * Dsp.sin2Pi(p)
            val lfoR = 0.5f + 0.5f * Dsp.sin2Pi(pR)
            val wetL = lineL.readCubic((delayMs + depthMs * lfoL) * msToSamples)
            val wetR = lineR.readCubic((delayMs + depthMs * lfoR) * msToSamples)

            lineL.write(left[i] + Dsp.flush(wetL * feedback))
            lineR.write(right[i] + Dsp.flush(wetR * feedback))

            val mid = 0.5f * (wetL + wetR)
            val side = 0.5f * (wetL - wetR) * width
            left[i] += (mid + side) * mix
            right[i] += (mid - side) * mix

            phase += rateHz / sampleRate
            if (phase >= 1.0) phase -= 1.0
        }
    }
}
