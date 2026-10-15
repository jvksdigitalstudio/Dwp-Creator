package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.LoopMode

/** Controles de reproducción de la muestra (módulo SAMPLE): afinación, inicio, bucle, sensibilidad, glide. */
object SampleControls {

    val PITCH = VoiceParam(
        "SAMPLE_PITCH", "Pitch", ParamScale(-24f, 24f, 0f, steps = 48), FxUnit.SEMITONES,
        reader = { it.sample.pitchSemitones },
        writer = { s, v -> s.copy(sample = s.sample.copy(pitchSemitones = v)) }
    )
    val FINE = VoiceParam(
        "SAMPLE_FINE", "Fine", ParamScale(-100f, 100f, 0f, steps = 200), FxUnit.CENTS,
        reader = { it.sample.fineCents },
        writer = { s, v -> s.copy(sample = s.sample.copy(fineCents = v)) }
    )
    val PITCH_TRACK = VoiceParam(
        "SAMPLE_PITCH_TRACK", "Key Trk", ParamScale(-1f, 1f, 1f), FxUnit.PERCENT,
        reader = { it.sample.pitchTrack },
        writer = { s, v -> s.copy(sample = s.sample.copy(pitchTrack = v)) }
    )
    val BEND_RANGE = VoiceParam(
        "SAMPLE_PB_RANGE", "PB Range", ParamScale(1f, 24f, 2f, steps = 23), FxUnit.SEMITONES,
        reader = { it.sample.bendRangeSemitones },
        writer = { s, v -> s.copy(sample = s.sample.copy(bendRangeSemitones = v)) }
    )
    val START = VoiceParam(
        "SAMPLE_START", "Start", ParamScale(0f, 1f, 0f), FxUnit.PERCENT,
        reader = { it.sample.startFraction },
        writer = { s, v -> s.copy(sample = s.sample.copy(startFraction = v)) }
    )
    val VELOCITY_SENSITIVITY = VoiceParam(
        "SAMPLE_VEL_SENS", "Vel Sens", ParamScale(0f, 1f, 1f), FxUnit.PERCENT,
        reader = { it.sample.velocitySensitivity },
        writer = { s, v -> s.copy(sample = s.sample.copy(velocitySensitivity = v)) }
    )
    val GLIDE = VoiceParam(
        "SAMPLE_GLIDE", "Glide", ParamScale(0f, 10_000f, 0f, FxCurve.SQUARED), FxUnit.MILLISECONDS,
        reader = { it.sample.glideMs },
        writer = { s, v -> s.copy(sample = s.sample.copy(glideMs = v)) }
    )
    val LOOP_MODE = VoiceParam.choice(
        "SAMPLE_LOOP_MODE", "Loop", LoopMode.FORWARD,
        get = { it.sample.loopMode },
        set = { s, v -> s.copy(sample = s.sample.copy(loopMode = v)) }
    )

    val REVERSE = VoiceToggle(
        "SAMPLE_REVERSE", false,
        reader = { it.sample.reverse },
        writer = { s, v -> s.copy(sample = s.sample.copy(reverse = v)) }
    )
    val LEGATO = VoiceToggle(
        "SAMPLE_LEGATO", false,
        reader = { it.sample.legato },
        writer = { s, v -> s.copy(sample = s.sample.copy(legato = v)) }
    )
    val GLIDE_ONLY_LEGATO = VoiceToggle(
        "SAMPLE_GLIDE_ONLY_LEGATO", true,
        reader = { it.sample.glideOnlyLegato },
        writer = { s, v -> s.copy(sample = s.sample.copy(glideOnlyLegato = v)) }
    )

    val params: List<VoiceParam> =
        listOf(PITCH, FINE, PITCH_TRACK, BEND_RANGE, START, LOOP_MODE, VELOCITY_SENSITIVITY, GLIDE)
    val toggles: List<VoiceToggle> = listOf(REVERSE, LEGATO, GLIDE_ONLY_LEGATO)
}
