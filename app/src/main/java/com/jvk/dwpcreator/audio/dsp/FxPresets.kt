package com.jvk.dwpcreator.audio.dsp

import kotlin.math.abs

/**
 * Los cinco módulos de efecto que tienen presets y reinicio propio. Cada uno
 * declara **qué parámetros e interruptores le pertenecen**: es la única
 * definición que usan los presets, el reinicio de módulo y la detección de
 * "Custom" (nada está duplicado en la UI).
 *
 * [power] es el interruptor de encendido del módulo; **no** forma parte de sus
 * ajustes (un preset no se considera "distinto" por estar el módulo apagado).
 */
enum class FxModule(
    val title: String,
    val power: FxToggle,
    val params: List<FxParam>,
    val toggles: List<FxToggle> = emptyList()
) {
    DRIVE_A("FX DRIVE A", FxToggle.DRIVE_A, listOf(FxParam.DRIVE_A_AMOUNT, FxParam.DRIVE_A_TONE)),
    DRIVE_B("FX DRIVE B", FxToggle.DRIVE_B, listOf(FxParam.DRIVE_B_AMOUNT, FxParam.DRIVE_B_TONE)),
    CHORUS(
        "FX CHORUS", FxToggle.CHORUS,
        listOf(
            FxParam.CHORUS_DELAY, FxParam.CHORUS_DEPTH, FxParam.CHORUS_RATE,
            FxParam.CHORUS_FEEDBACK, FxParam.CHORUS_MIX, FxParam.CHORUS_WIDTH
        )
    ),
    DELAY(
        "FX DELAY", FxToggle.DELAY,
        listOf(
            FxParam.DELAY_TIME, FxParam.DELAY_DIVISION, FxParam.DELAY_BPM, FxParam.DELAY_FEEDBACK,
            FxParam.DELAY_LOW_CUT, FxParam.DELAY_HIGH_CUT, FxParam.DELAY_WOW, FxParam.DELAY_MIX
        ),
        listOf(FxToggle.DELAY_BOUNCE, FxToggle.DELAY_SYNC, FxToggle.DELAY_TAPE)
    ),
    REVERB(
        "FX REVERB", FxToggle.REVERB,
        listOf(
            FxParam.REVERB_ROOM, FxParam.REVERB_DAMP, FxParam.REVERB_DIFFUSION, FxParam.REVERB_DECAY,
            FxParam.REVERB_PREDELAY, FxParam.REVERB_MODULATION, FxParam.REVERB_WIDTH, FxParam.REVERB_MIX
        )
    )
}

/**
 * Un preset: valores para los parámetros e interruptores de **un** módulo.
 * Lo que no se indique toma el valor de fábrica, de modo que un preset
 * describe siempre el módulo completo (requisito para poder detectarlo).
 */
class FxPreset(
    val name: String,
    val values: Map<FxParam, Float>,
    val toggles: Map<FxToggle, Boolean> = emptyMap()
)

/**
 * Presets de fábrica y operaciones sobre ellos. **Todo son funciones puras
 * sobre [SamplerFxState]**: sin estado propio, sin Android, probables en JVM.
 *
 * - [apply]: carga un preset y **enciende** el módulo (quien elige un preset
 *   quiere oírlo).
 * - [activeName]: nombre del preset que coincide con los ajustes actuales o
 *   [CUSTOM]. La comparación es sobre la posición normalizada del knob
 *   (tolerancia [MATCH_TOLERANCE]), la misma que ve el usuario, así que el
 *   redondeo de punto flotante o de la persistencia no rompe la coincidencia.
 * - [reset]: devuelve los ajustes del módulo a los de fábrica **sin cambiar
 *   su encendido**.
 */
object FxPresets {

    const val CUSTOM = "Custom"
    private const val MATCH_TOLERANCE = 0.002f

    private fun drive(prefix: FxModule) = { name: String, amount: Float, tone: Float ->
        val (amountParam, toneParam) = prefix.params
        FxPreset(name, mapOf(amountParam to amount, toneParam to tone))
    }

    private val driveA = drive(FxModule.DRIVE_A)
    private val driveB = drive(FxModule.DRIVE_B)

    private val DRIVE_A_PRESETS = listOf(
        driveA("Subtle Warmth", 0.15f, 10_000f),
        driveA("Tape Glue", 0.30f, 7_000f),
        driveA("Crunch", 0.55f, 6_000f),
        driveA("Overdrive", 0.75f, 5_000f),
        driveA("Fuzz", 1.00f, 3_500f)
    )
    private val DRIVE_B_PRESETS = listOf(
        driveB("Subtle Warmth", 0.15f, 10_000f),
        driveB("Tape Glue", 0.30f, 7_000f),
        driveB("Crunch", 0.55f, 6_000f),
        driveB("Overdrive", 0.75f, 5_000f),
        driveB("Fuzz", 1.00f, 3_500f)
    )

    private val CHORUS_PRESETS = listOf(
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

    private val DELAY_PRESETS = listOf(
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

    private val REVERB_PRESETS = listOf(
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

    /** Presets de fábrica de [module], en el orden en que se muestran. */
    fun forModule(module: FxModule): List<FxPreset> = when (module) {
        FxModule.DRIVE_A -> DRIVE_A_PRESETS
        FxModule.DRIVE_B -> DRIVE_B_PRESETS
        FxModule.CHORUS -> CHORUS_PRESETS
        FxModule.DELAY -> DELAY_PRESETS
        FxModule.REVERB -> REVERB_PRESETS
    }

    /** Aplica [preset] a [module] y enciende el módulo. Los ajustes no mencionados vuelven al valor de fábrica. */
    fun apply(module: FxModule, preset: FxPreset, state: SamplerFxState): SamplerFxState {
        var next = state
        for (param in module.params) next = param.write(next, preset.values[param] ?: param.default)
        for (toggle in module.toggles) next = toggle.write(next, preset.toggles[toggle] ?: toggle.default)
        return module.power.write(next, true)
    }

    /** Devuelve los ajustes de [module] a los de fábrica; el encendido no cambia. */
    fun reset(module: FxModule, state: SamplerFxState): SamplerFxState {
        var next = state
        for (param in module.params) next = param.write(next, param.default)
        for (toggle in module.toggles) next = toggle.write(next, toggle.default)
        return next
    }

    /** `true` si los ajustes de [module] en [state] coinciden con [preset]. */
    fun matches(module: FxModule, preset: FxPreset, state: SamplerFxState): Boolean {
        for (param in module.params) {
            val expected = preset.values[param] ?: param.default
            if (abs(param.toNormalized(param.read(state)) - param.toNormalized(expected)) > MATCH_TOLERANCE) return false
        }
        for (toggle in module.toggles) {
            if (toggle.read(state) != (preset.toggles[toggle] ?: toggle.default)) return false
        }
        return true
    }

    /** Nombre del preset activo de [module] o [CUSTOM] si los ajustes no coinciden con ninguno. */
    fun activeName(module: FxModule, state: SamplerFxState): String =
        forModule(module).firstOrNull { matches(module, it, state) }?.name ?: CUSTOM
}
