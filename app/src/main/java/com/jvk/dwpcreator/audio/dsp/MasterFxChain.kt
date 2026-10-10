package com.jvk.dwpcreator.audio.dsp

/**
 * Cadena de efectos del bus maestro, en el orden de señal:
 *
 * `voces -> DRIVE A -> CHORUS -> DELAY -> REVERB -> DRIVE B -> VOLUME -> limitador`
 *
 * Drive A satura la señal "seca" antes de que los efectos *send* la
 * procesen; Drive B actúa sobre el resultado completo (efectos incluidos) y
 * le da el color final. El volumen maestro se suaviza (20 ms) y va antes del
 * limitador, que es siempre el último eslabón.
 *
 * No asigna memoria al procesar. Todo se ejecuta en el hilo de audio;
 * [apply] se llama desde ese mismo hilo, al inicio de un bloque, cuando el
 * estado publicado cambió.
 */
internal class MasterFxChain(sampleRate: Float) {

    private val driveA = DriveStage(sampleRate, asymmetric = false)
    private val chorus = ChorusEffect(sampleRate)
    private val delay = StereoDelayEffect(sampleRate)
    private val reverb = FdnReverbEffect(sampleRate)
    private val driveB = DriveStage(sampleRate, asymmetric = true)
    private val limiter = PeakLimiter(sampleRate)

    private val volumeSmoothing = Dsp.smoothingCoef(20f, sampleRate)
    private var volumeTarget = 1f
    private var volume = 1f

    fun apply(state: SamplerFxState) {
        driveA.setAmount(if (state.driveAEnabled) state.driveAAmount else 0f)
        driveA.setToneHz(state.driveAToneHz)
        driveB.setAmount(if (state.driveBEnabled) state.driveBAmount else 0f)
        driveB.setToneHz(state.driveBToneHz)
        chorus.setParams(
            state.chorusEnabled, state.chorusDelayMs, state.chorusDepthMs,
            state.chorusRateHz, state.chorusFeedback, state.chorusMix, state.chorusWidth
        )
        delay.setParams(
            state.delayEnabled, DelaySync.effectiveTimeMs(state), state.delayFeedback,
            state.delayLowCutHz, state.delayHighCutHz, state.delayBounce, state.delayMix,
            state.delayTape, state.delayWow
        )
        reverb.setParams(
            state.reverbEnabled, state.reverbRoom, state.reverbDampHz,
            state.reverbDiffusion, state.reverbDecaySec, state.reverbMix,
            state.reverbPreDelayMs, state.reverbModulation, state.reverbWidth
        )
        volumeTarget = if (state.masterVolumeDb <= FxParam.MASTER_VOLUME.min) 0f
        else Dsp.dbToLinear(state.masterVolumeDb)
    }

    fun process(left: FloatArray, right: FloatArray, frames: Int) {
        driveA.process(left, right, frames)
        chorus.process(left, right, frames)
        delay.process(left, right, frames)
        reverb.process(left, right, frames)
        driveB.process(left, right, frames)
        for (i in 0 until frames) {
            volume += (volumeTarget - volume) * volumeSmoothing
            left[i] *= volume
            right[i] *= volume
        }
        limiter.process(left, right, frames)
    }
}
