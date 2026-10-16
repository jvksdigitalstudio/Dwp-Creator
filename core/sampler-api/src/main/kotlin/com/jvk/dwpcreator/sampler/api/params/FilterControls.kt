package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.state.FilterMode

/** Controles del filtro por voz (módulo FILTER). */
object FilterControls {

    val ENABLED = VoiceToggle(
        "FILTER_ENABLED", true,
        reader = { it.filter.enabled },
        writer = { s, v -> s.copy(filter = s.filter.copy(enabled = v)) }
    )

    val MODE = VoiceParam.choice(
        "FILTER_MODE", "Mode", FilterMode.LOWPASS_12,
        get = { it.filter.mode },
        set = { s, v -> s.copy(filter = s.filter.copy(mode = v)) }
    )
    val CUTOFF = VoiceParam(
        "FILTER_CUTOFF", "Cutoff", ParamScale(0f, 1f, 1f), FxUnit.CUTOFF,
        reader = { it.filter.cutoff },
        writer = { s, v -> s.copy(filter = s.filter.copy(cutoff = v)) }
    )
    val RESONANCE = VoiceParam(
        "FILTER_RESONANCE", "Resonance", ParamScale(0f, 1f, 0f), FxUnit.PERCENT,
        reader = { it.filter.resonance },
        writer = { s, v -> s.copy(filter = s.filter.copy(resonance = v)) }
    )
    val DRIVE = VoiceParam(
        "FILTER_DRIVE", "Drive", ParamScale(0f, 1f, 0f), FxUnit.PERCENT,
        reader = { it.filter.drive },
        writer = { s, v -> s.copy(filter = s.filter.copy(drive = v)) }
    )
    val KEY_TRACK = VoiceParam(
        "FILTER_KEYTRACK", "Key Trk", ParamScale(-1f, 1f, 0f), FxUnit.PERCENT,
        reader = { it.filter.keyTrack },
        writer = { s, v -> s.copy(filter = s.filter.copy(keyTrack = v)) }
    )
    val VELOCITY_TRACK = VoiceParam(
        "FILTER_VELTRACK", "Vel Trk", ParamScale(-1f, 1f, 0f), FxUnit.PERCENT,
        reader = { it.filter.velocityTrack },
        writer = { s, v -> s.copy(filter = s.filter.copy(velocityTrack = v)) }
    )

    val params: List<VoiceParam> = listOf(MODE, CUTOFF, RESONANCE, DRIVE, KEY_TRACK, VELOCITY_TRACK)
    val toggles: List<VoiceToggle> = listOf(ENABLED)
}
