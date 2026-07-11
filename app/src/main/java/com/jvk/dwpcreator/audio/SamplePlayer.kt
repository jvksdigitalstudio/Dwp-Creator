package com.jvk.dwpcreator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

import com.jvk.dwpcreator.domain.audio.PcmConverter
import com.jvk.dwpcreator.domain.audio.WavDecoder

/**
 * Native sample playback using Android's AudioTrack in MODE_STREAM,
 * always as 16-bit PCM.
 *
 * Two deliberate choices here, both learned the hard way:
 *
 * 1. MODE_STREAM, not MODE_STATIC: DirectWave samples run several seconds
 *    long (~3MB each), and allocating that as one static native buffer up
 *    front can fail silently on some devices -- Builder().build() returns
 *    without throwing, but the track never reaches STATE_INITIALIZED, and
 *    play() then throws IllegalStateException.
 *
 * 2. Always convert to 16-bit PCM before playback, even though the source
 *    files are 32-bit float: ENCODING_PCM_FLOAT is only unevenly supported
 *    across real device audio HALs -- on unsupported hardware it fails
 *    with NO error and NO sound at all, which is far worse than a crash.
 *    16-bit PCM is universally supported on every Android device.
 *
 * Polyphonic: every call to [play] gets its own AudioTrack and its own
 * writer thread, so overlapping notes never cut each other off.
 */
class SamplePlayer {

    private val activeTracks = mutableSetOf<AudioTrack>()

    /**
     * Decodes [wavBytes] and plays it immediately on a background thread.
     * [velocity] (1..127, MIDI convention) scales playback volume linearly.
     * Returns immediately; playback happens asynchronously.
     */
    fun play(wavBytes: ByteArray, velocity: Int = 127) {
        val decoded = try {
            WavDecoder.decode(wavBytes)
        } catch (e: Exception) {
            return
        }
        if (decoded.frameCount <= 0) return

        // Always play back as 16-bit PCM regardless of source format.
        val pcm16 = when (decoded.format) {
            WavDecoder.SampleFormat.PCM_16 -> decoded.pcmData
            WavDecoder.SampleFormat.PCM_FLOAT -> PcmConverter.floatToInt16(decoded.pcmData)
        }

        val channelConfig = if (decoded.channelCount == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(decoded.sampleRateHz, channelConfig, encoding)
        if (minBufferSize <= 0) return // This sample rate/channel combo isn't supported on this device.

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(decoded.sampleRateHz)
            .setChannelMask(channelConfig)
            .setEncoding(encoding)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val streamBufferBytes = minBufferSize * 2 // safety margin against underruns

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(streamBufferBytes)
                .build()
        } catch (e: Exception) {
            return // Device/format combination rejected by the platform; skip this voice.
        }

        // Defensive: Builder.build() can return without throwing yet still be
        // uninitialized on some OEM devices. Never call play() without this check.
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }

        track.setVolume(velocity.coerceIn(1, 127) / 127f)
        synchronized(activeTracks) { activeTracks.add(track) }

        try {
            track.play()
        } catch (e: Exception) {
            releaseTrack(track)
            return
        }

        val writerThread = Thread({
            try {
                var offset = 0
                while (offset < pcm16.size) {
                    val length = minOf(streamBufferBytes, pcm16.size - offset)
                    val written = track.write(pcm16, offset, length, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }
            } catch (ignored: Exception) {
                // Track may have been released/stopped concurrently (e.g. releaseAll()); nothing to do.
            } finally {
                releaseTrack(track)
            }
        }, "SamplePlayer-writer")
        writerThread.isDaemon = true
        writerThread.start()
    }

    private fun releaseTrack(track: AudioTrack) {
        synchronized(activeTracks) { activeTracks.remove(track) }
        try {
            track.stop()
        } catch (ignored: Exception) {
        }
        try {
            track.release()
        } catch (ignored: Exception) {
        }
    }

    /** Stops and releases every currently-playing voice. Call from the owner's cleanup (e.g. ViewModel.onCleared). */
    fun releaseAll() {
        synchronized(activeTracks) {
            activeTracks.forEach {
                try {
                    it.stop()
                } catch (ignored: Exception) {
                }
                try {
                    it.release()
                } catch (ignored: Exception) {
                }
            }
            activeTracks.clear()
        }
    }
}
