package com.jvk.dwpcreator.audio

import android.content.Context
import com.jvk.dwpcreator.audio.dsp.SamplerFxState
import com.jvk.dwpcreator.domain.audio.DecodedSampleCache

/**
 * Fachada de reproducción del instrumento: decodifica (con caché), dispara y
 * libera notas, y expone el bus de efectos. La API pública se mantiene
 * (`play`, `stopNote`, `prewarm`, `resetForNewProject`, `releaseAll`) para que el
 * resto de la app no cambie; por dentro, el audio ya no sale de "un
 * `AudioTrack` por nota" sino de un único motor de mezcla
 * ([SamplerEngine]) con su cadena de efectos maestra, volcado a hardware por
 * [AudioOutputDriver] -- ver el KDoc de [SamplerEngine] para el porqué.
 *
 * **Todo el audio es bajo demanda, no al construir el objeto.** Crear el `SamplePlayer`
 * (al arrancar la app) no construye el motor de mezcla ni abre ningún stream ni hilo: el
 * motor (con sus búferes de efectos) se crea y el stream se abre en [prewarm] (al cargar
 * un instrumento) o, como red de seguridad, en el primer [play]. Quien abre la app sólo
 * para renombrar o exportar un `.dwp` no paga nada de audio, y el arranque en frío que
 * mide el Baseline Profile no toca el sistema de audio (verificado en el perfil real de
 * Doc/68: sin `start`, `runLoop` ni `render`; esta versión además evita las construcciones).
 *
 * **Contabilidad de notas (nota sostenida real).** Cada `play` registra su
 * [SamplerEngine.VoiceToken] **antes** de cualquier trabajo lento
 * (decodificar la muestra la primera vez); [stopNote] marca el token
 * liberado en cuanto la tecla se suelta, aunque el audio aún no haya llegado
 * al motor. Si la liberación llegó antes que el audio, la voz arranca ya
 * marcada y el motor la apaga tras su tiempo mínimo de nota -- nunca queda una
 * nota pegada. Varias voces pueden compartir índice (dos dedos, re-disparo);
 * [stopNote] libera la **más reciente que siga sonando** (LIFO).
 */
class SamplePlayer(context: Context) {

    private val cache = DecodedSampleCache()

    private val appContext = context.applicationContext

    /** Motor de mezcla + salida de hardware: un par que se construye junto, la primera vez que hace falta. */
    private class AudioBackend(val engine: SamplerEngine, val driver: AudioOutputDriver)

    private val backendLock = Any()

    @Volatile
    private var backend: AudioBackend? = null

    /** Último estado de efectos pedido; sólo se toca con [backendLock] tomado, para no perder ninguna actualización. */
    private var fxState = SamplerFxState()

    /**
     * Devuelve el motor de audio, construyéndolo la primera vez (doble comprobación). Crear
     * [SamplerEngine] reserva los búferes de delay/reverb (~1 MB) y carga ~30 clases de DSP:
     * trabajo que no debe pagarse al arrancar la app, sólo cuando hay algo que reproducir.
     */
    private fun backend(): AudioBackend = backend ?: synchronized(backendLock) {
        backend ?: createBackend().also { backend = it }
    }

    private fun createBackend(): AudioBackend {
        val engine = SamplerEngine(AudioOutputDriver.nativeSampleRate(appContext))
        engine.fxState = fxState
        return AudioBackend(engine, AudioOutputDriver(engine, AudioOutputDriver.nativeBurstFrames(appContext)))
    }

    private val tokensByIndex = mutableMapOf<Int, ArrayDeque<SamplerEngine.VoiceToken>>()
    private val tokensLock = Any()

    /**
     * Decodifica y cachea por adelantado el audio de [audioByIndex]; llamar
     * desde un hilo de fondo. Mejor esfuerzo: se detiene en cuanto la caché
     * declara estar llena ([DecodedSampleCache.isFull]).
     */
    fun prewarm(audioByIndex: List<ByteArray>) {
        // Un instrumento recién cargado es la señal de que se va a tocar: se abre ya el
        // stream de audio (en su propio hilo) para que la primera nota no pague su apertura.
        backend().driver.start()
        for (index in audioByIndex.indices) {
            if (cache.isFull()) return
            cache.get(index, audioByIndex[index])
        }
    }

    /**
     * Dispara la muestra [index] a [velocity] (1..127) con el [volume]
     * (0f..1f) y [pan] (-1f..1f) vigentes del mini-mezclador de esa muestra.
     * Sigue sonando hasta que la muestra termine o [stopNote] la libere.
     */
    fun play(index: Int, wavBytes: ByteArray, velocity: Int = 127, volume: Float = 1f, pan: Float = 0f) {
        val audio = backend()
        audio.driver.start() // idempotente: red de seguridad si se toca sin haber pasado por prewarm
        val token = SamplerEngine.VoiceToken()
        register(index, token)

        val sample = cache.get(index, wavBytes)
        if (sample == null) {
            token.finished = true
            return
        }
        audio.engine.noteOn(token, sample, velocity, volume, pan)
        audio.driver.wake()
    }

    /**
     * Libera (con el fundido de liberación del motor, sin clic) la voz más
     * reciente de [index] que siga sonando. Si ya terminó por sí sola o no
     * existe, no hace nada.
     */
    fun stopNote(index: Int) {
        val token = synchronized(tokensLock) {
            val stack = tokensByIndex[index] ?: return
            var found: SamplerEngine.VoiceToken? = null
            while (found == null && stack.isNotEmpty()) {
                val candidate = stack.removeLast()
                if (!candidate.finished) found = candidate
            }
            if (stack.isEmpty()) tokensByIndex.remove(index)
            found
        }
        token?.released = true
    }

    private fun register(index: Int, token: SamplerEngine.VoiceToken) {
        synchronized(tokensLock) {
            val stack = tokensByIndex.getOrPut(index) { ArrayDeque() }
            stack.removeAll { it.finished }
            stack.addLast(token)
        }
    }

    /** Estado actual del bus de efectos; se adopta en el siguiente bloque de audio, sin clics. */
    fun setFxState(state: SamplerFxState) {
        synchronized(backendLock) {
            fxState = state
            backend?.engine?.fxState = state
        }
    }

    /** Picos de salida recientes (izquierdo, derecho) para el vúmetro de la UI. */
    fun outputPeaks(): Pair<Float, Float> {
        val audio = backend ?: return Pair(0f, 0f)
        return Pair(audio.engine.peakLeft, audio.engine.peakRight)
    }

    /** Carga DSP, latencia de búfer y underruns medidos en vivo en el hilo de audio. */
    fun audioStats(): AudioStats = backend?.driver?.stats() ?: AudioStats()

    /** Detiene el hilo de audio y libera el stream. Llamar desde `ViewModel.onCleared`. */
    fun releaseAll() {
        synchronized(tokensLock) { tokensByIndex.clear() }
        backend?.driver?.stop()
    }

    /**
     * Apaga cualquier nota que siguiera sonando y vacía la caché de audio
     * decodificado -- llamar siempre al cargar un instrumento nuevo, antes de
     * tocar nada de él. [DecodedSampleCache] indexa por posición de muestra,
     * y dos instrumentos distintos reutilizan las mismas posiciones: sin este
     * reseteo, la posición 0 del segundo instrumento devolvería el audio ya
     * decodificado del primero.
     */
    fun resetForNewProject() {
        val stale = synchronized(tokensLock) {
            val all = tokensByIndex.values.flatten()
            tokensByIndex.clear()
            all
        }
        stale.forEach { it.released = true }
        backend?.engine?.resetAllVoices()
        cache.clear()
    }
}
