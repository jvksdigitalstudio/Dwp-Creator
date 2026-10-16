package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption

/** Forma de onda del LFO. Las formas aleatorias se evalúan en [LfoWaveforms]. */
enum class LfoShape(override val label: String) : ChoiceOption {
    SINE("Sine"),
    TRIANGLE("Triangle"),

    /** Ataque brusco y caída lineal (diente de sierra descendente). */
    SAW("Saw"),
    SQUARE("Square"),

    /** Muestrea un valor aleatorio nuevo en cada ciclo y lo mantiene. */
    SAMPLE_HOLD("S&H"),

    /** Valores aleatorios unidos por una transición suave. */
    SMOOTH_RANDOM("Smooth")
}
