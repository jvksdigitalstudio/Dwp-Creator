package com.jvk.dwpcreator.session.testing

import com.jvk.dwpcreator.sampler.api.AudioStats
import com.jvk.dwpcreator.sampler.api.SamplerPlayback
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState

/** Doble de [SamplerPlayback] que registra cada llamada para poder comprobarla. */
class FakePlayback : SamplerPlayback {

    data class Play(
        val index: Int,
        val velocity: Int,
        val volume: Float,
        val pan: Float,
        val key: Int,
        val rootKey: Int
    )

    val plays = mutableListOf<Play>()
    val stops = mutableListOf<Int>()
    val fxStates = mutableListOf<SamplerFxState>()
    var pitchBend = 0f
        private set
    var resets = 0
        private set
    var releases = 0
        private set

    override fun prewarm(audioByIndex: List<ByteArray>) = Unit

    override fun play(index: Int, wavBytes: ByteArray, velocity: Int, volume: Float, pan: Float, key: Int, rootKey: Int) {
        plays += Play(index, velocity, volume, pan, key, rootKey)
    }

    override fun stopNote(index: Int) {
        stops += index
    }

    override fun setPitchBend(value: Float) {
        pitchBend = value
    }

    override fun setFxState(state: SamplerFxState) {
        fxStates += state
    }

    override fun outputPeaks(): Pair<Float, Float> = Pair(0f, 0f)

    override fun audioStats(): AudioStats = AudioStats()

    override fun resetForNewProject() {
        resets++
    }

    override fun releaseAll() {
        releases++
    }
}
