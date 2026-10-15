package com.jvk.dwpcreator.sampler.api.state

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption

/** Cómo se fija la velocidad del LFO. */
enum class LfoRateMode(override val label: String) : ChoiceOption {
    /** Frecuencia libre en Hz. */
    HERTZ("Hz"),

    /** Frecuencia derivada del tempo y de una división rítmica ([LfoSync]). */
    TEMPO("Tempo"),

    /** Un solo ciclo por nota: dura `1 / rate` segundos y se queda en su valor final. */
    ONE_SHOT("One-Shot")
}
