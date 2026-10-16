package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption

/**
 * Cómo se comporta la muestra al llegar al final de su región de bucle. Los
 * puntos de bucle salen de la propia muestra (chunk `smpl` del WAV); sin ellos
 * los modos de bucle se comportan como [OFF].
 */
enum class LoopMode(override val label: String) : ChoiceOption {
    /** Se reproduce una vez, de principio a fin, y se corta al soltar la nota. */
    OFF("Off"),

    /** Repite la región de bucle mientras la nota esté pulsada y al soltarla se desvanece. */
    FORWARD("Forward"),

    /** Repite la región de bucle mientras la nota esté pulsada; al soltarla continúa **más allá** del bucle (la cola de la muestra). */
    SUSTAIN("Sustain"),

    /** Va y viene (ping-pong) por la región de bucle. */
    BOUNCE("Bounce"),

    /** Se reproduce entera ignorando el *note-off* (percusión). */
    ONE_SHOT("One-Shot");

    /** `true` si el modo repite una región de bucle (cuando la muestra tiene una). */
    val loops: Boolean get() = this == FORWARD || this == SUSTAIN || this == BOUNCE
}
