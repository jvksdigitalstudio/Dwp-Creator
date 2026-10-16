package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.engine.sample.CachedSample

/**
 * Una nota que se pide al motor: qué muestra, con qué velocidad/volumen/paneo y
 * desde qué tecla. [key] es la nota MIDI **tocada** y [rootKey] la tecla raíz de
 * la muestra; su diferencia, escalada por el seguimiento de teclado, es la
 * transposición. Un valor negativo significa "desconocida" (sin transposición ni
 * seguimiento por tecla).
 */
internal class VoiceNote(
    val token: VoiceToken,
    val sample: CachedSample,
    val velocity: Int,
    val volume: Float,
    val pan: Float,
    val key: Int,
    val rootKey: Int
)
