package com.jvk.dwpcreator.sampler.engine.sample

import com.jvk.dwpcreator.codec.wav.SampleLoop

/** Resultado listo para reproducir: PCM de 16 bits + el formato con el que hay que abrir el AudioTrack. */
data class CachedSample(
    val pcm16: ByteArray,
    val sampleRateHz: Int,
    val channelCount: Int,
    /** Región de bucle de la muestra (frames), si la trae; ver [WavLoopPoints]. */
    val loop: SampleLoop? = null
)
