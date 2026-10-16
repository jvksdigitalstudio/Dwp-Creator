package com.jvk.dwpcreator.sampler.api.state

/** Qué modula cada una de las tres envolventes de voz. */
enum class EnvelopeTarget(
    val keyPrefix: String,
    val title: String,
    /** Valores de fábrica de la ADSR. */
    val defaults: AdsrState,
    /** Ataque mínimo: la amplitud conserva 3 ms contra clics; las de modulación admiten 1 ms. */
    val minAttackMs: Float,
    /** `false` para la amplitud (siempre al 100 %); `true` si tiene cantidad bipolar. */
    val hasAmount: Boolean
) {
    AMP("AMP", "AMP ENV", AdsrState.AMP, 3f, false),
    FILTER("FLT_ENV", "FILTER ENV", AdsrState.FILTER, 1f, true),
    PITCH("PIT_ENV", "PITCH ENV", AdsrState.PITCH, 1f, true)
}
