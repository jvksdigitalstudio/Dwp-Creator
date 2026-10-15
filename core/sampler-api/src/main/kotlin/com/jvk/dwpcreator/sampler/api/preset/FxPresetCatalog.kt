package com.jvk.dwpcreator.sampler.api.preset

import com.jvk.dwpcreator.sampler.api.params.FxParam
import com.jvk.dwpcreator.sampler.api.params.FxToggle
import com.jvk.dwpcreator.sampler.api.params.SamplerParam

/**
 * Presets de fábrica del **bus de efectos** (drive A/B, chorus, delay, reverb).
 * Sólo datos: las operaciones (aplicar, detectar, reiniciar) viven en [FxPresets].
 * Cada preset describe su módulo completo; lo que no mencione toma el valor de fábrica.
 */
internal object FxPresetCatalog {

    private class DriveSpec(val name: String, val amount: Float, val tone: Float)

    /** Los mismos cinco caracteres de saturación sirven a los dos módulos de drive. */
    private val DRIVE_SPECS = listOf(
        DriveSpec("Subtle Warmth", 0.15f, 10_000f),
        DriveSpec("Tape Glue", 0.30f, 7_000f),
        DriveSpec("Crunch", 0.55f, 6_000f),
        DriveSpec("Overdrive", 0.75f, 5_000f),
        DriveSpec("Fuzz", 1.00f, 3_500f)
    )

    private fun drivePresets(amount: SamplerParam, tone: SamplerParam): List<FxPreset> =
        DRIVE_SPECS.map { FxPreset(it.name, mapOf(amount to it.amount, tone to it.tone)) }

    val DRIVE_A_PRESETS: List<FxPreset> = drivePresets(FxParam.DRIVE_A_AMOUNT, FxParam.DRIVE_A_TONE)
    val DRIVE_B_PRESETS: List<FxPreset> = drivePresets(FxParam.DRIVE_B_AMOUNT, FxParam.DRIVE_B_TONE)

    val CHORUS_PRESETS = listOf(
        FxPreset(
            "Classic Chorus",
            mapOf(
                FxParam.CHORUS_DELAY to 7f, FxParam.CHORUS_DEPTH to 4f, FxParam.CHORUS_RATE to 0.8f,
                FxParam.CHORUS_FEEDBACK to 0f, FxParam.CHORUS_MIX to 0.5f, FxParam.CHORUS_WIDTH to 1f
            )
        ),
        FxPreset(
            "Wide Ensemble",
            mapOf(
                FxParam.CHORUS_DELAY to 12f, FxParam.CHORUS_DEPTH to 8f, FxParam.CHORUS_RATE to 0.45f,
                FxParam.CHORUS_FEEDBACK to 0.1f, FxParam.CHORUS_MIX to 0.5f, FxParam.CHORUS_WIDTH to 1.5f
            )
        ),
        FxPreset(
            "Subtle Doubler",
            mapOf(
                FxParam.CHORUS_DELAY to 14f, FxParam.CHORUS_DEPTH to 2.5f, FxParam.CHORUS_RATE to 0.35f,
                FxParam.CHORUS_FEEDBACK to 0f, FxParam.CHORUS_MIX to 0.35f, FxParam.CHORUS_WIDTH to 1f
            )
        ),
        FxPreset(
            "Dimension",
            mapOf(
                FxParam.CHORUS_DELAY to 5f, FxParam.CHORUS_DEPTH to 3f, FxParam.CHORUS_RATE to 0.6f,
                FxParam.CHORUS_FEEDBACK to 0.15f, FxParam.CHORUS_MIX to 0.4f, FxParam.CHORUS_WIDTH to 1.5f
            )
        ),
        FxPreset(
            "Jet Flanger",
            mapOf(
                FxParam.CHORUS_DELAY to 1.2f, FxParam.CHORUS_DEPTH to 3f, FxParam.CHORUS_RATE to 0.25f,
                FxParam.CHORUS_FEEDBACK to 0.7f, FxParam.CHORUS_MIX to 0.5f, FxParam.CHORUS_WIDTH to 1f
            )
        )
    )

    val DELAY_PRESETS = listOf(
        FxPreset(
            "Slapback",
            mapOf(
                FxParam.DELAY_TIME to 110f, FxParam.DELAY_FEEDBACK to 0.15f, FxParam.DELAY_LOW_CUT to 150f,
                FxParam.DELAY_HIGH_CUT to 6_000f, FxParam.DELAY_MIX to 0.3f
            ),
            mapOf(FxToggle.DELAY_BOUNCE to false)
        ),
        FxPreset(
            "Ping-Pong 1/8",
            mapOf(
                FxParam.DELAY_DIVISION to 3f, FxParam.DELAY_BPM to 120f, FxParam.DELAY_FEEDBACK to 0.45f,
                FxParam.DELAY_LOW_CUT to 200f, FxParam.DELAY_HIGH_CUT to 7_000f, FxParam.DELAY_MIX to 0.35f
            ),
            mapOf(FxToggle.DELAY_BOUNCE to true, FxToggle.DELAY_SYNC to true)
        ),
        FxPreset(
            "Dotted Echo",
            mapOf(
                FxParam.DELAY_DIVISION to 4f, FxParam.DELAY_BPM to 110f, FxParam.DELAY_FEEDBACK to 0.5f,
                FxParam.DELAY_LOW_CUT to 180f, FxParam.DELAY_HIGH_CUT to 6_500f, FxParam.DELAY_MIX to 0.35f
            ),
            mapOf(FxToggle.DELAY_BOUNCE to true, FxToggle.DELAY_SYNC to true)
        ),
        FxPreset(
            "Tape Echo",
            mapOf(
                FxParam.DELAY_TIME to 320f, FxParam.DELAY_FEEDBACK to 0.55f, FxParam.DELAY_LOW_CUT to 250f,
                FxParam.DELAY_HIGH_CUT to 3_800f, FxParam.DELAY_WOW to 0.4f, FxParam.DELAY_MIX to 0.35f
            ),
            mapOf(FxToggle.DELAY_BOUNCE to false, FxToggle.DELAY_TAPE to true)
        ),
        FxPreset(
            "Dub Space",
            mapOf(
                FxParam.DELAY_TIME to 450f, FxParam.DELAY_FEEDBACK to 0.75f, FxParam.DELAY_LOW_CUT to 300f,
                FxParam.DELAY_HIGH_CUT to 3_200f, FxParam.DELAY_WOW to 0.3f, FxParam.DELAY_MIX to 0.4f
            ),
            mapOf(FxToggle.DELAY_BOUNCE to true, FxToggle.DELAY_TAPE to true)
        )
    )

    val REVERB_PRESETS = listOf(
        FxPreset(
            "Small Room",
            mapOf(
                FxParam.REVERB_ROOM to 0.15f, FxParam.REVERB_DAMP to 9_000f, FxParam.REVERB_DIFFUSION to 0.7f,
                FxParam.REVERB_DECAY to 0.5f, FxParam.REVERB_PREDELAY to 0f, FxParam.REVERB_MODULATION to 0.15f,
                FxParam.REVERB_WIDTH to 1f, FxParam.REVERB_MIX to 0.2f
            )
        ),
        FxPreset(
            "Bright Chamber",
            mapOf(
                FxParam.REVERB_ROOM to 0.4f, FxParam.REVERB_DAMP to 16_000f, FxParam.REVERB_DIFFUSION to 0.75f,
                FxParam.REVERB_DECAY to 1.2f, FxParam.REVERB_PREDELAY to 5f, FxParam.REVERB_MODULATION to 0.2f,
                FxParam.REVERB_WIDTH to 1f, FxParam.REVERB_MIX to 0.25f
            )
        ),
        FxPreset(
            "Plate",
            mapOf(
                FxParam.REVERB_ROOM to 0.35f, FxParam.REVERB_DAMP to 12_000f, FxParam.REVERB_DIFFUSION to 0.9f,
                FxParam.REVERB_DECAY to 1.6f, FxParam.REVERB_PREDELAY to 10f, FxParam.REVERB_MODULATION to 0.3f,
                FxParam.REVERB_WIDTH to 1.2f, FxParam.REVERB_MIX to 0.3f
            )
        ),
        FxPreset(
            "Hall",
            mapOf(
                FxParam.REVERB_ROOM to 0.7f, FxParam.REVERB_DAMP to 7_000f, FxParam.REVERB_DIFFUSION to 0.8f,
                FxParam.REVERB_DECAY to 3.2f, FxParam.REVERB_PREDELAY to 25f, FxParam.REVERB_MODULATION to 0.35f,
                FxParam.REVERB_WIDTH to 1.3f, FxParam.REVERB_MIX to 0.3f
            )
        ),
        FxPreset(
            "Cathedral",
            mapOf(
                FxParam.REVERB_ROOM to 1f, FxParam.REVERB_DAMP to 5_500f, FxParam.REVERB_DIFFUSION to 0.85f,
                FxParam.REVERB_DECAY to 7f, FxParam.REVERB_PREDELAY to 40f, FxParam.REVERB_MODULATION to 0.4f,
                FxParam.REVERB_WIDTH to 1.5f, FxParam.REVERB_MIX to 0.35f
            )
        ),
        FxPreset(
            "Ambient Wash",
            mapOf(
                FxParam.REVERB_ROOM to 0.8f, FxParam.REVERB_DAMP to 4_500f, FxParam.REVERB_DIFFUSION to 1f,
                FxParam.REVERB_DECAY to 12f, FxParam.REVERB_PREDELAY to 60f, FxParam.REVERB_MODULATION to 0.6f,
                FxParam.REVERB_WIDTH to 1.5f, FxParam.REVERB_MIX to 0.45f
            )
        )
    )
}
