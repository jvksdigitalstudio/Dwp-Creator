package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.codec.wav.SampleLoop
import com.jvk.dwpcreator.sampler.api.state.LoopMode
import com.jvk.dwpcreator.sampler.engine.sample.CachedSample
import kotlin.math.max

/**
 * Cursor de reproducción de una muestra: **dónde** está la lectura y **qué
 * valor interpolado** hay ahí. Resuelve la dirección (normal, reverse,
 * ping-pong), los modos de bucle ([LoopMode]) y el final de la muestra; no
 * sabe nada de envolventes ni de filtros.
 *
 * Lectura por interpolación cúbica de Hermite (Catmull-Rom). Dentro de un
 * bucle hacia delante, los vecinos que caen más allá del final del bucle se
 * toman del inicio (`wrap`), así la interpolación **no** introduce un salto en
 * la costura del bucle.
 */
internal class SamplePlayhead {
    private var pcm: ByteArray = EMPTY_PCM
    private var channels = 1
    private var totalFrames = 0
    private var endFade = 1f

    private var loopStart = 0
    private var loopEnd = 0
    private var mode = LoopMode.OFF
    private var hasLoop = false
    private var released = false

    private var position = 0.0
    private var direction = 1

    /** `true` cuando la lectura salió de la muestra (no hay más audio que dar). */
    var ended: Boolean = false
        private set

    val stereo: Boolean get() = channels == 2

    /** Posición de lectura en frames de la muestra de origen (diagnóstico y pruebas). */
    val framePosition: Double get() = position

    /**
     * Arranca la lectura de [sample].
     *
     * @param startFraction punto de inicio, 0..1 de la duración.
     * @param reverse pide reproducir al revés; se ignora si el bucle va a estar activo.
     */
    fun start(sample: CachedSample, startFraction: Float, reverse: Boolean, loopMode: LoopMode) {
        pcm = sample.pcm16
        channels = sample.channelCount
        totalFrames = pcm.size / (2 * channels)
        endFade = max(1f, sample.sampleRateHz * END_FADE_MS / 1000f)
        mode = loopMode
        released = false
        ended = false

        val loop: SampleLoop? = sample.loop
        hasLoop = loop != null && loopMode.loops &&
            loop.startFrame in 0 until loop.endFrame && loop.endFrame <= totalFrames &&
            loop.endFrame - loop.startFrame >= MIN_LOOP_FRAMES
        loopStart = if (hasLoop) loop!!.startFrame else 0
        loopEnd = if (hasLoop) loop!!.endFrame else 0

        val backwards = reverse && !hasLoop
        direction = if (backwards) -1 else 1
        val offset = startFraction.coerceIn(0f, 1f).toDouble() * totalFrames
        position = if (backwards) (totalFrames - 1 - offset).coerceAtLeast(0.0) else offset.coerceAtMost(totalFrames - 1.0)
    }

    /** Suelta la nota: en modo [LoopMode.SUSTAIN] deja de repetir el bucle y continúa hacia el final. */
    fun release() {
        released = true
    }

    /** `true` si ahora mismo se está repitiendo una región de bucle. */
    val looping: Boolean
        get() = hasLoop && (mode != LoopMode.SUSTAIN || !released)

    /** Avanza [step] frames de origen en la dirección actual y resuelve bucle, rebote y final. */
    fun advance(step: Double) {
        position += step * direction
        if (looping) {
            if (mode == LoopMode.BOUNCE) bounce() else wrapForward()
        }
        if (direction > 0 && position >= totalFrames) ended = true
        if (direction < 0 && position < 0.0) ended = true
    }

    private fun wrapForward() {
        if (position >= loopEnd) {
            val length = (loopEnd - loopStart).toDouble()
            position = loopStart + (position - loopEnd) % length
        }
    }

    private fun bounce() {
        if (direction > 0 && position >= loopEnd) {
            position = 2.0 * loopEnd - position
            direction = -1
        } else if (direction < 0 && position <= loopStart) {
            position = 2.0 * loopStart - position
            direction = 1
        }
    }

    /** Ganancia de fundido en el extremo de la muestra (anti-clic); 1 mientras haya bucle activo. */
    fun tailGain(): Float {
        if (looping) return 1f
        val remaining = if (direction > 0) totalFrames - position else position + 1.0
        return if (remaining < endFade) (remaining / endFade).toFloat().coerceAtLeast(0f) else 1f
    }

    /** Valor interpolado (-1..1) del [channel] en la posición actual; en una muestra mono, el canal 1 devuelve el 0. */
    fun read(channel: Int): Float {
        val base = position.toInt()
        val frac = (position - base).toFloat()
        val c = if (channel < channels) channel else 0
        val wrap = looping && mode != LoopMode.BOUNCE
        val ym1 = frameValue(base - 1, c, wrap)
        val y0 = frameValue(base, c, wrap)
        val y1 = frameValue(base + 1, c, wrap)
        val y2 = frameValue(base + 2, c, wrap)
        val c1 = 0.5f * (y1 - ym1)
        val c2 = ym1 - 2.5f * y0 + 2f * y1 - 0.5f * y2
        val c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1)
        return ((c3 * frac + c2) * frac + c1) * frac + y0
    }

    private fun frameValue(frame: Int, channel: Int, wrap: Boolean): Float {
        var f = frame
        if (wrap && f >= loopEnd) {
            f = loopStart + (f - loopEnd) % (loopEnd - loopStart)
        }
        f = if (f < 0) 0 else if (f >= totalFrames) totalFrames - 1 else f
        val offset = (f * channels + channel) * 2
        val value = (pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xFF)
        return value * INT16_TO_FLOAT
    }

    fun clear() {
        pcm = EMPTY_PCM
        totalFrames = 0
        ended = true
    }

    private companion object {
        const val END_FADE_MS = 2f
        const val MIN_LOOP_FRAMES = 2
        const val INT16_TO_FLOAT = 1f / 32768f
        val EMPTY_PCM = ByteArray(0)
    }
}
