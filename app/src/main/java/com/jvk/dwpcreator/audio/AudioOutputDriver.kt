package com.jvk.dwpcreator.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.Semaphore

/**
 * Salida de audio real del [SamplerEngine]: **un único** `AudioTrack` en coma
 * flotante estéreo (`ENCODING_PCM_FLOAT`) con `PERFORMANCE_MODE_LOW_LATENCY`,
 * alimentado por un hilo dedicado con prioridad de audio que llama a
 * [SamplerEngine.render] en bloques del tamaño de ráfaga nativa del
 * dispositivo.
 *
 * - **Frecuencia y ráfaga nativas.** Se pide al sistema su frecuencia de
 *   salida y su tamaño de ráfaga ([nativeSampleRate], `PROPERTY_OUTPUT_*`):
 *   abrir el stream a esa frecuencia evita el remuestreo del mezclador del
 *   sistema, que es el camino más corto (y la condición para obtener la ruta
 *   rápida).
 * - **Búfer adaptativo.** Empieza en 2 ráfagas (mínima latencia estable). Si el
 *   contador de *underruns* del `AudioTrack` sube, el búfer crece una ráfaga
 *   (hasta [MAX_BURSTS]): un dispositivo modesto pasa a un búfer holgado y
 *   deja de "crujir" en vez de fallar.
 * - **Recuperación.** Si el `AudioTrack` muere (cambio de ruta, auriculares,
 *   HAL reiniciado: `ERROR_DEAD_OBJECT`), se reconstruye sin parar el hilo.
 * - **Suspensión por inactividad.** Tras [IDLE_SUSPEND_SECONDS] de silencio
 *   total ([SamplerEngine.quietSeconds]: sin voces y con las colas de
 *   efectos extinguidas) el stream se pausa y el hilo duerme -- cero CPU y
 *   batería -- hasta que [wake] lo despierta con la siguiente nota.
 */
class AudioOutputDriver(
    private val engine: SamplerEngine,
    private val burstFrames: Int
) {

    /** Permisos = peticiones de despertar pendientes. El hilo de audio se bloquea aquí mientras está suspendido. */
    private val wakeSignal = Semaphore(0)

    @Volatile
    private var running = false

    @Volatile
    private var suspended = false

    private var thread: Thread? = null

    // Métricas publicadas por el hilo de audio, leídas por la UI (ver AudioStats).
    @Volatile
    private var dspLoadPercent = 0f

    @Volatile
    private var worstBlockMs = 0f

    @Volatile
    private var bufferMs = 0f

    @Volatile
    private var underrunTotal = 0

    private val blockMs = burstFrames * 1000f / engine.sampleRateHz

    /** Métricas actuales de carga DSP, latencia de búfer y *underruns*. */
    fun stats(): AudioStats = AudioStats(dspLoadPercent, worstBlockMs, blockMs, bufferMs, underrunTotal)

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "SamplerEngine-audio").apply {
            isDaemon = true
            start()
        }
    }

    /** Despierta el stream si estaba suspendido por inactividad. Barato de llamar en cada nota. */
    fun wake() {
        if (suspended) wakeSignal.release()
    }

    @Synchronized
    fun stop() {
        running = false
        wakeSignal.release()
        thread?.let {
            try {
                it.join(STOP_JOIN_TIMEOUT_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
    }

    private fun runLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo elevar la prioridad del hilo de audio", e)
        }

        val block = FloatArray(burstFrames * CHANNELS)
        var track = buildTrack()
        var bursts = INITIAL_BURSTS
        var lastUnderruns = 0
        var blocksSinceCheck = 0

        val blockPeriodNanos = burstFrames * 1_000_000_000.0 / engine.sampleRateHz
        var loadAverage = 0.0
        var windowWorstNanos = 0L
        var windowBlocks = 0
        bufferMs = burstFrames * bursts * 1000f / engine.sampleRateHz

        try {
            while (running) {
                val current = track
                if (current == null) {
                    // Sin stream utilizable: reintentar con espera, sin quemar CPU.
                    sleepQuietly(RETRY_DELAY_MS)
                    track = buildTrack()
                    // Stream nuevo: su búfer y su contador de underruns empiezan de cero.
                    bursts = INITIAL_BURSTS
                    lastUnderruns = 0
                    bufferMs = burstFrames * bursts * 1000f / engine.sampleRateHz
                    continue
                }

                val renderStart = System.nanoTime()
                engine.render(block, burstFrames)
                val renderNanos = System.nanoTime() - renderStart
                loadAverage += (renderNanos / blockPeriodNanos - loadAverage) * LOAD_AVERAGE_ALPHA
                dspLoadPercent = (loadAverage * 100.0).toFloat()
                if (renderNanos > windowWorstNanos) windowWorstNanos = renderNanos
                if (++windowBlocks >= STATS_WINDOW_BLOCKS) {
                    worstBlockMs = windowWorstNanos / 1_000_000f
                    windowWorstNanos = 0L
                    windowBlocks = 0
                }
                val written = current.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack.write devolvió $written; reconstruyendo el stream")
                    releaseQuietly(current)
                    track = null
                    continue
                }

                if (++blocksSinceCheck >= UNDERRUN_CHECK_BLOCKS) {
                    blocksSinceCheck = 0
                    val underruns = current.underrunCount
                    if (underruns > lastUnderruns) {
                        underrunTotal += underruns - lastUnderruns
                        Log.w(TAG, "Underrun(s): total=$underrunTotal, carga DSP=${"%.1f".format(dspLoadPercent)}%, búfer=${bufferMs}ms")
                        if (bursts < MAX_BURSTS) {
                            bursts++
                            current.setBufferSizeInFrames(burstFrames * bursts)
                            bufferMs = burstFrames * bursts * 1000f / engine.sampleRateHz
                        }
                    }
                    lastUnderruns = underruns
                }

                if (engine.quietSeconds >= IDLE_SUSPEND_SECONDS) {
                    suspendUntilWoken(current)
                }
            }
        } finally {
            track?.let { releaseQuietly(it) }
        }
    }

    /**
     * Pausa el stream y duerme hasta [wake]. **Orden deliberado** (patrón de
     * "publicar la bandera y luego comprobar la cola"): primero se publica
     * `suspended = true` y sólo después se mira si el motor tiene una nota
     * pendiente. Una nota que llegue en cualquier momento cae en uno de dos
     * casos -- o su [wake] ya ve `suspended = true` y libera el semáforo, o su
     * `noteOn` ya estaba en la cola y la comprobación de aquí lo detecta --
     * así que no existe ventana en la que una nota se quede sin despertar el
     * stream.
     */
    private fun suspendUntilWoken(track: AudioTrack) {
        wakeSignal.drainPermits()
        suspended = true
        if (engine.hasPendingCommands()) {
            suspended = false
            return
        }
        try {
            track.pause()
        } catch (e: Exception) {
            Log.w(TAG, "pause() falló al suspender", e)
        }
        try {
            wakeSignal.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        suspended = false
        if (running) {
            try {
                track.play()
            } catch (e: Exception) {
                Log.w(TAG, "play() falló al despertar", e)
            }
        }
    }

    private fun buildTrack(): AudioTrack? {
        return buildWithMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            ?: buildWithMode(AudioTrack.PERFORMANCE_MODE_NONE)
    }

    private fun buildWithMode(performanceMode: Int): AudioTrack? {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(engine.sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            engine.sampleRateHz, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBytes <= 0) return null
        val capacityBytes = maxOf(minBytes, burstFrames * MAX_BURSTS * CHANNELS * BYTES_PER_FLOAT)
        return try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(capacityBytes)
                .setPerformanceMode(performanceMode)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return null
            }
            track.setBufferSizeInFrames(burstFrames * INITIAL_BURSTS)
            track.play()
            track
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear el AudioTrack (modo $performanceMode)", e)
            null
        }
    }

    private fun releaseQuietly(track: AudioTrack) {
        try {
            track.stop()
        } catch (ignored: Exception) {
        }
        try {
            track.release()
        } catch (ignored: Exception) {
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val TAG = "AudioOutputDriver"
        private const val CHANNELS = 2
        private const val BYTES_PER_FLOAT = 4
        private const val INITIAL_BURSTS = 2
        private const val MAX_BURSTS = 8
        private const val UNDERRUN_CHECK_BLOCKS = 32
        private const val STATS_WINDOW_BLOCKS = 256
        private const val LOAD_AVERAGE_ALPHA = 0.02
        private const val IDLE_SUSPEND_SECONDS = 15f
        private const val RETRY_DELAY_MS = 500L
        private const val STOP_JOIN_TIMEOUT_MS = 1000L

        private const val FALLBACK_SAMPLE_RATE = 48_000
        private const val FALLBACK_BURST_FRAMES = 256
        private const val MIN_BURST_FRAMES = 64
        private const val MAX_BURST_FRAMES = 1024

        /** Frecuencia de salida nativa del dispositivo (la que evita el remuestreo del sistema). */
        fun nativeSampleRate(context: Context): Int {
            val value = audioManager(context)?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            return value?.toIntOrNull()?.takeIf { it in 8_000..192_000 } ?: FALLBACK_SAMPLE_RATE
        }

        /** Tamaño de ráfaga nativo del dispositivo, acotado a un rango razonable. */
        fun nativeBurstFrames(context: Context): Int {
            val value = audioManager(context)?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            return (value?.toIntOrNull() ?: FALLBACK_BURST_FRAMES).coerceIn(MIN_BURST_FRAMES, MAX_BURST_FRAMES)
        }

        private fun audioManager(context: Context): AudioManager? =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
}
