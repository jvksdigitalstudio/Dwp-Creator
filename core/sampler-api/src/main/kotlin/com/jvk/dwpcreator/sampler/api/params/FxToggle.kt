package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/** Interruptores del bus (encendido de cada módulo + modos de dos estados). Clave de persistencia = [name]. */
enum class FxToggle(
    private val reader: (SamplerFxState) -> Boolean,
    private val writer: (SamplerFxState, Boolean) -> SamplerFxState,
    override val default: Boolean
) : SamplerToggle {
    DRIVE_A({ it.driveAEnabled }, { s, v -> s.copy(driveAEnabled = v) }, false),
    DRIVE_B({ it.driveBEnabled }, { s, v -> s.copy(driveBEnabled = v) }, false),
    CHORUS({ it.chorusEnabled }, { s, v -> s.copy(chorusEnabled = v) }, false),
    DELAY({ it.delayEnabled }, { s, v -> s.copy(delayEnabled = v) }, false),
    REVERB({ it.reverbEnabled }, { s, v -> s.copy(reverbEnabled = v) }, false),
    /** `true` = ping-pong ("Bounce"), `false` = "Normal". */
    DELAY_BOUNCE({ it.delayBounce }, { s, v -> s.copy(delayBounce = v) }, true),
    /** `true` = tiempo por BPM + división rítmica, `false` = tiempo libre en ms. */
    DELAY_SYNC({ it.delaySync }, { s, v -> s.copy(delaySync = v) }, false),
    /** `true` = eco de cinta (saturación en el bucle + wow/flutter). */
    DELAY_TAPE({ it.delayTape }, { s, v -> s.copy(delayTape = v) }, false),
    /** `true` = Mono (una nota corta la anterior), `false` = Poly. */
    MONOPHONIC({ it.monophonic }, { s, v -> s.copy(monophonic = v) }, false);

    override val key: String get() = name
    override fun read(state: SamplerFxState): Boolean = reader(state)
    override fun write(state: SamplerFxState, value: Boolean): SamplerFxState = writer(state, value)
}
