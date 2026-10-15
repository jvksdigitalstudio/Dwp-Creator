package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption

/** Parámetro de voz que modula un LFO. */
enum class LfoDestination(override val label: String) : ChoiceOption {
    PITCH("Pitch"),
    FILTER("Filter"),
    LEVEL("Level"),
    PAN("Pan"),
    RESONANCE("Reso")
}
