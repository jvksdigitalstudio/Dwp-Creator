package com.jvk.dwpcreator.audio

import android.media.AudioFormat
import android.media.AudioTrack
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 *
 * **Pan y Volumen reales por nota (mini-mezclador por muestra, `SampleRow`).**
 * [play] acepta un [Pair] de ganancias resuelto una sola vez al iniciar la
 * nota ([resolvePanGains]: paneo de potencia constante -- estándar de mesa
 * de mezcla, no un paneo lineal simple -- multiplicado por el volumen 0f..1f
 * del mezclador) y las aplica **directamente sobre el PCM**, muestra a
 * muestra, en cada trozo que escribe -- no con `AudioTrack.setStereoVolume`
 * (deprecada desde API 21, y de todas formas inútil aquí, ver el porqué de
 * la salida siempre estéreo dos líneas más abajo). Por eso toda voz de esta
 * clase se reproduce ahora por una pista de salida **siempre estéreo**
 * ([STEREO_CHANNEL_COUNT]), sin importar si la muestra de origen es mono o
 * estéreo: una pista de salida mono físicamente no tiene manera de
 * posicionar el sonido entre izquierda y derecha, así que una muestra mono
 * se "sube" a estéreo duplicando su señal en ambos canales con la ganancia
 * ya resuelta de cada uno ([writeStereoChunk]). Para una muestra ya
 * estéreo, esto actúa como un control de balance (escala cada canal de
 * origen con su ganancia) en vez de un paneo "puro" que mezclara los
 * canales entre sí -- simplificación deliberada y documentada, suficiente
 * para lo que pide un mezclador de previsualización de muestras de
 * instrumento (casi siempre mono en un `.dwp` real). El paneo/volumen se
 * fija al iniciar la nota con el ajuste vigente en ese instante -- no hay
 * automatización en vivo del pan/volumen a media nota sostenida, igual que
 * un fader físico que no se puede mover mientras la nota ya está sonando en
 * este diseño.
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

        /** Toda voz se reproduce por una pista de salida estéreo -- ver KDoc de clase, sección "Pan y Volumen reales". */
        private const val STEREO_CHANNEL_COUNT = 2
        private const val STEREO_BYTES_PER_FRAME = 4 // 2 canales * 16 bits
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
     * [velocity] (convención MIDI, 1..127), con el [volume] (0f..1f) y
     * [pan] (-1f..1f) vigentes del mini-mezclador de esa muestra en este
     * instante (ver KDoc de clase, sección "Pan y Volumen reales" -- se
     * resuelven una sola vez aquí, no se automatizan a media nota). Devuelve
     * inmediatamente; la reproducción ocurre de forma asíncrona en el pool
     * de hilos de esta instancia, y sigue sonando hasta que la muestra
     * termine sola o hasta que [stopNote] la corte (nota mantenida real, ver
     * KDoc de la clase).
     */
    fun play(index: Int, wavBytes: ByteArray, velocity: Int = 127, volume: Float = 1f, pan: Float = 0f) {
        // La voz se REGISTRA antes de cualquier trabajo lento (decodificar la
        // muestra la primera vez, pedir una voz al pool). Si el usuario suelta la
        // tecla mientras eso ocurre, [stopNote] ya encuentra este registro y
        // levanta la bandera de liberación: el bucle de escritura la ve en su
        // primer trozo y corta con el fade-out corto en vez de dejar sonar la
        // muestra entera (nota "pegada"). Registrar después de decodificar -- el
        // orden anterior -- dejaba justo esa ventana abierta con muestras frías.
        // Si el arranque no llega a entregar la voz al hilo escritor (muestra no
        // reproducible, sin voz libre, fallo de AudioTrack), se retira el registro
        // aquí; si llega, lo retira el propio hilo escritor al terminar.
        val handle = registerVoiceHandle(index)
        var handedToWriter = false
        try {
            handedToWriter = startVoice(index, handle, wavBytes, velocity, volume, pan)
        } finally {
            if (!handedToWriter) unregisterVoiceHandle(index, handle)
        }
    }

    /** Cuerpo real de [play]. Devuelve `true` solo si la voz quedó entregada al hilo escritor. */
    private fun startVoice(
        index: Int,
        handle: VoiceHandle,
        wavBytes: ByteArray,
        velocity: Int,
        volume: Float,
        pan: Float
    ): Boolean {
        val cached = cache.get(index, wavBytes) ?: return false
        // Siempre se pide una voz ESTÉREO -- ver KDoc de clase -- sin importar
        // el número real de canales de la muestra de origen (`cached.channelCount`,
        // que sigue rigiendo cómo se LEE `cached.pcm16` más abajo).
        val track = pool.acquire(cached.sampleRateHz, STEREO_CHANNEL_COUNT) ?: return false

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
            return false
        }

        val sourceBytesPerFrame = 2 * cached.channelCount // 16-bit PCM, formato de ORIGEN (mono o estéreo)
        val totalFrames = cached.pcm16.size / sourceBytesPerFrame.coerceAtLeast(1)
        val chunkFrames = (cached.sampleRateHz.toLong() * WRITE_CHUNK_MS / 1000L).toInt().coerceAtLeast(1)
        val fadeFrames = (cached.sampleRateHz.toLong() * RELEASE_FADE_MS / 1000L).toInt().coerceAtLeast(1)
        val (leftGain, rightGain) = resolvePanGains(pan, volume)

        writerExecutor.execute {
            // Voz "sana" solo si de verdad llegó a escribir algo antes de
            // terminar; ver AudioVoicePool.discard para el porqué de esta
            // distinción (evitar reciclar una voz que quedó rota en tiempo de
            // ejecución -- el pool no puede detectarlo por sí solo).
            var healthy = false
            var releasedEarly = false
            try {
                var frameOffset = 0
                // Se escribe en trozos pequeños (no la muestra entera de una
                // vez) precisamente para poder consultar `releaseRequested`
                // entre trozos -- ver WRITE_CHUNK_MS. Un único write()
                // bloqueante de todo el archivo (diseño anterior) no deja
                // ningún punto donde reaccionar a que el usuario soltó la
                // tecla antes de que la muestra termine sola.
                while (frameOffset < totalFrames) {
                    if (handle.releaseRequested.get()) {
                        releasedEarly = true
                        break
                    }
                    val framesInChunk = minOf(chunkFrames, totalFrames - frameOffset)
                    val framesWritten = writeStereoChunk(
                        track = track,
                        sourcePcm = cached.pcm16,
                        sourceOffsetBytes = frameOffset * sourceBytesPerFrame,
                        frameCount = framesInChunk,
                        sourceBytesPerFrame = sourceBytesPerFrame,
                        sourceChannelCount = cached.channelCount,
                        leftGain = leftGain,
                        rightGain = rightGain
                    )
                    if (framesWritten <= 0) break
                    frameOffset += framesWritten
                }
                healthy = frameOffset > 0
                if (releasedEarly) {
                    // Tecla soltada a media reproducción: en vez de cortar la
                    // voz en seco (posible "clic" de discontinuidad de forma
                    // de onda), se escribe una cola corta con fade-out lineal
                    // partiendo de donde se quedó la muestra, y se libera la
                    // voz de inmediato después -- sin esperar el resto de la
                    // muestra original.
                    writeReleaseFadeTail(
                        track = track,
                        sourcePcm = cached.pcm16,
                        sourceOffsetBytes = frameOffset * sourceBytesPerFrame,
                        sourceBytesPerFrame = sourceBytesPerFrame,
                        sourceChannelCount = cached.channelCount,
                        leftGain = leftGain,
                        rightGain = rightGain,
                        fadeFrames = fadeFrames
                    )
                } else if (frameOffset >= totalFrames) {
                    // `write` en MODE_STREAM vuelve en cuanto el último trozo ENTRA en el
                    // buffer del track, no cuando ya SONÓ; se espera a que la posición de
                    // reproducción alcance el último frame antes de devolver la voz al pool
                    // (si no, la siguiente nota que reutilice esta voz cortaría la cola de esta).
                    // La voz real siempre es estéreo (ver KDoc de clase), así que el buffer
                    // mínimo se calcula con ese formato -- no con el de la muestra de origen.
                    val minBufferSize = AudioTrack.getMinBufferSize(cached.sampleRateHz, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                    val bufferFrames = if (minBufferSize > 0) minBufferSize / STEREO_BYTES_PER_FRAME else totalFrames
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
                    pool.recycle(cached.sampleRateHz, STEREO_CHANNEL_COUNT, track)
                } else {
                    pool.discard(track)
                }
            }
        }
        return true
    }

    /**
     * Escribe [frameCount] frames de [sourcePcm] (a partir de
     * [sourceOffsetBytes], en el formato de ORIGEN -- mono o estéreo, ver
     * [sourceChannelCount]) hacia [track], que siempre es una voz de salida
     * **estéreo** ([STEREO_CHANNEL_COUNT]): una muestra mono se duplica en
     * ambos canales de salida, cada uno con su propia ganancia ya resuelta
     * ([leftGain]/[rightGain], ver [resolvePanGains]) -- este es el
     * mecanismo real detrás del control de Pan (ver KDoc de clase). Devuelve
     * los **frames** de origen efectivamente escritos (no bytes), para que
     * el bucle de [play] pueda avanzar su propio contador en unidades de
     * frame sin tener que reconvertir entre el tamaño de frame de origen y
     * el de salida; `<= 0` indica que `write()` falló o no aceptó nada.
     */
    private fun writeStereoChunk(
        track: AudioTrack,
        sourcePcm: ByteArray,
        sourceOffsetBytes: Int,
        frameCount: Int,
        sourceBytesPerFrame: Int,
        sourceChannelCount: Int,
        leftGain: Float,
        rightGain: Float
    ): Int {
        val out = ByteArray(frameCount * STEREO_BYTES_PER_FRAME)
        for (frame in 0 until frameCount) {
            val srcOffset = sourceOffsetBytes + frame * sourceBytesPerFrame
            val leftSample = readInt16LE(sourcePcm, srcOffset)
            // Mono: el mismo sample alimenta ambos canales de salida (cada uno ya con
            // su propia ganancia); estéreo: se lee el canal derecho real de origen.
            val rightSample = if (sourceChannelCount == 2) readInt16LE(sourcePcm, srcOffset + 2) else leftSample
            val outOffset = frame * STEREO_BYTES_PER_FRAME
            writeInt16LE(out, outOffset, scaleSample(leftSample, leftGain))
            writeInt16LE(out, outOffset + 2, scaleSample(rightSample, rightGain))
        }
        val written = track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
        if (written <= 0) return written
        return written / STEREO_BYTES_PER_FRAME
    }

    /**
     * Resuelve, una sola vez por nota, la ganancia de cada canal de salida
     * ([leftGain] a [rightGain]) a partir del [pan] (-1f..1f) y el [volume]
     * (0f..1f) vigentes del mini-mezclador -- ver KDoc de clase.
     *
     * Ley de paneo de **potencia constante** (equal-power pan law): en vez
     * de un paneo lineal simple (`left = (1-pan)/2`, `right = (1+pan)/2`),
     * que se percibe más flojo justo en el centro, esta ley mantiene el
     * volumen PERCIBIDO aproximadamente constante en todo el recorrido del
     * control -- el estándar de cualquier mesa de mezcla/DAW profesional.
     * En `pan = -1f` (extremo izquierdo), `leftGain = 1 * volume` y
     * `rightGain = 0`; en `pan = 0f` (centro), ambos valen
     * `volume * (raíz de 2)/2 ≈ 0.707 * volume`; en `pan = 1f` (extremo
     * derecho), se invierte.
     */
    private fun resolvePanGains(pan: Float, volume: Float): Pair<Float, Float> {
        val clampedPan = pan.coerceIn(-1f, 1f)
        val clampedVolume = volume.coerceIn(0f, 1f)
        val angle = (clampedPan + 1f) * (PI / 4.0).toFloat() // 0 en pan=-1 (izquierda) .. PI/2 en pan=+1 (derecha)
        val left = cos(angle) * clampedVolume
        val right = sin(angle) * clampedVolume
        return left to right
    }

    /** Escala [sample] (16 bits con signo) por [gain] y satura al rango representable -- evita "wrap-around" si `gain` empujara el valor fuera de -32768..32767. */
    private fun scaleSample(sample: Int, gain: Float): Int =
        (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    /** Lee una muestra PCM de 16 bits con signo, little-endian, en [offset] de [pcm]. */
    private fun readInt16LE(pcm: ByteArray, offset: Int): Int {
        val lo = pcm[offset].toInt() and 0xFF
        val hi = pcm[offset + 1].toInt() // con signo: preserva la extensión de signo del byte alto
        return (hi shl 8) or lo
    }

    /** Escribe [value] (16 bits con signo) en [offset] de [pcm], little-endian. */
    private fun writeInt16LE(pcm: ByteArray, offset: Int, value: Int) {
        pcm[offset] = (value and 0xFF).toByte()
        pcm[offset + 1] = ((value shr 8) and 0xFF).toByte()
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
     * Escribe, a partir de [sourceOffsetBytes] dentro de [sourcePcm] (formato
     * de ORIGEN -- mono o estéreo), una cola de como mucho [fadeFrames]
     * frames hacia [track] (siempre estéreo, igual que [writeStereoChunk])
     * con un `fade-out` lineal (envolvente 1 -> 0) **combinado** con las
     * ganancias de pan/volumen ya resueltas ([leftGain]/[rightGain]), y
     * detiene la voz inmediatamente después. Se copia el tramo a un búfer de
     * salida propio -- nunca se muta [sourcePcm] en el sitio, porque es el
     * mismo `ByteArray` cacheado en [DecodedSampleCache] y se reutiliza en
     * futuras pulsaciones de esta misma muestra; mutarlo dejaría esas
     * pulsaciones futuras ya "pre-desvanecidas".
     */
    private fun writeReleaseFadeTail(
        track: AudioTrack,
        sourcePcm: ByteArray,
        sourceOffsetBytes: Int,
        sourceBytesPerFrame: Int,
        sourceChannelCount: Int,
        leftGain: Float,
        rightGain: Float,
        fadeFrames: Int
    ) {
        val remainingBytes = sourcePcm.size - sourceOffsetBytes
        if (remainingBytes > 0 && sourceBytesPerFrame > 0) {
            val availableFrames = remainingBytes / sourceBytesPerFrame
            val framesToFade = minOf(fadeFrames, availableFrames).coerceAtLeast(1)
            val out = ByteArray(framesToFade * STEREO_BYTES_PER_FRAME)
            for (frame in 0 until framesToFade) {
                val envelope = 1f - (frame.toFloat() / framesToFade.toFloat())
                val srcOffset = sourceOffsetBytes + frame * sourceBytesPerFrame
                val leftSample = readInt16LE(sourcePcm, srcOffset)
                val rightSample = if (sourceChannelCount == 2) readInt16LE(sourcePcm, srcOffset + 2) else leftSample
                val outOffset = frame * STEREO_BYTES_PER_FRAME
                writeInt16LE(out, outOffset, scaleSample(leftSample, leftGain * envelope))
                writeInt16LE(out, outOffset + 2, scaleSample(rightSample, rightGain * envelope))
            }
            try {
                track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
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

    /**
     * Corta de inmediato cualquier nota que siguiera sonando y descarta
     * **todo** el audio cacheado ([DecodedSampleCache]) -- llamar siempre
     * que se cargue un instrumento/proyecto nuevo, antes de tocar cualquier
     * tecla de ese instrumento nuevo.
     *
     * **El bug real que esto corrige.** [DecodedSampleCache.get] indexa
     * únicamente por [index] -- la posición de la muestra dentro del
     * proyecto -- nunca por el contenido real de [wavBytes], bajo la
     * premisa (documentada en su propio KDoc) de que esa posición es una
     * "clave estable" para el mismo audio durante toda la vida del
     * proceso. Esa premisa es cierta *dentro* de un mismo instrumento
     * cargado, pero la app sí permite cargar un instrumento, tocarlo, y
     * luego usar LOAD otra vez para abrir uno **distinto** -- y ambos
     * instrumentos reutilizan las mismas posiciones (0, 1, 2...) para
     * audios completamente diferentes. Sin este reseteo, tras cargar el
     * segundo instrumento, tocar la posición 0 seguía devolviendo desde la
     * caché el PCM ya decodificado del **primer** instrumento -- el sonido
     * equivocado -- hasta que esa entrada se expulsara sola por LRU. Este
     * método no es una corrección puntual sobre ese síntoma: vacía la
     * caché entera, así que ninguna posición del proyecto nuevo puede
     * arrastrar audio del proyecto anterior.
     */
    fun resetForNewProject() {
        val staleHandles = synchronized(voiceHandlesLock) {
            val all = voiceHandlesByIndex.values.flatten()
            voiceHandlesByIndex.clear()
            all
        }
        staleHandles.forEach { it.releaseRequested.set(true) }
        cache.clear()
    }
}
