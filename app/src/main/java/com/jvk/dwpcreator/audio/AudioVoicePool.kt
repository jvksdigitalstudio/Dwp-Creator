package com.jvk.dwpcreator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Pool de voces `AudioTrack` en `MODE_STREAM`, reutilizables entre notas.
 *
 * **Por qué existe.** `AudioTrack.Builder().build()` no es una operación
 * barata: reserva un buffer nativo y abre una ruta real en el HAL de audio
 * del dispositivo. Antes de esta clase, `SamplePlayer.play()` hacía esto
 * -- y su liberación simétrica (`stop()`+`release()`) -- en **cada
 * pulsación**, incluida cada nota de un acorde. Ese coste de
 * construcción/destrucción, sumado por nota, es una causa real y bien
 * documentada de retardo percibido en apps de audio en Android. Este pool
 * construye cada voz una vez y la devuelve a un almacén de voces libres en
 * vez de destruirla, así que la segunda vez que se toca una tecla (de hecho,
 * casi siempre a partir de la segunda nota de la sesión) no hay ninguna
 * llamada a `Builder().build()` en el camino caliente.
 *
 * **Agrupación por formato.** Las voces se agrupan por
 * `(sampleRateHz, channelCount)` porque ese es el único subconjunto del
 * formato de audio que determina si una voz puede reutilizarse tal cual
 * (todas las muestras se reproducen siempre como PCM de 16 bits, ver
 * `SamplePlayer`/`DecodedSampleCache` -- eso nunca varía). Un instrumento
 * DirectWave real normalmente exporta todas sus muestras con el mismo
 * sample rate y número de canales, así que en la práctica hay un único
 * grupo y el pool actúa, en efecto, como una única pila de voces listas.
 *
 * **Modo de baja latencia.** Se intenta primero
 * `AudioTrack.PERFORMANCE_MODE_LOW_LATENCY` (disponible desde API 26, el
 * mismo `minSdk` de esta app -- sin necesidad de comprobar versión). Ese
 * modo pide al sistema la ruta de audio más rápida disponible (en
 * dispositivos con soporte "Pro Audio"/AAudio, del orden de 10-20ms en vez
 * de los 40-100ms+ de la ruta normal). **No siempre se concede**: en
 * hardware o combinaciones de sample rate que no lo soportan,
 * `Builder().build()` no lanza ninguna excepción -- simplemente entrega una
 * pista por la ruta normal sin avisar. Por eso, si el intento en modo baja
 * latencia falla en construirse o en inicializarse, se reintenta sin ese
 * modo (con el doble de margen de buffer, igual que antes de este cambio) en
 * vez de fallar la voz por completo. **No confirmado con hardware real en
 * este entorno de trabajo** (sin dispositivo/Android SDK disponible aquí,
 * igual que el resto del proyecto) si el dispositivo del usuario concede
 * realmente el modo rápido para el sample rate de sus muestras.
 */
class AudioVoicePool(private val maxVoices: Int) {

    private data class FormatKey(val sampleRateHz: Int, val channelCount: Int)

    private val idleVoices = mutableMapOf<FormatKey, ArrayDeque<AudioTrack>>()
    private var totalVoices = 0

    /** `true` tras [releaseAll]: una voz que intenta devolverse después ya no se reutiliza, se libera directamente. */
    @Volatile
    private var closed = false

    /**
     * Entrega una voz lista para usar (recién construida, o reciclada de
     * [recycle]) para el formato pedido, o `null` si el pool ya tiene
     * [maxVoices] voces vivas -- el mismo límite de seguridad que antes
     * imponía `SamplePlayer.MAX_CONCURRENT_VOICES` directamente sobre
     * `activeTracks`, ahora aplicado sobre voces del pool en vez de sobre
     * tracks efímeros.
     */
    @Synchronized
    fun acquire(sampleRateHz: Int, channelCount: Int): AudioTrack? {
        if (closed) return null
        val key = FormatKey(sampleRateHz, channelCount)
        val queue = idleVoices[key]
        if (queue != null) {
            while (queue.isNotEmpty()) {
                val track = queue.removeLast()
                if (track.state == AudioTrack.STATE_INITIALIZED) return track
                totalVoices-- // la voz murió mientras estaba inactiva; no cuenta contra el límite
            }
        }
        if (totalVoices >= maxVoices) return null
        val track = buildTrack(sampleRateHz, channelCount) ?: return null
        totalVoices++
        return track
    }

    /**
     * Devuelve [track] al pool para la próxima nota de este mismo formato,
     * en vez de liberarlo. `pause()` + `flush()` detiene la reproducción y
     * descarta cualquier audio ya encolado -- tras `flush()`,
     * `getPlaybackHeadPosition()` vuelve a 0 (documentado por la propia
     * plataforma), que es justo lo que necesita
     * `SamplePlayer.awaitPlaybackDrained` para esperar correctamente la
     * siguiente vez que se escriba en esta misma voz.
     *
     * Solo para una voz que se sabe sana: si `pause()`/`flush()` fallan aquí
     * (el único indicio de salud que esta llamada puede comprobar por sí
     * misma), se descarta en vez de reciclarse -- ver [discard] para el caso
     * general, que es responsabilidad de quien reproduce detectar (p. ej. un
     * `write()` que nunca llega a escribir nada).
     */
    @Synchronized
    fun recycle(sampleRateHz: Int, channelCount: Int, track: AudioTrack) {
        if (closed) {
            releaseTrackQuiet(track)
            return
        }
        try {
            track.pause()
            track.flush()
        } catch (e: Exception) {
            discardLocked(track)
            return
        }
        val key = FormatKey(sampleRateHz, channelCount)
        idleVoices.getOrPut(key) { ArrayDeque() }.addLast(track)
    }

    /**
     * Libera [track] de verdad y libera su hueco en [maxVoices] para que se
     * pueda construir una voz nueva más adelante -- a diferencia de
     * [recycle], que la devuelve al pool para reutilizarla.
     *
     * **Por qué existe por separado de `recycle`.** `track.state` (usado en
     * [acquire] para descartar una voz inactiva que "murió mientras
     * esperaba") solo refleja si el `AudioTrack` se construyó con éxito, NO
     * si sigue funcionando -- una voz cuyo `play()` o `write()` falla en
     * tiempo de ejecución (pérdida de foco de audio, un fallo del HAL, lo
     * que sea) puede seguir reportando `STATE_INITIALIZED` para siempre.
     * Sin esta distinción, `SamplePlayer` reciclaría esa voz ya rota una y
     * otra vez -- una "voz zombi" que ocupa un hueco del límite de
     * [maxVoices] sin volver a sonar nunca, en una sesión de uso larga.
     * Quien reproduce ([SamplePlayer]) decide cuándo una voz demostró estar
     * rota (p. ej. un `write()` que nunca llegó a escribir ni un byte) y
     * llama aquí en vez de a [recycle] para esos casos.
     */
    @Synchronized
    fun discard(track: AudioTrack) {
        if (closed) {
            releaseTrackQuiet(track)
            return
        }
        discardLocked(track)
    }

    /** Libera [track] y decrementa [totalVoices]; asume que ya se tiene el lock de esta instancia. */
    private fun discardLocked(track: AudioTrack) {
        releaseTrackQuiet(track)
        if (totalVoices > 0) totalVoices--
    }

    /** Libera de verdad todas las voces inactivas y marca el pool como cerrado: llamar desde `SamplePlayer.releaseAll()`. */
    @Synchronized
    fun releaseAll() {
        closed = true
        idleVoices.values.forEach { queue -> queue.forEach(::releaseTrackQuiet) }
        idleVoices.clear()
        totalVoices = 0
    }

    private fun releaseTrackQuiet(track: AudioTrack) {
        try {
            track.stop()
        } catch (ignored: Exception) {
        }
        try {
            track.release()
        } catch (ignored: Exception) {
        }
    }

    private fun buildTrack(sampleRateHz: Int, channelCount: Int): AudioTrack? {
        val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBufferSize <= 0) return null // combinación de sample rate/canales no soportada por este dispositivo

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelConfig)
            .setEncoding(encoding)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        return buildWithPerformanceMode(audioFormat, attributes, minBufferSize, AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            ?: buildWithPerformanceMode(audioFormat, attributes, minBufferSize * 2, AudioTrack.PERFORMANCE_MODE_NONE)
    }

    private fun buildWithPerformanceMode(
        audioFormat: AudioFormat,
        attributes: AudioAttributes,
        bufferSizeBytes: Int,
        performanceMode: Int
    ): AudioTrack? {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(audioFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setPerformanceMode(performanceMode)
                .build()
        } catch (e: Exception) {
            return null
        }
        // Defensivo (mismo motivo que antes de este cambio): build() puede volver
        // sin lanzar y aun así no llegar a STATE_INITIALIZED en algunos OEM.
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }
}
