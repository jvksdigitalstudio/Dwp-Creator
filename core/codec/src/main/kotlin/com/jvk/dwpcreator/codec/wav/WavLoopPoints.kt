package com.jvk.dwpcreator.codec.wav

/**
 * Lee el bucle de un chunk `smpl` de un WAV (formato estándar de Microsoft/
 * MIDI Manufacturers Association): cabecera de 36 bytes y, a continuación,
 * `numSampleLoops` registros de 24 bytes (`id`, `type`, `start`, `end`,
 * `fraction`, `playCount`). En el archivo el `end` es **inclusivo**; aquí se
 * devuelve exclusivo ([SampleLoop]).
 *
 * Es deliberadamente tolerante: cualquier inconsistencia (chunk corto, bucle
 * fuera de la muestra, longitud nula) devuelve `null` y la muestra se
 * reproduce sin bucle, en vez de rechazar un WAV por lo demás válido.
 * Kotlin puro, sin Android.
 */
object WavLoopPoints {

    private const val HEADER_BYTES = 36
    private const val LOOP_RECORD_BYTES = 24
    private const val LOOP_COUNT_OFFSET = 28
    private const val FIRST_LOOP_OFFSET = HEADER_BYTES
    private const val START_FIELD = 8
    private const val END_FIELD = 12

    /**
     * @param bodyStart posición del primer byte del cuerpo del chunk `smpl`.
     * @param size tamaño del cuerpo.
     * @param frameCount frames de audio del archivo, para validar el bucle.
     */
    fun parse(bytes: ByteArray, bodyStart: Int, size: Int, frameCount: Int): SampleLoop? {
        if (bodyStart < 0 || size < HEADER_BYTES + LOOP_RECORD_BYTES) return null
        if (bodyStart.toLong() + size > bytes.size) return null
        val loopCount = readLE32(bytes, bodyStart + LOOP_COUNT_OFFSET)
        if (loopCount < 1) return null
        if (HEADER_BYTES.toLong() + loopCount.toLong() * LOOP_RECORD_BYTES > size) return null

        val start = readLE32(bytes, bodyStart + FIRST_LOOP_OFFSET + START_FIELD)
        val endInclusive = readLE32(bytes, bodyStart + FIRST_LOOP_OFFSET + END_FIELD)
        val endExclusive = endInclusive.toLong() + 1L
        if (start < 0 || endExclusive <= start || endExclusive > frameCount) return null
        return SampleLoop(start, endExclusive.toInt())
    }

    private fun readLE32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
