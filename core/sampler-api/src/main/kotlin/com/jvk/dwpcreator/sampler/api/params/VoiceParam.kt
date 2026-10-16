package com.jvk.dwpcreator.sampler.api.params

import com.jvk.dwpcreator.sampler.api.options.ChoiceOption
import com.jvk.dwpcreator.sampler.api.options.optionAt
import com.jvk.dwpcreator.sampler.api.options.optionLabels
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.sampler.api.state.VoiceState

/**
 * Parámetro numérico de la síntesis por voz. **Una sola clase** para todos los
 * grupos (muestra, filtro, envolventes, LFO): cada grupo se limita a declarar
 * sus instancias con su escala y las funciones de lectura/escritura sobre
 * [VoiceState].
 */
class VoiceParam(
    override val key: String,
    override val label: String,
    override val scale: ParamScale,
    override val unit: FxUnit,
    override val labels: List<String>? = null,
    private val reader: (VoiceState) -> Float,
    private val writer: (VoiceState, Float) -> VoiceState
) : SamplerParam {

    override fun read(state: SamplerFxState): Float = reader(state.voice)

    override fun apply(state: SamplerFxState, value: Float): SamplerFxState =
        state.withVoice { writer(it, value) }

    companion object {
        /**
         * Parámetro de elección sobre un enum de opciones [E]: las etiquetas, el
         * rango escalonado y la conversión índice <-> opción salen del propio enum.
         */
        inline fun <reified E> choice(
            key: String,
            label: String,
            default: E,
            crossinline get: (VoiceState) -> E,
            crossinline set: (VoiceState, E) -> VoiceState
        ): VoiceParam where E : Enum<E>, E : ChoiceOption {
            val labels = optionLabels<E>()
            return VoiceParam(
                key = key,
                label = label,
                scale = ParamScale(0f, (labels.size - 1).toFloat(), default.ordinal.toFloat(), steps = labels.size - 1),
                unit = FxUnit.CHOICE,
                labels = labels,
                reader = { get(it).ordinal.toFloat() },
                writer = { state, value -> set(state, optionAt<E>(value)) }
            )
        }
    }
}
