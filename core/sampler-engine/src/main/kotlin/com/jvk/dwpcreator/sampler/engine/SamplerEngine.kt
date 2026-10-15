package com.jvk.dwpcreator.sampler.engine

import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.sampler.engine.dsp.MasterFxChain
import com.jvk.dwpcreator.sampler.engine.sample.CachedSample
import com.jvk.dwpcreator.sampler.engine.voice.SamplerVoice
import com.jvk.dwpcreator.sampler.engine.voice.VoiceNote
import com.jvk.dwpcreator.sampler.engine.voice.VoiceSettings
import com.jvk.dwpcreator.sampler.engine.voice.VoiceToken
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.max

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
 * **Voces.** [POOL_SIZE] voces preasignadas ([SamplerVoice]; la síntesis de
 * cada una -- muestra, filtro, envolventes, LFO, glide -- vive en
 * `sampler.engine.voice`). Este motor sólo las **asigna y mezcla**: hasta
 * [MAX_POLYPHONY] pueden estar "vivas" (sin liberar); si entra una nota más,
 * la más antigua se apaga con un fundido rápido de [STEAL_RELEASE_MS] -- sin
 * corte brusco -- y la nueva usa una de las voces extra del pool. En modo mono
 * una nota nueva apaga a la anterior, salvo en **legato**, donde la continúa en
 * su misma voz. Los ajustes de voz ([SamplerFxState.voice]) se resuelven fuera
 * del hilo de audio ([VoiceSettings]) y se adoptan en el siguiente bloque.
 */
class SamplerEngine(val sampleRateHz: Int) {

    private sealed class Command {
        class NoteOn(val note: VoiceNote) : Command()

        object Reset : Command()
    }

    /** Estado del panel y sus ajustes de voz ya resueltos: se publican juntos para que el hilo de audio nunca vea uno sin el otro. */
    private class Snapshot(val state: SamplerFxState, val voice: VoiceSettings)

    private val sampleRate = sampleRateHz.toFloat()
    private val voices = Array(POOL_SIZE) { SamplerVoice(sampleRate) }
    private val commands = ConcurrentLinkedQueue<Command>()
    private val chain = MasterFxChain(sampleRate)

    private val bufferLeft = FloatArray(MAX_BLOCK_FRAMES)
    private val bufferRight = FloatArray(MAX_BLOCK_FRAMES)

    private val stealFrames = max(1f, sampleRate * STEAL_RELEASE_MS / 1000f)
    private val minHoldFrames = (sampleRate * MIN_HOLD_MS / 1000f).toLong()
    private val meterHalfLifeFrames = sampleRate * METER_HALF_LIFE_S

    private var serialCounter = 0L
    private var lastKey = -1
    private var appliedState: SamplerFxState? = null
    private var quietFrames = 0L
    private var meterLeft = 0f
    private var meterRight = 0f

    @Volatile
    private var snapshot = Snapshot(SamplerFxState(), VoiceSettings.from(SamplerFxState().voice, sampleRate))

    @Volatile
    private var bend = 0f

    /** Estado del panel (bus maestro y voz). Se puede asignar desde cualquier hilo; el hilo de audio lo adopta en el siguiente bloque. */
    var fxState: SamplerFxState
        get() = snapshot.state
        set(value) {
            snapshot = Snapshot(value, VoiceSettings.from(value.voice, sampleRate))
        }

    /** Rueda de pitch bend, -1..1 (el rango en semitonos es [com.jvk.dwpcreator.sampler.api.state.SampleState.bendRangeSemitones]). Cualquier hilo. */
    var pitchBend: Float
        get() = bend
        set(value) {
            bend = value.coerceIn(-1f, 1f)
        }

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

    /**
     * Pide tocar [sample]. [key] es la nota MIDI tocada y [rootKey] la tecla raíz
     * de la muestra (negativas = desconocidas): su diferencia transpone la
     * muestra, y [key] alimenta el seguimiento de teclado del filtro y el glide.
     */
    fun noteOn(
        token: VoiceToken,
        sample: CachedSample,
        velocity: Int,
        volume: Float,
        pan: Float,
        key: Int = -1,
        rootKey: Int = -1
    ) {
        commands.add(Command.NoteOn(VoiceNote(token, sample, velocity, volume, pan, key, rootKey)))
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

        val snap = snapshot
        if (snap.state !== appliedState) {
            chain.apply(snap.state)
            appliedState = snap.state
        }

        bufferLeft.fill(0f, 0, frames)
        bufferRight.fill(0f, 0, frames)

        val currentBend = bend
        var active = 0
        for (voice in voices) {
            if (!voice.active) continue
            val token = voice.token
            if (!voice.isReleasing && !voice.ignoresRelease && token != null && token.released &&
                voice.ageFrames >= minHoldFrames
            ) {
                voice.release()
            }
            voice.render(frames, snap.voice, currentBend, bufferLeft, bufferRight)
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
                is Command.NoteOn -> startVoice(command.note)
                is Command.Reset -> {
                    for (voice in voices) {
                        if (voice.active && !voice.isReleasing) voice.kill(stealFrames)
                    }
                    // La primera nota del instrumento siguiente no debe deslizarse desde la última del anterior.
                    lastKey = -1
                }
            }
        }
    }

    private fun startVoice(note: VoiceNote) {
        val sample = note.sample
        val totalFrames = sample.pcm16.size / (2 * sample.channelCount.coerceAtLeast(1))
        if (totalFrames <= 0 || sample.channelCount !in 1..2 || sample.sampleRateHz <= 0) {
            note.token.finished = true
            return
        }

        val snap = snapshot
        val settings = snap.voice
        val mono = snap.state.monophonic

        if (mono && settings.legato) {
            val held = findHeldVoice()
            if (held != null) {
                held.retarget(note, settings)
                rememberKey(note)
                return
            }
        }

        val overlapping = findHeldVoice() != null
        if (mono) {
            for (voice in voices) {
                if (voice.active && !voice.isReleasing) voice.kill(stealFrames)
            }
        } else {
            enforcePolyphonyLimit()
        }

        val slot = findFreeVoice()
        if (slot == null) {
            note.token.finished = true
            return
        }
        slot.start(note, settings, ++serialCounter, glideOffset(note, settings, overlapping))
        rememberKey(note)
    }

    private fun rememberKey(note: VoiceNote) {
        if (note.key >= 0) lastKey = note.key
    }

    /**
     * Semitonos de los que parte una nota nueva con glide: la distancia a la
     * tecla anterior. Con "sólo legato" sólo se desliza si la anterior aún suena pulsada.
     */
    private fun glideOffset(note: VoiceNote, settings: VoiceSettings, overlapping: Boolean): Float {
        if (settings.glideFrames <= 0f || lastKey < 0 || note.key < 0) return 0f
        if (settings.glideOnlyLegato && !overlapping) return 0f
        return (lastKey - note.key) * settings.pitchTrack
    }

    /** Una voz que suena con su tecla aún pulsada (la más reciente), o `null`. */
    private fun findHeldVoice(): SamplerVoice? {
        var newest: SamplerVoice? = null
        for (voice in voices) {
            if (voice.isHeld && (newest == null || voice.serial > newest.serial)) newest = voice
        }
        return newest
    }

    /** Si ya hay [MAX_POLYPHONY] voces vivas, apaga (con fundido rápido) la más antigua. */
    private fun enforcePolyphonyLimit() {
        var alive = 0
        var oldest: SamplerVoice? = null
        for (voice in voices) {
            if (voice.active && !voice.isReleasing) {
                alive++
                if (oldest == null || voice.serial < oldest.serial) oldest = voice
            }
        }
        if (alive >= MAX_POLYPHONY && oldest != null) oldest.kill(stealFrames)
    }

    /** Voz libre; si el pool entero está ocupado, se reutiliza la que ya se está apagando y suena más baja. */
    private fun findFreeVoice(): SamplerVoice? {
        for (voice in voices) if (!voice.active) return voice
        var candidate: SamplerVoice? = null
        for (voice in voices) {
            if (voice.isReleasing && (candidate == null || voice.ampLevel < candidate.ampLevel)) candidate = voice
        }
        candidate?.token?.finished = true
        return candidate
    }

    companion object {
        /** Voces "vivas" simultáneas máximas antes de empezar a apagar la más antigua. */
        const val MAX_POLYPHONY = 32

        /** Voces preasignadas: las vivas más unas pocas extra para las que se están apagando. */
        const val POOL_SIZE = 40

        /** Tamaño máximo de trozo interno de [render]. */
        const val MAX_BLOCK_FRAMES = 512

        const val STEAL_RELEASE_MS = 6f

        /** Una nota dura como mínimo esto aunque la tecla se suelte antes: evita un "tic" mudo en toques ultrarrápidos. */
        const val MIN_HOLD_MS = 15f

        private const val METER_HALF_LIFE_S = 0.25f
        private const val SILENCE_THRESHOLD = 1e-5f
    }
}
