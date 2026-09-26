package com.jvk.dwpcreator.domain.flac

/**
 * Predictores fijos (FIXED, órdenes 0-4) de FLAC — las cinco fórmulas de
 * diferenciación polinómica definidas por la especificación pública de
 * FLAC para SUBFRAME_FIXED. No se implementa LPC (predicción lineal
 * adaptativa): los predictores fijos producen FLAC 100% válido y
 * perfectamente reversible, simplemente con menor tasa de compresión que
 * LPC. Ver Doc/24 para la justificación de esta decisión (prioridad de
 * corrección/verificabilidad sobre optimización, en línea con la Sección 9
 * del prompt maestro de esta fase: "no implementes características que no
 * sean necesarias sin justificar su inclusión").
 */
object FlacFixedPredictor {

    const val MAX_ORDER = 4

    /**
     * Calcula el residual de orden [order] para `samples[from, until)`.
     * Los primeros [order] valores de `samples` en ese rango son las
     * muestras de calentamiento (warm-up) y no se incluyen en el residual
     * devuelto -- el residual cubre `samples[from+order, until)`.
     */
    fun residual(samples: IntArray, from: Int, until: Int, order: Int): LongArray {
        val n = until - from
        require(order in 0..MAX_ORDER) { "order=$order fuera de rango 0..$MAX_ORDER" }
        require(n > order) { "No hay suficientes muestras ($n) para calentar un predictor de orden $order" }
        val out = LongArray(n - order)
        for (i in order until n) {
            val s0 = samples[from + i].toLong()
            val value = when (order) {
                0 -> s0
                1 -> s0 - samples[from + i - 1]
                2 -> s0 - 2L * samples[from + i - 1] + samples[from + i - 2]
                3 -> s0 - 3L * samples[from + i - 1] + 3L * samples[from + i - 2] - samples[from + i - 3]
                4 -> s0 - 4L * samples[from + i - 1] + 6L * samples[from + i - 2] - 4L * samples[from + i - 3] + samples[from + i - 4]
                else -> error("unreachable")
            }
            out[i - order] = value
        }
        return out
    }

    /**
     * Reconstruye las muestras originales a partir de las [order] muestras
     * de calentamiento ([warmup], tamaño exacto [order]) y el [residual].
     * Inversa exacta de [residual]; usado por [FlacDecoder] para verificar
     * round-trip.
     */
    fun reconstruct(warmup: IntArray, residual: LongArray, order: Int): IntArray {
        require(warmup.size == order) { "warmup.size=${warmup.size} debe ser igual a order=$order" }
        val out = IntArray(order + residual.size)
        System.arraycopy(warmup, 0, out, 0, order)
        for (i in residual.indices) {
            val idx = order + i
            val value = when (order) {
                0 -> residual[i]
                1 -> residual[i] + out[idx - 1]
                2 -> residual[i] + 2L * out[idx - 1] - out[idx - 2]
                3 -> residual[i] + 3L * out[idx - 1] - 3L * out[idx - 2] + out[idx - 3]
                4 -> residual[i] + 4L * out[idx - 1] - 6L * out[idx - 2] + 4L * out[idx - 3] - out[idx - 4]
                else -> error("unreachable")
            }
            out[idx] = value.toInt()
        }
        return out
    }

    /**
     * Elige heurísticamente el orden 0..min(4, maxOrder) que minimiza la
     * suma de valores absolutos del residual (proxy estándar y barato del
     * coste real en bits Rice, usado también por encoders FLAC de
     * referencia para predictores fijos). Devuelve el orden elegido.
     */
    fun selectBestOrder(samples: IntArray, from: Int, until: Int, maxOrder: Int = MAX_ORDER): Int {
        val n = until - from
        var bestOrder = 0
        var bestCost = Long.MAX_VALUE
        val cap = minOf(maxOrder, MAX_ORDER, n - 1).coerceAtLeast(0)
        for (order in 0..cap) {
            if (n <= order) continue
            val res = residual(samples, from, until, order)
            var cost = 0L
            for (v in res) cost += if (v < 0) -v else v
            if (cost < bestCost) {
                bestCost = cost
                bestOrder = order
            }
        }
        return bestOrder
    }
}
