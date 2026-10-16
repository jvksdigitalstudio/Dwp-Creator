package com.jvk.dwpcreator.sampler.engine.voice

import kotlin.math.abs
import kotlin.math.max

/**
 * Generador de envolvente ADSR de una voz: máquina de estados con el valor
 * actual 0..1. Los coeficientes se pasan en cada muestra ([next]), de modo que
 * mover un control durante una nota surte efecto al instante.
 *
 * - [trigger] reinicia el ataque **desde el valor actual** (no desde 0), así un
 *   retrigger no produce salto.
 * - [release] arranca la caída exponencial desde el valor actual.
 * - [kill] es el apagado rápido y **lineal** (robo de voz, cambio de
 *   instrumento): garantiza que la voz termine en un número fijo de muestras.
 */
internal class Adsr {

    enum class Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE, KILL }

    var stage: Stage = Stage.IDLE
        private set

    var value: Float = 0f
        private set

    private var killStep = 0f

    val isIdle: Boolean get() = stage == Stage.IDLE

    /** `true` si ya se pidió el final de la nota (release o apagado rápido). */
    val isFinishing: Boolean get() = stage == Stage.RELEASE || stage == Stage.KILL

    fun trigger() {
        stage = Stage.ATTACK
    }

    fun reset() {
        stage = Stage.IDLE
        value = 0f
    }

    fun release() {
        if (stage != Stage.IDLE && stage != Stage.KILL) stage = Stage.RELEASE
    }

    /** Apaga linealmente en [frames] muestras (mínimo 1). */
    fun kill(frames: Float) {
        if (stage == Stage.IDLE) return
        val over = max(1f, frames)
        killStep = max(value, MIN_KILL_LEVEL) / over
        stage = Stage.KILL
    }

    /** Avanza una muestra y devuelve el valor de la envolvente. */
    fun next(c: AdsrCoefficients): Float {
        when (stage) {
            Stage.IDLE -> Unit
            Stage.ATTACK -> {
                value += c.attackStep
                if (value >= 1f) {
                    value = 1f
                    stage = Stage.DECAY
                }
            }
            Stage.DECAY -> {
                value = c.sustain + (value - c.sustain) * c.decayCoef
                if (abs(value - c.sustain) < SETTLE_THRESHOLD) {
                    value = c.sustain
                    stage = Stage.SUSTAIN
                }
            }
            Stage.SUSTAIN -> value = c.sustain
            Stage.RELEASE -> {
                value *= c.releaseCoef
                if (value < SILENCE) {
                    value = 0f
                    stage = Stage.IDLE
                }
            }
            Stage.KILL -> {
                value -= killStep
                if (value <= 0f) {
                    value = 0f
                    stage = Stage.IDLE
                }
            }
        }
        return value
    }

    companion object {
        /** Por debajo de este nivel (-80 dB) la envolvente se considera extinguida. */
        const val SILENCE = 1e-4f
        private const val SETTLE_THRESHOLD = 1e-4f
        private const val MIN_KILL_LEVEL = 1e-3f
    }
}
