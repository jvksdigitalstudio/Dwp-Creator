package com.jvk.dwpcreator.sampler.engine.voice

import com.jvk.dwpcreator.sampler.api.math.FilterScaling
import com.jvk.dwpcreator.sampler.api.state.LoopMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Una voz del sampler: lee una muestra ([SamplePlayhead]), la filtra
 * ([SvfFilter]), la modula ([VoiceModulator]) y la mezcla en el bus estéreo
 * con su envolvente de amplitud ([Adsr]). Es quien **orquesta** esas piezas:
 * cada una resuelve un problema distinto y ninguna conoce a las demás.
 *
 * **Tasas de proceso.** La envolvente de amplitud, la lectura y el filtro
 * corren **por muestra**. Las modulaciones (envolventes de filtro/afinación,
 * LFO, glide, pitch bend), la afinación y los coeficientes del filtro se
 * recalculan cada [CONTROL_FRAMES] muestras (a ~3 kHz a 48 kHz) y la
 * velocidad de lectura y las ganancias se **interpolan linealmente** entre un
 * punto de control y el siguiente, sin escalones audibles. El contador de
 * control es de la voz, no del bloque: el resultado no depende de cómo el
 * motor trocee el audio.
 *
 * Sin asignaciones de memoria en [render]: se ejecuta en el hilo de audio.
 */
internal class SamplerVoice(private val sampleRate: Float) {

    private val amp = Adsr()
    private val modulator = VoiceModulator()
    private val playhead = SamplePlayhead()
    private val glide = Glide()
    private val filterLeft = SvfFilter()
    private val filterRight = SvfFilter()
    private val coefficients = SvfCoefficients()

    var active: Boolean = false
        private set

    var token: VoiceToken? = null
        private set

    /** Orden de llegada (para robar la más antigua). */
    var serial: Long = 0L
        private set

    var ageFrames: Long = 0L
        private set

    /** `true` desde que se pidió el final de la nota (release o apagado rápido). */
    val isReleasing: Boolean get() = amp.isFinishing

    /** Nivel actual de la envolvente de amplitud (para elegir qué voz reutilizar). */
    val ampLevel: Float get() = amp.value

    /** `true` si suena y su tecla sigue pulsada: candidata a continuar en legato. */
    val isHeld: Boolean get() = active && !amp.isFinishing && token?.released == false

    /** En modo one-shot la voz ignora el *note-off*. */
    var ignoresRelease: Boolean = false
        private set

    // --- nota ---
    private var key = -1
    private var rootKey = -1
    private var velocity = 100
    private var volume = 1f
    private var pan = 0f
    private var panLeft = 0f
    private var panRight = 0f
    private var sourceRatio = 1.0

    // --- estado de control interpolado ---
    private var controlRemaining = 0
    private var controlStarted = false
    private var filterActive = false
    private var bendSmoothed = 0f
    private var step = 1.0
    private var stepIncrement = 0.0
    private var gainLeft = 0f
    private var gainRight = 0f
    private var gainLeftIncrement = 0f
    private var gainRightIncrement = 0f

    /**
     * Arranca la voz con [note]. [glideOffset] son los semitonos de los que
     * parte la afinación (la nota anterior menos esta), 0 si no hay glide.
     */
    fun start(note: VoiceNote, settings: VoiceSettings, serial: Long, glideOffset: Float) {
        this.serial = serial
        active = true
        token = note.token
        ageFrames = 0L
        rootKey = note.rootKey
        bindNote(note)
        ignoresRelease = settings.loopMode == LoopMode.ONE_SHOT
        sourceRatio = note.sample.sampleRateHz.toDouble() / sampleRate

        playhead.start(note.sample, settings.startFraction, settings.reverse, settings.loopMode)
        amp.reset()
        amp.trigger()
        modulator.start(settings, serial.toInt())
        glide.begin(glideOffset, settings.glideFrames)
        filterLeft.reset()
        filterRight.reset()
        filterActive = false
        controlRemaining = 0
        controlStarted = false
    }

    /**
     * Legato: una nota nueva que solapa a esta la continúa **en la misma voz**,
     * con la misma muestra y sin reiniciar las envolventes. La afinación se
     * desliza (según el glide) desde donde estaba hasta la de la nota nueva.
     */
    fun retarget(note: VoiceNote, settings: VoiceSettings) {
        val before = noteSemitones(settings) + glide.offsetSemitones
        token?.finished = true
        token = note.token
        bindNote(note)
        glide.begin(before - noteSemitones(settings), settings.glideFrames)
    }

    private fun bindNote(note: VoiceNote) {
        key = note.key
        velocity = note.velocity.coerceIn(1, 127)
        volume = note.volume.coerceIn(0f, 1f)
        pan = note.pan.coerceIn(-1f, 1f)
        val angle = panAngle(pan)
        panLeft = cos(angle)
        panRight = sin(angle)
    }

    fun release() {
        amp.release()
        modulator.release()
        playhead.release()
    }

    /** Apagado rápido y lineal en [frames] muestras (robo de voz, cambio de instrumento). */
    fun kill(frames: Float) {
        amp.kill(frames)
    }

    /**
     * Suma [frames] muestras de esta voz a [outLeft]/[outRight]. [bend] es la
     * posición de la rueda de pitch bend (-1..1).
     */
    fun render(frames: Int, settings: VoiceSettings, bend: Float, outLeft: FloatArray, outRight: FloatArray) {
        val filterOn = !settings.filter.bypass
        if (filterOn != filterActive) engageFilter(filterOn, settings)
        val stereo = playhead.stereo
        var i = 0
        while (i < frames) {
            if (controlRemaining == 0) controlTick(settings, bend, filterOn)
            val end = i + min(controlRemaining, frames - i)
            val runLength = end - i
            while (i < end) {
                if (playhead.ended) {
                    finish()
                    return
                }
                val env = amp.next(settings.ampEnvelope)
                if (amp.isIdle || isSilentBody(env)) {
                    finish()
                    return
                }
                var left = playhead.read(0)
                var right = if (stereo) playhead.read(1) else left
                if (filterOn) {
                    left = filterLeft.process(left, coefficients)
                    right = if (stereo) filterRight.process(right, coefficients) else left
                }
                val level = env * playhead.tailGain()
                outLeft[i] += left * gainLeft * level
                outRight[i] += right * gainRight * level

                playhead.advance(step)
                step += stepIncrement
                gainLeft += gainLeftIncrement
                gainRight += gainRightIncrement
                i++
            }
            controlRemaining -= runLength
        }
        ageFrames += frames
    }

    /** Una nota con decay/sustain a cero se extingue sola: no hay que esperar al final de la muestra. */
    private fun isSilentBody(env: Float): Boolean =
        env < Adsr.SILENCE && (amp.stage == Adsr.Stage.DECAY || amp.stage == Adsr.Stage.SUSTAIN)

    private fun controlTick(settings: VoiceSettings, bend: Float, filterOn: Boolean) {
        glide.advance(CONTROL_FRAMES)
        modulator.update(CONTROL_FRAMES, settings)
        bendSmoothed = if (controlStarted) bendSmoothed + (bend - bendSmoothed) * BEND_SMOOTHING else bend

        val semitones = (settings.baseSemitones + noteSemitones(settings) + glide.offsetSemitones +
            modulator.pitchSemitones + bendSmoothed * settings.bendRangeSemitones)
            .coerceIn(-MAX_SEMITONES, MAX_SEMITONES)
        val stepTarget = sourceRatio * Math.pow(2.0, semitones / 12.0)

        val level = volume * (1f - settings.velocitySensitivity * (1f - velocity / 127f)) * modulator.level
        val left: Float
        val right: Float
        if (modulator.pan == 0f) {
            left = panLeft * level
            right = panRight * level
        } else {
            val angle = panAngle((pan + modulator.pan).coerceIn(-1f, 1f))
            left = cos(angle) * level
            right = sin(angle) * level
        }

        if (filterOn) updateFilter(settings)

        if (controlStarted) {
            stepIncrement = (stepTarget - step) / CONTROL_FRAMES
            gainLeftIncrement = (left - gainLeft) / CONTROL_FRAMES
            gainRightIncrement = (right - gainRight) / CONTROL_FRAMES
        } else {
            step = stepTarget
            gainLeft = left
            gainRight = right
            stepIncrement = 0.0
            gainLeftIncrement = 0f
            gainRightIncrement = 0f
            controlStarted = true
        }
        controlRemaining = CONTROL_FRAMES
    }

    /**
     * Enciende o apaga el filtro a mitad de nota. Al encenderlo se calculan los coeficientes y se
     * limpia el estado **antes** de la primera muestra filtrada: si no, hasta el siguiente punto de
     * control (16 muestras) el filtro tendría coeficientes sin inicializar y produciría silencio o un chasquido.
     */
    private fun engageFilter(on: Boolean, settings: VoiceSettings) {
        filterActive = on
        if (on) {
            updateFilter(settings)
            filterLeft.reset()
            filterRight.reset()
        }
    }

    private fun updateFilter(settings: VoiceSettings) {
        val f = settings.filter
        val keyOctaves = if (key >= 0) f.keyTrack * (key - KEY_REFERENCE) / 12f else 0f
        val velocityOctaves = f.velocityTrack * VELOCITY_FILTER_OCTAVES * (velocity / 127f - 0.5f)
        val octaves = (f.cutoffOctaves + modulator.cutoffOctaves + keyOctaves + velocityOctaves)
            .coerceIn(0f, FilterScaling.OCTAVES)
        val resonance = (f.resonance + modulator.resonance).coerceIn(0f, 1f)
        coefficients.update(f.mode, FilterScaling.hzFromOctaves(octaves), resonance, f.drive, sampleRate)
    }

    /** Transposición de la nota respecto a la tecla raíz de la muestra, en semitonos. */
    private fun noteSemitones(settings: VoiceSettings): Float =
        if (key < 0 || rootKey < 0) 0f else (key - rootKey) * settings.pitchTrack

    private fun finish() {
        active = false
        token?.finished = true
        token = null
        playhead.clear()
    }

    private fun panAngle(pan: Float): Float = (pan + 1f) * (PI / 4.0).toFloat()

    companion object {
        /** Muestras entre dos puntos de control de la modulación. */
        const val CONTROL_FRAMES = 16

        private const val BEND_SMOOTHING = 0.3f
        private const val MAX_SEMITONES = 96f
        private const val KEY_REFERENCE = 60
        private const val VELOCITY_FILTER_OCTAVES = 4f
    }
}
