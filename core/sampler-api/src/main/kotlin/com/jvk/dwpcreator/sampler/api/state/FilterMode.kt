package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption

/** Respuesta del filtro de voz. */
enum class FilterMode(override val label: String) : ChoiceOption {
    LOWPASS_12("LP 12"),
    LOWPASS_24("LP 24"),
    HIGHPASS_12("HP 12"),
    BANDPASS("Band"),
    NOTCH("Notch");

    /** `true` si con el corte totalmente abierto y sin resonancia el filtro no altera la señal (pasa-bajos). */
    val transparentWhenOpen: Boolean get() = this == LOWPASS_12 || this == LOWPASS_24
}
