package com.jvk.dwpcreator.sampler.api

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/**
 * Contrato de reproducción que la capa de aplicación exige al motor de audio.
 *
 * La presentación depende de esta interfaz y nunca de una clase concreta de
 * Android o del motor: la implementación real vive en `sampler-android`
 * (`SamplePlayer`) y se sustituye por un doble en las pruebas unitarias.
 */
interface SamplerPlayback {

    /**
     * Decodifica y cachea por adelantado el audio de [audioByIndex] y abre el
     * stream de salida. Llamar desde un hilo de fondo; es de mejor esfuerzo.
     */
    fun prewarm(audioByIndex: List<ByteArray>)

    /**
     * Dispara la muestra [index] a [velocity] (1..127) con el [volume]
     * (0f..1f) y [pan] (-1f..1f) vigentes de su mini-mezclador. Suena hasta
     * que termina o hasta que [stopNote] la libera.
     *
     * [key] es la nota MIDI tocada y [rootKey] la tecla raíz de la muestra
     * (negativas = desconocidas): su diferencia transpone la muestra y [key]
     * alimenta el seguimiento de teclado del filtro y el glide.
     */
    fun play(
        index: Int,
        wavBytes: ByteArray,
        velocity: Int = 127,
        volume: Float = 1f,
        pan: Float = 0f,
        key: Int = -1,
        rootKey: Int = -1
    )

    /** Libera (con fundido, sin clic) la voz más reciente de [index] que siga sonando. */
    fun stopNote(index: Int)

    /** Rueda de pitch bend (-1..1); afecta a las voces que suenan y a las siguientes. */
    fun setPitchBend(value: Float)

    /** Estado actual del bus de efectos; se adopta en el siguiente bloque de audio, sin clics. */
    fun setFxState(state: SamplerFxState)

    /** Picos de salida recientes (izquierdo, derecho) para el vúmetro. */
    fun outputPeaks(): Pair<Float, Float>

    /** Carga DSP, latencia de búfer y underruns medidos en vivo. */
    fun audioStats(): AudioStats

    /**
     * Apaga cualquier nota que siguiera sonando y vacía la caché de audio
     * decodificado. Debe llamarse al cargar un instrumento nuevo, porque la
     * caché indexa por posición de muestra y dos instrumentos reutilizan las
     * mismas posiciones.
     */
    fun resetForNewProject()

    /** Detiene el hilo de audio y libera el stream. */
    fun releaseAll()
}
