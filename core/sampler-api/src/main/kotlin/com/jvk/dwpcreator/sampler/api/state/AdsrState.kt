package com.jvk.dwpcreator.sampler.api.state

/**
 * Parámetros de una envolvente ADSR. Los tiempos son los de cada fase; el
 * decaimiento y el release son exponenciales (el tiempo es lo que tarda en
 * caer 60 dB), el ataque es lineal.
 */
data class AdsrState(
    val attackMs: Float,
    val decayMs: Float,
    /** Nivel de sostenido, 0..1. */
    val sustain: Float,
    val releaseMs: Float
) {
    companion object {
        /** Amplitud: ataque de 3 ms y release de 15 ms contra clics; sostenido pleno. */
        val AMP = AdsrState(attackMs = 3f, decayMs = 20_000f, sustain = 1f, releaseMs = 15f)

        /** Filtro: barrido tipo "pluck" cuando se sube su cantidad. */
        val FILTER = AdsrState(attackMs = 1f, decayMs = 400f, sustain = 0f, releaseMs = 300f)

        /** Afinación: caída rápida (bombo, "zap") cuando se sube su cantidad. */
        val PITCH = AdsrState(attackMs = 1f, decayMs = 120f, sustain = 0f, releaseMs = 100f)
    }
}
