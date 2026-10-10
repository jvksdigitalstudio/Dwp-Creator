package com.jvk.dwpcreator.audio.dsp

/**
 * Línea de retardo circular de un canal, con lectura fraccionaria
 * lineal ([read]) o cúbica ([readCubic]). Tamaño potencia de dos para indexar con máscara en
 * vez de módulo (sin ramas ni divisiones en el camino caliente del hilo de
 * audio). **No asigna memoria** después de construirse.
 *
 * Convención de lectura: [read] con `delaySamples = 1` devuelve la última
 * muestra escrita, `2` la anterior, etc. Por eso el patrón correcto en un
 * bucle de realimentación es "leer, luego escribir" y el retardo mínimo útil
 * es 1.
 */
internal class DelayLine(capacitySamples: Int) {

    private val size = Dsp.nextPowerOfTwo(capacitySamples.coerceAtLeast(4) + 2)
    private val mask = size - 1
    private val buffer = FloatArray(size)
    private var writeIndex = 0

    /** Retardo máximo que [read] puede entregar sin envolverse sobre sí mismo. */
    val maxDelay: Float get() = (size - 2).toFloat()

    fun write(sample: Float) {
        buffer[writeIndex] = sample
        writeIndex = (writeIndex + 1) and mask
    }

    fun read(delaySamples: Float): Float {
        val d = delaySamples.coerceIn(1f, maxDelay)
        val whole = d.toInt()
        val frac = d - whole
        val newer = buffer[(writeIndex - whole) and mask]
        val older = buffer[(writeIndex - whole - 1) and mask]
        return newer + (older - newer) * frac
    }

    /**
     * Lectura fraccionaria por **interpolación cúbica de Hermite**
     * (Catmull-Rom, 4 puntos). Imprescindible cuando el retardo se *modula*
     * (chorus, wow/flutter de la cinta, reverb): la interpolación lineal
     * actúa como un pasa-bajos que cambia con la parte fraccionaria y
     * "respira" audiblemente; la cúbica conserva el brillo y la fase. Misma
     * convención que [read]. El retardo mínimo útil es 2 (necesita una
     * muestra más reciente que la pedida).
     */
    fun readCubic(delaySamples: Float): Float {
        val d = delaySamples.coerceIn(2f, (size - 3).toFloat())
        val whole = d.toInt()
        val t = d - whole
        val base = writeIndex - whole
        val y0 = buffer[(base + 1) and mask]
        val y1 = buffer[base and mask]
        val y2 = buffer[(base - 1) and mask]
        val y3 = buffer[(base - 2) and mask]
        val c1 = 0.5f * (y2 - y0)
        val c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
        val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
        return ((c3 * t + c2) * t + c1) * t + y1
    }

    fun clear() {
        buffer.fill(0f)
        writeIndex = 0
    }
}

/**
 * Difusor *all-pass* de Schroeder de longitud entera fija. Forma
 * canónica: `v[n] = x[n] + g·v[n-N]`, `y[n] = v[n-N] - g·v[n]`.
 * Magnitud de respuesta plana (todo-paso) para `|g| < 1`: espesa la densidad
 * de ecos de la reverb sin colorear el espectro.
 */
internal class AllpassDiffuser(lengthSamples: Int) {
    private val length = lengthSamples.coerceAtLeast(2)
    private val buffer = FloatArray(length)
    private var index = 0

    fun process(x: Float, g: Float): Float {
        val delayed = buffer[index]
        val v = Dsp.flush(x + g * delayed)
        buffer[index] = v
        index++
        if (index >= length) index = 0
        return delayed - g * v
    }

    fun clear() {
        buffer.fill(0f)
        index = 0
    }
}

/** Pasa-bajos de un polo (6 dB/oct). */
internal class OnePoleLowPass {
    private var z = 0f

    fun process(x: Float, coef: Float): Float {
        z = Dsp.flush(z + coef * (x - z))
        return z
    }

    fun clear() {
        z = 0f
    }
}

/** Pasa-altos de un polo: la entrada menos su propio pasa-bajos. */
internal class OnePoleHighPass {
    private val low = OnePoleLowPass()

    fun process(x: Float, coef: Float): Float = x - low.process(x, coef)

    fun clear() = low.clear()
}
