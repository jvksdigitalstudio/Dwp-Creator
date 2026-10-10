package com.jvk.dwpcreator.audio.dsp

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Estado completo, **inmutable**, del bus maestro del sampler (módulos
 * MASTER, FX DRIVE A/B, FX DELAY, FX REVERB, FX CHORUS -- la misma
 * organización que la pestaña PROGRAM de DirectWave de escritorio).
 *
 * Inmutable a propósito: la UI y el ViewModel producen una instancia nueva
 * con `copy()` y el hilo de audio simplemente lee la referencia vigente
 * (`@Volatile`) al inicio de cada bloque -- sin locks y sin que el hilo de
 * audio pueda ver nunca un estado a medio escribir.
 *
 * Los valores por defecto de reverb y chorus son los que muestra DirectWave
 * al abrirse (Room 25 %, Damp 11.25 kHz, Diffusion 75 %, Decay 1.29 s;
 * Delay 3.09 ms, Depth 6 ms, Rate 1.25 Hz, Feedback 0 %). **Todos los
 * efectos arrancan apagados**: con el estado por defecto el sonido es
 * idéntico al de antes de existir este bus (ganancia 0 dB, nada insertado).
 */
data class SamplerFxState(
    // MASTER
    val masterVolumeDb: Float = 0f,
    val monophonic: Boolean = false,

    // FX DRIVE A (antes de los efectos) y B (después, asimétrico)
    val driveAEnabled: Boolean = false,
    val driveAAmount: Float = 0f,
    val driveAToneHz: Float = 20_000f,
    val driveBEnabled: Boolean = false,
    val driveBAmount: Float = 0f,
    val driveBToneHz: Float = 20_000f,

    // FX CHORUS
    val chorusEnabled: Boolean = false,
    val chorusDelayMs: Float = 3.09f,
    val chorusDepthMs: Float = 6f,
    val chorusRateHz: Float = 1.25f,
    val chorusFeedback: Float = 0f,
    val chorusMix: Float = 0.5f,
    val chorusWidth: Float = 1f,

    // FX DELAY
    val delayEnabled: Boolean = false,
    val delayTimeMs: Float = 375f,
    val delayFeedback: Float = 0.5f,
    val delayLowCutHz: Float = 120f,
    val delayHighCutHz: Float = 8000f,
    val delayBounce: Boolean = true,
    val delayMix: Float = 0.35f,
    /** `true` = el tiempo sale de [delayBpm] y [delayDivision]; `false` = [delayTimeMs] libre. */
    val delaySync: Boolean = false,
    val delayBpm: Float = 120f,
    /** Índice en [DelaySync.NAMES]. */
    val delayDivision: Int = DelaySync.DEFAULT_INDEX,
    /** Modo cinta: saturación en el bucle + wow/flutter ([delayWow]). */
    val delayTape: Boolean = false,
    val delayWow: Float = 0.3f,

    // FX REVERB
    val reverbEnabled: Boolean = false,
    val reverbRoom: Float = 0.25f,
    val reverbDampHz: Float = 11_250f,
    val reverbDiffusion: Float = 0.75f,
    val reverbDecaySec: Float = 1.29f,
    val reverbMix: Float = 0.3f,
    val reverbPreDelayMs: Float = 0f,
    val reverbModulation: Float = 0.2f,
    val reverbWidth: Float = 1f
)

/** Cómo se reparte el recorrido de un knob entre su mínimo y su máximo. */
enum class FxCurve { LINEAR, LOG }

enum class FxUnit { PERCENT, DECIBEL, HERTZ, MILLISECONDS, SECONDS, BPM, DIVISION }

/**
 * Descriptor de un parámetro numérico del bus: rango, valor de fábrica,
 * curva del knob, unidad de visualización y cómo se lee/escribe en
 * [SamplerFxState]. **Una sola definición** alimenta al motor DSP (rangos de
 * seguridad), a la UI (mapeo knob <-> valor, texto) y a la persistencia
 * (clave estable = [name]).
 *
 * Con [FxCurve.LOG], [min] debe ser > 0.
 */
enum class FxParam(
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val curve: FxCurve,
    val unit: FxUnit,
    private val reader: (SamplerFxState) -> Float,
    private val writer: (SamplerFxState, Float) -> SamplerFxState,
    /** `0` = continuo. `n > 0` = parámetro escalonado de `n` intervalos (`n + 1` posiciones), p. ej. la división rítmica del delay. */
    val steps: Int = 0
) {
    MASTER_VOLUME("Volume", -48f, 6f, 0f, FxCurve.LINEAR, FxUnit.DECIBEL,
        { it.masterVolumeDb }, { s, v -> s.copy(masterVolumeDb = v) }),

    DRIVE_A_AMOUNT("Amount", 0f, 1f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.driveAAmount }, { s, v -> s.copy(driveAAmount = v) }),
    DRIVE_A_TONE("Tone", 500f, 20_000f, 20_000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.driveAToneHz }, { s, v -> s.copy(driveAToneHz = v) }),
    DRIVE_B_AMOUNT("Amount", 0f, 1f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.driveBAmount }, { s, v -> s.copy(driveBAmount = v) }),
    DRIVE_B_TONE("Tone", 500f, 20_000f, 20_000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.driveBToneHz }, { s, v -> s.copy(driveBToneHz = v) }),

    CHORUS_DELAY("Delay", 0.5f, 20f, 3.09f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.chorusDelayMs }, { s, v -> s.copy(chorusDelayMs = v) }),
    CHORUS_DEPTH("Depth", 0f, 15f, 6f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.chorusDepthMs }, { s, v -> s.copy(chorusDepthMs = v) }),
    CHORUS_RATE("Rate", 0.05f, 10f, 1.25f, FxCurve.LOG, FxUnit.HERTZ,
        { it.chorusRateHz }, { s, v -> s.copy(chorusRateHz = v) }),
    CHORUS_FEEDBACK("Feedback", 0f, 0.9f, 0f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusFeedback }, { s, v -> s.copy(chorusFeedback = v) }),
    CHORUS_MIX("Mix", 0f, 1f, 0.5f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusMix }, { s, v -> s.copy(chorusMix = v) }),
    CHORUS_WIDTH("Width", 0f, 1.5f, 1f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.chorusWidth }, { s, v -> s.copy(chorusWidth = v) }),

    DELAY_TIME("Time", 10f, 2000f, 375f, FxCurve.LOG, FxUnit.MILLISECONDS,
        { it.delayTimeMs }, { s, v -> s.copy(delayTimeMs = v) }),
    DELAY_FEEDBACK("Feedback", 0f, 0.95f, 0.5f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayFeedback }, { s, v -> s.copy(delayFeedback = v) }),
    DELAY_LOW_CUT("Low cut", 20f, 2000f, 120f, FxCurve.LOG, FxUnit.HERTZ,
        { it.delayLowCutHz }, { s, v -> s.copy(delayLowCutHz = v) }),
    DELAY_HIGH_CUT("High cut", 500f, 20000f, 8000f, FxCurve.LOG, FxUnit.HERTZ,
        { it.delayHighCutHz }, { s, v -> s.copy(delayHighCutHz = v) }),
    DELAY_MIX("Mix", 0f, 1f, 0.35f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayMix }, { s, v -> s.copy(delayMix = v) }),
    DELAY_BPM("BPM", 40f, 240f, 120f, FxCurve.LINEAR, FxUnit.BPM,
        { it.delayBpm }, { s, v -> s.copy(delayBpm = v) }),
    DELAY_DIVISION("Division", 0f, 9f, DelaySync.DEFAULT_INDEX.toFloat(), FxCurve.LINEAR, FxUnit.DIVISION,
        { it.delayDivision.toFloat() }, { s, v -> s.copy(delayDivision = DelaySync.indexOf(v)) }, steps = 9),
    DELAY_WOW("Wow", 0f, 1f, 0.3f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.delayWow }, { s, v -> s.copy(delayWow = v) }),

    REVERB_ROOM("Room", 0f, 1f, 0.25f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbRoom }, { s, v -> s.copy(reverbRoom = v) }),
    REVERB_DAMP("Damp", 1000f, 20000f, 11_250f, FxCurve.LOG, FxUnit.HERTZ,
        { it.reverbDampHz }, { s, v -> s.copy(reverbDampHz = v) }),
    REVERB_DIFFUSION("Diffusion", 0f, 1f, 0.75f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbDiffusion }, { s, v -> s.copy(reverbDiffusion = v) }),
    REVERB_DECAY("Decay", 0.1f, 20f, 1.29f, FxCurve.LOG, FxUnit.SECONDS,
        { it.reverbDecaySec }, { s, v -> s.copy(reverbDecaySec = v) }),
    REVERB_MIX("Mix", 0f, 1f, 0.3f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbMix }, { s, v -> s.copy(reverbMix = v) }),
    REVERB_PREDELAY("Pre-dly", 0f, 250f, 0f, FxCurve.LINEAR, FxUnit.MILLISECONDS,
        { it.reverbPreDelayMs }, { s, v -> s.copy(reverbPreDelayMs = v) }),
    REVERB_MODULATION("Mod", 0f, 1f, 0.2f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbModulation }, { s, v -> s.copy(reverbModulation = v) }),
    REVERB_WIDTH("Width", 0f, 1.5f, 1f, FxCurve.LINEAR, FxUnit.PERCENT,
        { it.reverbWidth }, { s, v -> s.copy(reverbWidth = v) });

    fun read(state: SamplerFxState): Float = reader(state)

    /** Escribe [value] (saturado a [min]..[max] y, si es escalonado, ajustado al paso más cercano) y devuelve el estado nuevo. */
    fun write(state: SamplerFxState, value: Float): SamplerFxState =
        writer(state, snap(value.coerceIn(min, max)))

    /** Ajusta [value] a la rejilla de [steps]; los parámetros continuos lo devuelven intacto. */
    private fun snap(value: Float): Float {
        if (steps <= 0) return value
        val stepSize = (max - min) / steps
        return min + ((value - min) / stepSize).roundToInt() * stepSize
    }

    fun toNormalized(value: Float): Float {
        val v = value.coerceIn(min, max)
        val n = when (curve) {
            FxCurve.LINEAR -> (v - min) / (max - min)
            FxCurve.LOG -> ln(v / min) / ln(max / min)
        }
        return n.coerceIn(0f, 1f)
    }

    fun fromNormalized(normalized: Float): Float {
        val n = normalized.coerceIn(0f, 1f)
        return snap(
            when (curve) {
                FxCurve.LINEAR -> min + n * (max - min)
                FxCurve.LOG -> min * (max / min).pow(n)
            }
        )
    }

    /** Texto del valor tal como lo muestra DirectWave ("25.00%", "11.25 kHz", "1.29 sec"...). */
    fun format(value: Float): String = when (unit) {
        FxUnit.PERCENT -> String.format(Locale.US, "%.2f%%", value * 100f)
        FxUnit.DECIBEL ->
            if (value <= min) "-inf dB" else String.format(Locale.US, "%+.1f dB", value)
        FxUnit.HERTZ ->
            if (value >= 1000f) String.format(Locale.US, "%.2f kHz", value / 1000f)
            else String.format(Locale.US, "%.1f Hz", value)
        FxUnit.MILLISECONDS ->
            if (value >= 1000f) String.format(Locale.US, "%.3f s", value / 1000f)
            else String.format(Locale.US, "%.2f ms", value)
        FxUnit.SECONDS -> String.format(Locale.US, "%.2f sec", value)
        FxUnit.BPM -> String.format(Locale.US, "%.1f BPM", value)
        FxUnit.DIVISION -> DelaySync.name(DelaySync.indexOf(value))
    }
}

/** Interruptores del bus (encendido de cada módulo + modos de dos estados). Clave de persistencia = [name]. */
enum class FxToggle(
    private val reader: (SamplerFxState) -> Boolean,
    private val writer: (SamplerFxState, Boolean) -> SamplerFxState,
    val default: Boolean
) {
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

    fun read(state: SamplerFxState): Boolean = reader(state)
    fun write(state: SamplerFxState, value: Boolean): SamplerFxState = writer(state, value)
}
