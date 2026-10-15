package com.jvk.dwpcreator.codec.wav

/** Resultado de [WavDecoder.decode]: PCM crudo del chunk `data` más el formato necesario para interpretarlo. */
data class DecodedWav(
    val sampleRateHz: Int,
    val channelCount: Int,
    val format: SampleFormat,
    /** Raw PCM bytes exactly as stored in the file's `data` chunk (little-endian). */
    val pcmData: ByteArray,
    /** Región de bucle del chunk `smpl`, si el archivo la trae y es coherente ([WavLoopPoints]). */
    val loop: SampleLoop? = null
) {
    val bytesPerSample: Int
        get() = when (format) {
            SampleFormat.PCM_8 -> 1
            SampleFormat.PCM_16 -> 2
            SampleFormat.PCM_24 -> 3
            SampleFormat.PCM_32, SampleFormat.FLOAT_32 -> 4
        }
    val bytesPerFrame: Int get() = bytesPerSample * channelCount
    val frameCount: Int get() = pcmData.size / bytesPerFrame
}
