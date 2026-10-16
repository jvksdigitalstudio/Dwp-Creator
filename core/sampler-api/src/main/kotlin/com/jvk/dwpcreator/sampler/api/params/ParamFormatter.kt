package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import java.util.Locale
import kotlin.math.roundToInt

/** Conversión valor -> texto de cada [FxUnit]. Único punto de formato de la UI ("25.00%", "11.25 kHz", "1/8."...). */
object ParamFormatter {

    fun format(unit: FxUnit, value: Float, scale: ParamScale, labels: List<String>?): String = when (unit) {
        FxUnit.PERCENT -> String.format(Locale.US, "%.2f%%", value * 100f)
        FxUnit.DECIBEL ->
            if (value <= scale.min) "-inf dB" else String.format(Locale.US, "%+.1f dB", value)
        FxUnit.HERTZ -> hertz(value)
        FxUnit.MILLISECONDS ->
            if (value >= 1000f) String.format(Locale.US, "%.3f s", value / 1000f)
            else String.format(Locale.US, "%.2f ms", value)
        FxUnit.SECONDS -> String.format(Locale.US, "%.2f sec", value)
        FxUnit.BPM -> String.format(Locale.US, "%.1f BPM", value)
        FxUnit.SEMITONES -> signed(value.roundToInt(), "st")
        FxUnit.CENTS -> signed(value.roundToInt(), "ct")
        FxUnit.CUTOFF -> hertz(FilterScaling.cutoffHz(value))
        FxUnit.DEGREES -> String.format(Locale.US, "%d\u00B0", (value * 360f).roundToInt())
        FxUnit.CHOICE ->
            if (labels.isNullOrEmpty()) "" else labels[value.roundToInt().coerceIn(0, labels.size - 1)]
    }

    private fun hertz(value: Float): String =
        if (value >= 1000f) String.format(Locale.US, "%.2f kHz", value / 1000f)
        else String.format(Locale.US, "%.1f Hz", value)

    private fun signed(value: Int, suffix: String): String =
        if (value == 0) "0 $suffix" else String.format(Locale.US, "%+d %s", value, suffix)
}
