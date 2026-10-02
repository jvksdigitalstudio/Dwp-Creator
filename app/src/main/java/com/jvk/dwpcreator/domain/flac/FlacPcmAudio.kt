package com.jvk.dwpcreator.domain.flac

/**
 * Audio PCM ya normalizado, listo para codificar como FLAC: canales
 * deinterleaved como arrays de muestras enteras con signo, y los tres
 * parámetros de formato que FLAC necesita.
 *
 * Deliberadamente agnóstico de DWP y de WAV -- [FlacEncoder] no debe
 * conocer nada de DirectWave (Sección 7 del prompt maestro de esta fase:
 * "FlacEncoder NO debe conocer detalles internos de DWP"). La conversión
 * desde `WavDecoder.DecodedWav` vive en
 * `com.jvk.dwpcreator.domain.dwp.monolithic.PcmNormalizer`, en la capa que
 * sí conoce ambos mundos.
 */
data class FlacPcmAudio(
    val channels: Array<IntArray>,
    val sampleRateHz: Int,
    val bitsPerSample: Int
) {
    init {
        require(channels.isNotEmpty()) { "channels no puede estar vacío" }
        require(channels.all { it.size == channels[0].size }) { "Todos los canales deben tener el mismo número de muestras" }
        require(channels.size in 1..8) { "channelCount=${channels.size} fuera de rango soportado por este encoder (1..8)" }
        require(sampleRateHz in 1..0xFFFFF) { "sampleRateHz=$sampleRateHz fuera de rango representable en STREAMINFO (1..1048575)" }
        require(bitsPerSample in 4..32) { "bitsPerSample=$bitsPerSample fuera de rango representable en STREAMINFO (4..32)" }
    }

    val channelCount: Int get() = channels.size
    val frameCount: Int get() = channels[0].size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlacPcmAudio) return false
        return sampleRateHz == other.sampleRateHz &&
            bitsPerSample == other.bitsPerSample &&
            channels.size == other.channels.size &&
            channels.indices.all { channels[it].contentEquals(other.channels[it]) }
    }

    override fun hashCode(): Int {
        var result = sampleRateHz
        result = 31 * result + bitsPerSample
        for (c in channels) result = 31 * result + c.contentHashCode()
        return result
    }
}
