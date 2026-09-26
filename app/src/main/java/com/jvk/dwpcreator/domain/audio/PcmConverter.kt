package com.jvk.dwpcreator.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts any of the PCM/float formats [WavDecoder] can now decode down to
 * 16-bit signed integer PCM, the one format [AudioTrack] is guaranteed to
 * support on every real device (see [com.jvk.dwpcreator.audio.SamplePlayer]
 * for why float playback isn't used directly).
 */
object PcmConverter {

    fun floatToInt16(floatPcm: ByteArray): ByteArray {
        val sampleCount = floatPcm.size / 4
        val input = ByteBuffer.wrap(floatPcm).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteArray(sampleCount * 2)
        val outputBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            // FASE 2.1 (Doc/28): comportamiento explícito para entradas no
            // finitas, auditado y verificado con tests
            // (PcmConverterTest), no solo observado por accidente:
            // - NaN: `Float.coerceIn` no reordena NaN (todas las
            //   comparaciones con NaN son `false` en IEEE 754), así que
            //   `coerceIn` lo deja pasar sin recortar; `(Float.NaN).toInt()`
            //   está definido por la especificación de Kotlin/Java como `0`,
            //   así que una muestra NaN termina en silencio digital (0), no
            //   en un crash ni en un valor indefinido.
            // - +Infinity/-Infinity: sí quedan correctamente recortados por
            //   `coerceIn` (Infinity > 1f y -Infinity < -1f son comparaciones
            //   IEEE 754 válidas), igual que cualquier otro valor fuera de
            //   rango.
            // - -0.0f: no es < -1f ni > 1f, pasa sin cambios; `(-0.0f).toInt()
            //   == 0`, así que termina igual que 0.0f (silencio), sin signo
            //   negativo espurio.
            // Es el camino de reproducción en pantalla (nunca escribe al
            // `.dwp`), así que "convertir a silencio" para una muestra
            // corrupta es la elección más segura posible -- preferible a
            // que la previsualización lance una excepción o reproduzca
            // ruido indefinido.
            val sample = input.getFloat(i * 4).coerceIn(-1f, 1f)
            val scaled = (sample * Short.MAX_VALUE).toInt().toShort()
            outputBuffer.putShort(i * 2, scaled)
        }
        return output
    }

    /** Unsigned 8-bit PCM (WAV convention: 0..255, centered at 128) -> signed 16-bit. */
    fun uint8ToInt16(pcm8: ByteArray): ByteArray {
        val output = ByteArray(pcm8.size * 2)
        val outputBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        for (i in pcm8.indices) {
            val unsigned = pcm8[i].toInt() and 0xFF
            val centered = (unsigned - 128) shl 8 // scale 8-bit range up to 16-bit
            outputBuffer.putShort(i * 2, centered.toShort())
        }
        return output
    }

    /** Signed 24-bit little-endian PCM -> signed 16-bit (drop the low byte of each sample). */
    fun int24ToInt16(pcm24: ByteArray): ByteArray {
        val sampleCount = pcm24.size / 3
        val output = ByteArray(sampleCount * 2)
        val outputBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val base = i * 3
            // Sign-extend the 24-bit sample, then keep only the top 16 bits.
            val raw = (pcm24[base].toInt() and 0xFF) or
                ((pcm24[base + 1].toInt() and 0xFF) shl 8) or
                (pcm24[base + 2].toInt() shl 16) // top byte carries the sign
            val clamped = raw.coerceIn(-8388608, 8388607)
            outputBuffer.putShort(i * 2, (clamped shr 8).toShort())
        }
        return output
    }

    /** Signed 32-bit little-endian integer PCM -> signed 16-bit (drop the low 16 bits of each sample). */
    fun int32ToInt16(pcm32: ByteArray): ByteArray {
        val sampleCount = pcm32.size / 4
        val input = ByteBuffer.wrap(pcm32).order(ByteOrder.LITTLE_ENDIAN)
        val output = ByteArray(sampleCount * 2)
        val outputBuffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val sample = input.getInt(i * 4)
            outputBuffer.putShort(i * 2, (sample shr 16).toShort())
        }
        return output
    }
}
