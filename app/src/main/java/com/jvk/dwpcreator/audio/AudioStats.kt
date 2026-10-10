package com.jvk.dwpcreator.audio

/**
 * Métricas en vivo de la salida de audio, medidas por [AudioOutputDriver] en el
 * propio hilo de audio del dispositivo (no estimadas):
 *
 * - [dspLoadPercent]: tiempo medio que tarda el motor en generar un bloque, como
 *   porcentaje del tiempo real disponible para ese bloque (media móvil). Por encima
 *   de ~70 % queda poco margen ante una interrupción del sistema; 100 % = *xruns*.
 * - [worstBlockMs]: el bloque más lento de la última ventana (~1,4 s). Si se acerca
 *   al periodo del bloque ([blockMs]), el siguiente fallo de planificación crujirá.
 * - [blockMs]: duración de un bloque (ráfaga nativa) -- el techo de [worstBlockMs].
 * - [bufferMs]: tamaño actual del búfer del stream = latencia de salida aproximada
 *   que añade la app (sin contar la del hardware/Bluetooth).
 * - [underruns]: cuántas veces el hardware se quedó sin datos desde que arrancó la
 *   app (acumulado entre reconstrucciones del stream).
 */
data class AudioStats(
    val dspLoadPercent: Float = 0f,
    val worstBlockMs: Float = 0f,
    val blockMs: Float = 0f,
    val bufferMs: Float = 0f,
    val underruns: Int = 0
)
