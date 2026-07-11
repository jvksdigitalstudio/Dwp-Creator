package com.jvk.dwpcreator.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts 32-bit IEEE-float PCM (the format DirectWave/FL Studio Desktop
 * actually exports) to 16-bit signed integer PCM.
 *
 * Why this exists: `AudioTrack` technically supports `ENCODING_PCM_FLOAT`
 * since API 21, but in practice not every device's audio HAL honors it --
 * some silently fail to produce sound with no error at all. 16-bit PCM is
 * the one format guaranteed to work on every Android device ever shipped,
 * so playback always converts down to it rather than gambling on float
 * support.
 */
object PcmConverter {

    fun floatToInt16(floatPcm: ByteArray): ByteArray {
        val sampleCount = floatPcm.size / 4
        val input = ByteBuffer.wrap(floatPcm).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteArray(sampleCount * 2)
        val outputBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val sample = input.getFloat(i * 4).coerceIn(-1f, 1f)
            val scaled = (sample * Short.MAX_VALUE).toInt().toShort()
            outputBuffer.putShort(i * 2, scaled)
        }
        return output
    }
}
