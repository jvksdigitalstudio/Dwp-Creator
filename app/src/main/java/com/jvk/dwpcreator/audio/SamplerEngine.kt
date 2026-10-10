package com.jvk.dwpcreator.audio

import com.jvk.dwpcreator.audio.dsp.MasterFxChain
import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache.CachedSample
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Motor de mezcla del sampler: **un único bus estéreo** al que contribuyen
 * todas las voces, seguido de la cadena de efectos maestra
 * ([MasterFxChain]). Kotlin/JVM puro (sin Android): [render] produce el audio
 * en un `FloatArray`, así que todo el motor se prueba en JVM; el hilo de
 * audio real vive en [AudioOutputDriver].
 *
 * **Por qué existe (reemplaza a "un `AudioTrack` por nota").** Reverb, delay y
 * chorus actúan sobre la *suma* de lo que suena; con una pista de hardware
 * independiente por voz no hay dónde insertarlos. Aquí todas las voces se
 * mezclan en coma flotante y el resultado pasa por la cadena de efectos una
 * sola vez, igual que el bus de programa de DirectWave.
 *
 * **Hilos.** Cualquier hilo puede llamar a [noteOn], [resetAllVoices] y
 * asignar [fxState]; todo entra al hilo de audio por una cola sin bloqueos
 * ([ConcurrentLinkedQueue]) o una referencia `@Volatile`. El hilo de audio no
 * toma locks ni reserva memoria. La liberación de una nota **no** pasa por la
 * cola: cada voz mira la bandera [VoiceToken.released] de su token al inicio
 * de cada bloque (ver KDoc de [VoiceToken]).
 *
 * **Voces.** [POOL_SIZE] voces preasignadas. Hasta [MAX_POLYPHONY] pueden estar
 * "vivas" (sin liberar); si entra una nota más, la más antigua se libera con
 * un fundido rápido de [STEAL_RELEASE_MS] -- sin corte brusco -- y la nueva usa
 * una de las voces extra del pool. Reproduce cada muestra a su altura original
 * (una muestra por tecla); la conversión de frecuencia de muestreo del archivo
 * a la del dispositivo usa interpolación cúbica (Catmull-Rom / Hermite).
 *
 * **Anti-clic.** Ataque lineal de [ATTACK_MS], liberación lineal de
 * [RELEASE_MS] y un fundido de [END_FADE_MS] sobre el final natural de la
 * muestra; todo por muestra (sample-accurate), no por bloque.
 */
class SamplerEngine(val sampleRateHz: Int) {

    /**
     * Identidad de una nota, creada por quien la dispara. [released] se
     * escribe desde cualquier hilo para pedir la liberación (tecla soltada);
     * el hilo de audio la lee en cada bloque. [finished] lo escribe el hilo de
     * audio cuando la voz terminó o se descartó, para que quien lleva la
     * contabilidad de notas pueda olvidarla. Dos `@Volatile` en vez de un
     * mensaje por la cola: así soltar una tecla **antes** de que su
     * `noteOn` haya llegado al motor (muestra aún decodificándose) sigue
     * funcionando, sin condiciones de carrera ni notas pegadas.
     */
    class VoiceToken {
        @Volatile
        var released: Boolean = false

        @Volatile
        var finished: Boolean = false
    }

    private sealed class Command {
        class NoteOn(
            val token: VoiceToken,
            val sample: CachedSample,
            val velocity: Int,
            val volume: Float,
            val pan: Float
        ) : Command()

        object Reset : Command()
    }

    private class Voice {
        var active = false
        var releasing = false
        var token: VoiceToken? = null
        var pcm: ByteArray = EMPTY_PCM
        var channels = 1
        var totalFrames = 0
        var position = 0.0
        var step = 1.0
        var gainLeft = 0f
        var gainRight = 0f
        var env = 0f
        var releaseStep = 0f
        var ageFrames = 0L
        var endFadeFrames = 1f
        var serial = 0L
    }

    private val sampleRate = sampleRateHz.toFloat()
    private val voices = Array(POOL_SIZE) { Voice() }
    private val commands = ConcurrentLinkedQueue<Command>()
    private val chain = MasterFxChain(sampleRate)

    private val bufferLeft = FloatArray(MAX_BLOCK_FRAMES)
    private val bufferRight = FloatArray(MAX_BLOCK_FRAMES)

    private val attackStep = 1f / max(1f, sampleRate * ATTACK_MS / 1000f)
    private val releaseFrames = max(1f, sampleRate * RELEASE_MS / 1000f)
    private val stealFrames = max(1f, sampleRate * STEAL_RELEASE_MS / 1000f)
    private val minHoldFrames = (sampleRate * MIN_HOLD_MS / 1000f).toLong()
    private val meterHalfLifeFrames = sampleRate * METER_HALF_LIFE_S

    private var serialCounter = 0L
    private var appliedState: SamplerFxState? = null
    private var quietFrames = 0L
    private var meterLeft = 0f
    private var meterRight = 0f

    /** Estado del bus maestro. Se puede asignar desde cualquier hilo; el hilo de audio lo adopta en el siguiente bloque. */
    @Volatile
    var fxState: SamplerFxState = SamplerFxState()

    /** Picos recientes de salida (0..1+), con caída suave: para el vúmetro de la UI. */
    @Volatile
    var peakLeft: Float = 0f
        private set

    @Volatile
    var peakRight: Float = 0f
        private set

    /** Voces sonando ahora mismo (diagnóstico y pruebas). */
    @Volatile
    var activeVoiceCount: Int = 0
        private set

    /** Segundos consecutivos de silencio total (sin voces y con las colas de efectos extinguidas). */
    @Volatile
    var quietSeconds: Float = 0f
        private set

    fun noteOn(token: VoiceToken, sample: CachedSample, velocity: Int, volume: Float, pan: Float) {
        commands.add(Command.NoteOn(token, sample, velocity, volume, pan))
    }

    /** `true` si hay órdenes encoladas que el hilo de audio aún no procesó (lo usa [AudioOutputDriver] para no dormirse con una nota en vuelo). */
    fun hasPendingCommands(): Boolean = !commands.isEmpty()

    /** Silencia todas las voces con un fundido corto (carga de un instrumento nuevo). */
    fun resetAllVoices() {
        commands.add(Command.Reset)
    }

    /**
     * Genera [frames] frames estéreo entrelazados (L,R,L,R...) en [out]
     * (`out.size >= frames * 2`). Seguro para llamar con cualquier tamaño de
     * bloque: internamente se procesa en trozos de [MAX_BLOCK_FRAMES].
     */
    fun render(out: FloatArray, frames: Int) {
        require(out.size >= frames * 2) { "out demasiado pequeño para $frames frames estéreo" }
        var done = 0
        while (done < frames) {
            val n = minOf(MAX_BLOCK_FRAMES, frames - done)
            renderChunk(out, done, n)
            done += n
        }
    }

    private fun renderChunk(out: FloatArray, outFrameOffset: Int, frames: Int) {
        drainCommands()

        val state = fxState
        if (state !== appliedState) {
            chain.apply(state)
            appliedState = state
        }

        bufferLeft.fill(0f, 0, frames)
        bufferRight.fill(0f, 0, frames)

        var active = 0
        for (voice in voices) {
            if (!voice.active) continue
            val token = voice.token
            if (!voice.releasing && token != null && token.released && voice.ageFrames >= minHoldFrames) {
                beginRelease(voice, releaseFrames)
            }
            renderVoice(voice, frames)
            if (voice.active) active++
        }
        activeVoiceCount = active

        chain.process(bufferLeft, bufferRight, frames)

        var peakL = 0f
        var peakR = 0f
        var o = outFrameOffset * 2
        for (i in 0 until frames) {
            val l = bufferLeft[i]
            val r = bufferRight[i]
            out[o++] = l
            out[o++] = r
            val al = abs(l)
            val ar = abs(r)
            if (al > peakL) peakL = al
            if (ar > peakR) peakR = ar
        }

        // Caída exponencial con semivida fija en tiempo real, independiente del tamaño del trozo.
        val meterDecay = Math.pow(0.5, frames / meterHalfLifeFrames.toDouble()).toFloat()
        meterLeft = max(peakL, meterLeft * meterDecay)
        meterRight = max(peakR, meterRight * meterDecay)
        peakLeft = meterLeft
        peakRight = meterRight

        if (active == 0 && peakL < SILENCE_THRESHOLD && peakR < SILENCE_THRESHOLD) {
            quietFrames += frames
        } else {
            quietFrames = 0
        }
        quietSeconds = quietFrames / sampleRate
    }

    private fun drainCommands() {
        while (true) {
            when (val command = commands.poll() ?: return) {
                is Command.NoteOn -> startVoice(command)
                is Command.Reset -> {
                    for (voice in voices) {
                        if (voice.active && !voice.releasing) beginRelease(voice, stealFrames)
                    }
                }
            }
        }
    }

    private fun startVoice(command: Command.NoteOn) {
        val sample = command.sample
        val totalFrames = sample.pcm16.size / (2 * sample.channelCount.coerceAtLeast(1))
        if (totalFrames <= 0 || sample.channelCount !in 1..2 || sample.sampleRateHz <= 0) {
            command.token.finished = true
            return
        }

        if (fxState.monophonic) {
            for (voice in voices) {
                if (voice.active && !voice.releasing) beginRelease(voice, stealFrames)
            }
        } else {
            enforcePolyphonyLimit()
        }

        val slot = findFreeVoice()
        if (slot == null) {
            command.token.finished = true
            return
        }

        val panAngle = (command.pan.coerceIn(-1f, 1f) + 1f) * (PI / 4.0).toFloat()
        val levelGain = command.volume.coerceIn(0f, 1f) * (command.velocity.coerceIn(1, 127) / 127f)

        slot.active = true
        slot.releasing = false
        slot.token = command.token
        slot.pcm = sample.pcm16
        slot.channels = sample.channelCount
        slot.totalFrames = totalFrames
        slot.position = 0.0
        slot.step = sample.sampleRateHz.toDouble() / sampleRateHz
        // Paneo de potencia constante (ley estándar de mesa de mezcla): centro = 0.707 por canal.
        slot.gainLeft = cos(panAngle) * levelGain
        slot.gainRight = sin(panAngle) * levelGain
        slot.env = 0f
        slot.releaseStep = 0f
        slot.ageFrames = 0
        slot.endFadeFrames = max(1f, sample.sampleRateHz * END_FADE_MS / 1000f)
        slot.serial = ++serialCounter
    }

    /** Si ya hay [MAX_POLYPHONY] voces vivas, libera (con fundido rápido) la más antigua. */
    private fun enforcePolyphonyLimit() {
        var alive = 0
        var oldest: Voice? = null
        for (voice in voices) {
            if (voice.active && !voice.releasing) {
                alive++
                if (oldest == null || voice.serial < oldest.serial) oldest = voice
            }
        }
        if (alive >= MAX_POLYPHONY && oldest != null) beginRelease(oldest, stealFrames)
    }

    /** Voz libre; si el pool entero está ocupado, se reutiliza la que ya se está apagando y suena más baja. */
    private fun findFreeVoice(): Voice? {
        for (voice in voices) if (!voice.active) return voice
        var candidate: Voice? = null
        for (voice in voices) {
            if (voice.releasing && (candidate == null || voice.env < candidate.env)) candidate = voice
        }
        candidate?.let { it.token?.finished = true }
        return candidate
    }

    private fun beginRelease(voice: Voice, overFrames: Float) {
        voice.releasing = true
        voice.releaseStep = voice.env / overFrames
        if (voice.releaseStep <= 0f) voice.releaseStep = 1f / overFrames
    }

    private fun renderVoice(voice: Voice, frames: Int) {
        val pcm = voice.pcm
        val channels = voice.channels
        val total = voice.totalFrames
        val last = total - 1
        var position = voice.position
        val step = voice.step
        var env = voice.env
        val gainL = voice.gainLeft
        val gainR = voice.gainRight
        val stereo = channels == 2
        val endFade = voice.endFadeFrames // fundido final, en frames de la MUESTRA de origen

        var i = 0
        while (i < frames) {
            val base = position.toInt()
            if (base >= total) {
                finishVoice(voice)
                break
            }
            if (voice.releasing) {
                env -= voice.releaseStep
                if (env <= 0f) {
                    finishVoice(voice)
                    break
                }
            } else if (env < 1f) {
                env += attackStep
                if (env > 1f) env = 1f
            }

            val frac = (position - base).toFloat()
            val remaining = (total - position).toFloat()
            val tail = if (remaining < endFade) remaining / endFade else 1f
            val amp = env * tail

            val left = cubic(pcm, base, frac, 0, channels, last)
            bufferLeft[i] += left * gainL * amp
            val right = if (stereo) cubic(pcm, base, frac, 1, channels, last) else left
            bufferRight[i] += right * gainR * amp

            position += step
            i++
        }
        if (voice.active) {
            voice.position = position
            voice.env = env
            voice.ageFrames += frames
        }
    }

    private fun finishVoice(voice: Voice) {
        voice.active = false
        voice.releasing = false
        voice.token?.finished = true
        voice.token = null
        voice.pcm = EMPTY_PCM
    }

    /** Interpolación cúbica de Hermite (Catmull-Rom) de 4 puntos sobre PCM de 16 bits; devuelve -1..1. */
    private fun cubic(pcm: ByteArray, base: Int, frac: Float, channel: Int, channels: Int, last: Int): Float {
        val ym1 = sampleAt(pcm, base - 1, channel, channels, last)
        val y0 = sampleAt(pcm, base, channel, channels, last)
        val y1 = sampleAt(pcm, base + 1, channel, channels, last)
        val y2 = sampleAt(pcm, base + 2, channel, channels, last)
        val c1 = 0.5f * (y1 - ym1)
        val c2 = ym1 - 2.5f * y0 + 2f * y1 - 0.5f * y2
        val c3 = 0.5f * (y2 - ym1) + 1.5f * (y0 - y1)
        return ((c3 * frac + c2) * frac + c1) * frac + y0
    }

    private fun sampleAt(pcm: ByteArray, frame: Int, channel: Int, channels: Int, last: Int): Float {
        val f = if (frame < 0) 0 else if (frame > last) last else frame
        val offset = (f * channels + channel) * 2
        val value = (pcm[offset + 1].toInt() shl 8) or (pcm[offset].toInt() and 0xFF)
        return value * INT16_TO_FLOAT
    }

    companion object {
        /** Voces "vivas" simultáneas máximas antes de empezar a apagar la más antigua. */
        const val MAX_POLYPHONY = 32

        /** Voces preasignadas: las vivas más unas pocas extra para las que se están apagando. */
        const val POOL_SIZE = 40

        /** Tamaño máximo de trozo interno de [render]. */
        const val MAX_BLOCK_FRAMES = 512

        const val ATTACK_MS = 3f
        const val RELEASE_MS = 15f
        const val STEAL_RELEASE_MS = 6f
        const val END_FADE_MS = 2f

        /** Una nota dura como mínimo esto aunque la tecla se suelte antes: evita un "tic" mudo en toques ultrarrápidos. */
        const val MIN_HOLD_MS = 15f

        private const val METER_HALF_LIFE_S = 0.25f
        private const val SILENCE_THRESHOLD = 1e-5f
        private const val INT16_TO_FLOAT = 1f / 32768f
        private val EMPTY_PCM = ByteArray(0)
    }
}
