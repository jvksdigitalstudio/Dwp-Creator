package com.jvk.dwpcreator.audio

import android.media.AudioFormat
import android.media.AudioTrack
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reproducción nativa de muestras vía `AudioTrack`, siempre como PCM de 16
 * bits (ver [DecodedSampleCache] para el porqué de esa conversión).
 *
 * **Rediseño real de rendimiento (Doc/29 §2, "teclado profesional").** La
 * versión anterior de esta clase decodificaba el WAV, lo convertía a 16-bit
 * y construía un `AudioTrack` nuevo **en cada pulsación**, incluida cada
 * nota de un acorde -- trabajo repetido, evitable e idéntico cada vez que se
 * volvía a tocar la misma tecla, y la causa real del retardo percibido antes
 * de este cambio. Ahora:
 *
 * 1. [DecodedSampleCache] decodifica y convierte cada muestra **una sola
 *    vez** y devuelve el resultado listo para escribir en las pulsaciones
 *    siguientes (Kotlin/JVM puro, sin dependencia de Android).
 * 2. [AudioVoicePool] reutiliza las voces `AudioTrack` entre notas en vez de
 *    construir y destruir una por pulsación, e intenta el modo de baja
 *    latencia de la plataforma (`PERFORMANCE_MODE_LOW_LATENCY`).
 * 3. El hilo que escribe cada nota en su voz viene de un pool de hilos fijo,
 *    reutilizado entre notas -- no un `Thread` nuevo por pulsación.
 *
 * Sigue siendo polifónico: cada nota activa usa su propia voz del pool, así
 * que las notas superpuestas nunca se cortan entre sí.
 *
 * **Nota sostenida real ("teclado profesional", corrección de comportamiento
 * de tecla mantenida).** [play] ya no es un disparo ciego que se deja correr
 * hasta el final de la muestra sin importar lo que haga el usuario: escribe
 * el PCM en trozos pequeños ([WRITE_CHUNK_MS]) en vez de en un único
 * `write()` bloqueante de todo el archivo, precisamente para poder
 * reaccionar a media reproducción. Mientras el dedo (o la tecla física MIDI)
 * sigue apretado la voz sigue escribiendo trozos con normalidad -- si la
 * muestra es más corta que el tiempo que dura la pulsación, simplemente
 * termina sola, como cualquier one-shot. Pero en cuanto llega [stopNote]
 * para ese índice, el trozo pendiente se sustituye por una cola corta con
 * `fade-out` lineal ([RELEASE_FADE_MS]) en vez de cortar la voz en seco (lo
 * que sonaría como un "clic"/"pop" de discontinuidad de forma de onda), y la
 * voz se libera de inmediato tras escribir esa cola -- sin esperar a que el
 * resto de la muestra original termine de sonar. Este es el mecanismo real
 * detrás de "mientras siga pulsando debe sonar, y al soltar debe callarse
 * de inmediato": antes, la nota simplemente se dejaba correr entera pasara
 * lo que pasara con el dedo.
 */
class SamplePlayer {

    private val cache = DecodedSampleCache()
    private val pool = AudioVoicePool(MAX_CONCURRENT_VOICES)

    /** Hilos daemon reutilizados para escribir PCM en las voces activas -- reemplaza un `Thread` nuevo por nota. */
    private val writerExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_VOICES, DaemonThreadFactory("SamplePlayer-writer"))

    /**
     * Voces actualmente en reproducción, apiladas por índice de muestra
     * (varias voces pueden compartir índice: p. ej. dos dedos sobre la
     * misma tecla, o un re-disparo mientras la anterior aún no se apagó).
     * [stopNote] apaga la más reciente para ese índice (LIFO) -- coincide
     * con el patrón real de uso: la última pulsación de esa tecla es la que
     * el gesto que la soltó estaba seguramente controlando. Protegido por
     * [voiceHandlesLock], porque se lee/escribe tanto desde el hilo que
     * llama a [play] (normalmente `Dispatchers.Default`) como, para
     * [stopNote], desde el hilo que procesa el gesto de soltar (UI/main).
     */
    private val voiceHandlesByIndex = mutableMapOf<Int, ArrayDeque<VoiceHandle>>()
    private val voiceHandlesLock = Any()

    /** Bandera de "liberar ya" compartida entre quien pide detener la nota y el hilo que la está escribiendo. */
    private class VoiceHandle {
        val releaseRequested = AtomicBoolean(false)
    }

    companion object {
        /**
         * Tope de seguridad de voces concurrentes (no un motor de "voice
         * stealing", fuera de alcance -- ver el KDoc original de esta
         * decisión). Simplemente declinar una voz nueva por encima de este
         * límite es el comportamiento seguro más simple: nunca corrompe
         * estado, solo omite sonido para la voz excedente.
         */
        private const val MAX_CONCURRENT_VOICES = 24

        /** Cada cuánto se consulta si el AudioTrack ya reprodujo todo lo escrito. */
        private const val DRAIN_POLL_INTERVAL_MS = 10L

        /** Margen añadido al tiempo teórico de vaciado del buffer antes de rendirse y liberar igualmente. */
        private const val DRAIN_SAFETY_MARGIN_MS = 500L

        /**
         * Tamaño de cada trozo escrito al `AudioTrack`, en milisegundos de
         * audio. Escribir la muestra completa en un único `write()`
         * bloqueante (el diseño anterior) impide reaccionar a [stopNote]
         * hasta que ese único `write()` vuelve -- es decir, hasta que la
         * muestra entera ya se escribió. Trocear en ventanas de ~10ms es el
         * tamaño estándar de una tabla de ondas/motor de sampler para
         * "sentir" la respuesta a soltar la tecla como instantánea sin
         * saturar el hilo escritor de llamadas.
         */
        private const val WRITE_CHUNK_MS = 10L

        /**
         * Duración del `fade-out` lineal aplicado a la cola de una nota
         * cortada por [stopNote] antes de silenciarla, para evitar el
         * "clic" audible de una discontinuidad brusca de forma de onda
         * (parar un `AudioTrack` a mitad de una muestra en amplitud
         * distinta de cero). 12ms es corto de sobra para sentirse
         * "inmediato" al soltar la tecla, y suficiente para que el oído no
         * perciba el corte como un chasquido.
         */
        private const val RELEASE_FADE_MS = 12L
    }

    /** `ThreadFactory` de hilos daemon con nombre -- para que no impidan cerrar la app y sean identificables en un volcado de hilos. */
    private class DaemonThreadFactory(private val baseName: String) : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread =
            Thread(r, "$baseName-${counter.incrementAndGet()}").apply { isDaemon = true }
    }

    /**
     * Decodifica y cachea por adelantado el audio de [audioByIndex], sin
     * bloquear al llamador (debe invocarse ya desde un hilo/contexto de
     * fondo). Es un precalentamiento de "mejor esfuerzo", no exhaustivo: se
     * detiene en cuanto la caché declara estar llena
     * ([DecodedSampleCache.isFull]), así que en un instrumento grande no
     * todas las muestras terminan calientes -- pero las primeras que el
     * usuario probablemente toque nada más cargar el instrumento, sí. Una
     * muestra no precalentada simplemente se decodifica en su primera
     * pulsación real, igual que antes de este cambio para todas.
     */
    fun prewarm(audioByIndex: List<ByteArray>) {
        for (index in audioByIndex.indices) {
            if (cache.isFull()) return
            cache.get(index, audioByIndex[index])
        }
    }

    /**
     * Dispara la muestra en la posición [index] (la misma clave estable que
     * [com.jvk.dwpcreator.domain.io.LoadedProject.audioByIndex]) a
     * [velocity] (convención MIDI, 1..127). Devuelve inmediatamente; la
     * reproducción ocurre de forma asíncrona en el pool de hilos de esta
     * instancia, y sigue sonando hasta que la muestra termine sola o hasta
     * que [stopNote] la corte (nota mantenida real, ver KDoc de la clase).
     */
    fun play(index: Int, wavBytes: ByteArray, velocity: Int = 127) {
        val cached = cache.get(index, wavBytes) ?: return
        val track = pool.acquire(cached.sampleRateHz, cached.channelCount) ?: return

        // setVolume() y play() se cubren con el mismo try/catch: si cualquiera de
        // los dos falla, la voz se descarta (nunca se recicla) en vez de dejar
        // que la excepción se propague fuera de esta corrutina -- sin capturar
        // aquí, un fallo transitorio de AudioTrack (pérdida de foco de audio, un
        // hueco del HAL, lo que sea) se colaría como una excepción no capturada
        // en el `Dispatchers.Default` de `noteOn`, y sin un manejador instalado
        // ahí, cerraría la app entera por una sola nota fallida.
        val started = try {
            track.setVolume(velocity.coerceIn(1, 127) / 127f)
            track.play()
            true
        } catch (e: Exception) {
            false
        }
        if (!started) {
            pool.discard(track)
            return
        }

        val bytesPerFrame = 2 * cached.channelCount // 16-bit PCM
        val totalFrames = cached.pcm16.size / bytesPerFrame.coerceAtLeast(1)
        val chunkFrames = (cached.sampleRateHz.toLong() * WRITE_CHUNK_MS / 1000L).toInt().coerceAtLeast(1)
        val chunkBytes = chunkFrames * bytesPerFrame
        val fadeFrames = (cached.sampleRateHz.toLong() * RELEASE_FADE_MS / 1000L).toInt().coerceAtLeast(1)

        val handle = registerVoiceHandle(index)

        writerExecutor.execute {
            // Voz "sana" solo si de verdad llegó a escribir algo antes de
            // terminar; ver AudioVoicePool.discard para el porqué de esta
            // distinción (evitar reciclar una voz que quedó rota en tiempo de
            // ejecución -- el pool no puede detectarlo por sí solo).
            var healthy = false
            var releasedEarly = false
            try {
                var offset = 0
                // Se escribe en trozos pequeños (no la muestra entera de una
                // vez) precisamente para poder consultar `releaseRequested`
                // entre trozos -- ver WRITE_CHUNK_MS. Un único write()
                // bloqueante de todo el archivo (diseño anterior) no deja
                // ningún punto donde reaccionar a que el usuario soltó la
                // tecla antes de que la muestra termine sola.
                while (offset < cached.pcm16.size) {
                    if (handle.releaseRequested.get()) {
                        releasedEarly = true
                        break
                    }
                    val length = minOf(chunkBytes, cached.pcm16.size - offset)
                    val written = track.write(cached.pcm16, offset, length, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }
                healthy = offset > 0
                if (releasedEarly) {
                    // Tecla soltada a media reproducción: en vez de cortar la
                    // voz en seco (posible "clic" de discontinuidad de forma
                    // de onda), se escribe una cola corta con fade-out lineal
                    // partiendo de donde se quedó la muestra, y se libera la
                    // voz de inmediato después -- sin esperar el resto de la
                    // muestra original.
                    writeReleaseFadeTail(track, cached.pcm16, offset, bytesPerFrame, fadeFrames)
                } else if (offset >= cached.pcm16.size) {
                    // `write` en MODE_STREAM vuelve en cuanto el último trozo ENTRA en el
                    // buffer del track, no cuando ya SONÓ; se espera a que la posición de
                    // reproducción alcance el último frame antes de devolver la voz al pool
                    // (si no, la siguiente nota que reutilice esta voz cortaría la cola de esta).
                    // Se recalcula a partir del formato ya conocido de la muestra (cached),
                    // no de propiedades leídas de `track`: son exactamente el mismo par
                    // (sampleRateHz, channelCount) con el que el pool construyó/agrupó esta
                    // voz, y evita depender de getters de AudioTrack para esto.
                    val channelConfig = if (cached.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                    val minBufferSize = AudioTrack.getMinBufferSize(cached.sampleRateHz, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
                    val bufferFrames = if (minBufferSize > 0) minBufferSize / bytesPerFrame.coerceAtLeast(1) else totalFrames
                    val drainTimeoutMs = bufferFrames * 1000L / cached.sampleRateHz.coerceAtLeast(1) + DRAIN_SAFETY_MARGIN_MS
                    awaitPlaybackDrained(track, totalFrames, drainTimeoutMs)
                }
            } catch (ignored: Exception) {
                // La voz puede haber sido liberada/detenida concurrentemente (p. ej.
                // releaseAll()), o puede haberse roto de verdad en tiempo de
                // ejecución -- en ambos casos `healthy` sigue en `false` y el
                // `finally` la descarta en vez de reciclarla.
            } finally {
                unregisterVoiceHandle(index, handle)
                if (healthy) {
                    pool.recycle(cached.sampleRateHz, cached.channelCount, track)
                } else {
                    pool.discard(track)
                }
            }
        }
    }

    /**
     * Corta de inmediato (con un `fade-out` corto anti-clic) la voz
     * actualmente sonando para la muestra [index] -- la más reciente si hay
     * más de una (acorde con la misma tecla repetida). Si esa muestra ya
     * terminó de sonar por sí sola (o nunca llegó a arrancar), no hace
     * nada: no hay ninguna voz activa que cortar. No toca el estado de la
     * "luz" de la tecla -- eso lo gestiona
     * [com.jvk.dwpcreator.viewmodel.DwpCreatorViewModel]; esta llamada
     * solo controla el audio real.
     */
    fun stopNote(index: Int) {
        val handle = synchronized(voiceHandlesLock) {
            voiceHandlesByIndex[index]?.let { stack ->
                val removed = if (stack.isNotEmpty()) stack.removeLast() else null
                if (stack.isEmpty()) voiceHandlesByIndex.remove(index)
                removed
            }
        }
        handle?.releaseRequested?.set(true)
    }

    private fun registerVoiceHandle(index: Int): VoiceHandle {
        val handle = VoiceHandle()
        synchronized(voiceHandlesLock) {
            voiceHandlesByIndex.getOrPut(index) { ArrayDeque() }.addLast(handle)
        }
        return handle
    }

    private fun unregisterVoiceHandle(index: Int, handle: VoiceHandle) {
        synchronized(voiceHandlesLock) {
            val stack = voiceHandlesByIndex[index] ?: return
            stack.remove(handle)
            if (stack.isEmpty()) voiceHandlesByIndex.remove(index)
        }
    }

    /**
     * Escribe, a partir de [offset] dentro de [pcm16], una cola de como
     * mucho [fadeFrames] frames con un `fade-out` lineal (ganancia 1 -> 0)
     * y detiene la voz inmediatamente después. Se copia el tramo a un
     * búfer temporal propio -- nunca se muta [pcm16] en el sitio, porque es
     * el mismo `ByteArray` cacheado en [DecodedSampleCache] y se reutiliza
     * en futuras pulsaciones de esta misma muestra; mutarlo dejaría esas
     * pulsaciones futuras ya "pre-desvanecidas".
     */
    private fun writeReleaseFadeTail(track: AudioTrack, pcm16: ByteArray, offset: Int, bytesPerFrame: Int, fadeFrames: Int) {
        val remainingBytes = pcm16.size - offset
        if (remainingBytes > 0 && bytesPerFrame > 0) {
            val availableFrames = remainingBytes / bytesPerFrame
            val framesToFade = minOf(fadeFrames, availableFrames).coerceAtLeast(1)
            val fadeByteCount = framesToFade * bytesPerFrame
            val tail = ByteArray(fadeByteCount)
            System.arraycopy(pcm16, offset, tail, 0, fadeByteCount)
            applyLinearFadeOut(tail, framesToFade, bytesPerFrame)
            try {
                track.write(tail, 0, tail.size, AudioTrack.WRITE_BLOCKING)
            } catch (ignored: Exception) {
                // Si ni siquiera la cola de fade se pudo escribir, igual se
                // detiene la voz a continuación -- mejor un corte sin cola
                // que dejarla sonando indefinidamente.
            }
        }
        try {
            track.pause()
        } catch (ignored: Exception) {
        }
        try {
            track.flush()
        } catch (ignored: Exception) {
        }
    }

    /**
     * Aplica, en el sitio, una rampa lineal de ganancia 1.0 -> 0.0 a lo
     * largo de [frameCount] frames de PCM de 16 bits con signo (little
     * endian, cualquier número de canales), idéntica en todos los canales
     * de cada frame para no desplazar el balance estéreo durante el
     * desvanecimiento.
     */
    private fun applyLinearFadeOut(pcm: ByteArray, frameCount: Int, bytesPerFrame: Int) {
        val channelsPerFrame = (bytesPerFrame / 2).coerceAtLeast(1) // 2 bytes por muestra (16-bit)
        for (frame in 0 until frameCount) {
            val gain = 1f - (frame.toFloat() / frameCount.toFloat())
            val frameOffset = frame * bytesPerFrame
            for (channel in 0 until channelsPerFrame) {
                val sampleOffset = frameOffset + channel * 2
                if (sampleOffset + 1 >= pcm.size) continue
                val lo = pcm[sampleOffset].toInt() and 0xFF
                val hi = pcm[sampleOffset + 1].toInt() // con signo: preserva la extensión de signo del byte alto
                val sample = (hi shl 8) or lo
                val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[sampleOffset] = (scaled and 0xFF).toByte()
                pcm[sampleOffset + 1] = ((scaled shr 8) and 0xFF).toByte()
            }
        }
    }

    /**
     * Bloquea el hilo escritor hasta que [track] haya reproducido [totalFrames]
     * frames (o hasta [timeoutMs], o hasta que el track deje de estar en
     * reproducción -- p. ej. porque [releaseAll] lo detuvo --). El tope evita
     * que una voz atascada retenga su hilo para siempre.
     */
    private fun awaitPlaybackDrained(track: AudioTrack, totalFrames: Int, timeoutMs: Long) {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadlineNanos) {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
            if (track.playbackHeadPosition >= totalFrames) return
            try {
                Thread.sleep(DRAIN_POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** Detiene y libera de verdad todas las voces (no las devuelve al pool) y cierra el pool de hilos. Llamar desde el cleanup del propietario (p. ej. ViewModel.onCleared). */
    fun releaseAll() {
        synchronized(voiceHandlesLock) { voiceHandlesByIndex.clear() }
        pool.releaseAll()
        writerExecutor.shutdownNow()
    }
}
