package com.jvk.dwpcreator.domain.audio

/**
 * Caché LRU de audio ya decodificado y convertido a 16-bit PCM, indexado por
 * la posición de la muestra (la misma clave estable que usa
 * [com.jvk.dwpcreator.domain.io.LoadedProject.audioByIndex]).
 *
 * **Por qué existe (el motivo real del retardo al tocar una tecla).** Antes
 * de esta clase, cada pulsación -- incluida cada nota de un acorde -- volvía
 * a hacer, desde cero, sobre los mismos bytes de siempre:
 *  1. `WavDecoder.decode()`: reparsear todos los chunks RIFF y copiar el
 *     chunk `data` completo (`copyOfRange`, varios MB para una muestra
 *     típica de DirectWave);
 *  2. `PcmConverter.*ToInt16()`: recorrer sample a sample para convertir a
 *     16-bit (el caso más caro, float32 estéreo, es literalmente el peor:
 *     máxima precisión de origen, mayor coste de conversión).
 * Ese trabajo es idéntico cada vez que se vuelve a tocar la misma tecla --
 * pura repetición evitable. Esta caché lo hace una sola vez por muestra (o,
 * si `prewarm` llegó primero, ni siquiera en la primera pulsación) y
 * devuelve el resultado ya listo para escribirse en un `AudioTrack`.
 *
 * **Por qué está acotada en bytes, no en número de entradas** ([maxTotalBytes],
 * por defecto [DEFAULT_MAX_TOTAL_BYTES]): las muestras de un mismo
 * instrumento pueden variar mucho de duración, y el objetivo real es un
 * límite de memoria, no un número arbitrario de "muestras calientes". El
 * proyecto ya mantiene en memoria el WAV original de cada muestra
 * (`LoadedProject.audioByIndex`, con `android:largeHeap` explícitamente
 * activado por ese motivo -- ver `Doc/29`); este límite existe para que la
 * caché de reproducción no duplique ese presupuesto sin control en un
 * instrumento muy grande. Cuando se supera, se descarta primero la entrada
 * usada hace más tiempo (orden de acceso real, vía `LinkedHashMap(accessOrder
 * = true)`), no la más antigua por posición -- así una tecla que se sigue
 * tocando nunca se expulsa a sí misma solo por haber otras entradas más
 * recientes en la caché.
 *
 * Sin ninguna dependencia de Android -- Kotlin/JVM puro, igual que
 * [WavDecoder]/[PcmConverter], y por la misma razón: testeable directamente.
 */
class DecodedSampleCache(private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES) {

    /** Resultado listo para reproducir: PCM de 16 bits + el formato con el que hay que abrir el AudioTrack. */
    data class CachedSample(
        val pcm16: ByteArray,
        val sampleRateHz: Int,
        val channelCount: Int
    )

    companion object {
        /** ~48 MB: suficiente para mantener "calientes" varias decenas de muestras típicas sin descontrolar la memoria total de la app. */
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 48L * 1024 * 1024
    }

    // accessOrder=true reordena la entrada al frente en cada get() -- es lo
    // que convierte "iterar en orden de inserción" en "iterar en orden LRU"
    // sin mantener ninguna estructura adicional.
    private val entries = LinkedHashMap<Int, CachedSample>(16, 0.75f, true)
    private var totalBytes = 0L

    /**
     * Devuelve el PCM de la muestra [index], de la caché si ya estaba, o
     * decodificándolo y convirtiéndolo desde [wavBytes] si no. `null` si
     * [wavBytes] no es un WAV soportado por [WavDecoder] o si tiene un
     * número de canales fuera de 1..2 -- mismo criterio de "silenciar en vez
     * de crashear" que ya usaba `SamplePlayer.play()` antes de esta clase.
     *
     * La decodificación ocurre **fuera** de cualquier bloqueo de esta
     * instancia: dos pulsaciones simultáneas sobre índices distintos nunca
     * se esperan entre sí. Si, en una carrera muy poco probable, dos hilos
     * piden el mismo índice no cacheado a la vez, ambos decodifican y el
     * segundo en llegar simplemente sobreescribe la entrada del primero --
     * trabajo duplicado en ese caso raro, nunca un resultado incorrecto.
     */
    fun get(index: Int, wavBytes: ByteArray): CachedSample? {
        synchronized(this) { entries[index]?.let { return it } }

        val decoded = try {
            WavDecoder.decode(wavBytes)
        } catch (e: Exception) {
            return null
        }
        if (decoded.frameCount <= 0 || decoded.channelCount !in 1..2) return null
        val pcm16 = convertToInt16(decoded)

        val cached = CachedSample(pcm16, decoded.sampleRateHz, decoded.channelCount)
        synchronized(this) { store(index, cached) }
        return cached
    }

    /** `true` si añadir una muestra más probablemente ya no cabría sin expulsar otra -- usado por el precalentamiento para saber cuándo parar. */
    fun isFull(): Boolean = synchronized(this) { totalBytes >= maxTotalBytes }

    @Synchronized
    fun clear() {
        entries.clear()
        totalBytes = 0L
    }

    private fun store(index: Int, sample: CachedSample) {
        entries.remove(index)?.let { totalBytes -= it.pcm16.size }
        entries[index] = sample
        totalBytes += sample.pcm16.size

        val it = entries.entries.iterator() // orden LRU: el primero es el usado hace más tiempo
        while (totalBytes > maxTotalBytes && it.hasNext()) {
            val evicted = it.next()
            if (evicted.key == index) continue // nunca expulsar la entrada que se acaba de pedir
            it.remove()
            totalBytes -= evicted.value.pcm16.size
        }
    }

    private fun convertToInt16(decoded: WavDecoder.DecodedWav): ByteArray = when (decoded.format) {
        WavDecoder.SampleFormat.PCM_16 -> decoded.pcmData
        WavDecoder.SampleFormat.PCM_8 -> PcmConverter.uint8ToInt16(decoded.pcmData)
        WavDecoder.SampleFormat.PCM_24 -> PcmConverter.int24ToInt16(decoded.pcmData)
        WavDecoder.SampleFormat.PCM_32 -> PcmConverter.int32ToInt16(decoded.pcmData)
        WavDecoder.SampleFormat.FLOAT_32 -> PcmConverter.floatToInt16(decoded.pcmData)
    }
}
