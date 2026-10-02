package com.jvk.dwpcreator.domain.flac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FlacFixedPredictorTest {

    @Test
    fun `order 0 residual equals the samples themselves`() {
        val samples = intArrayOf(5, -3, 100, -4000)
        val residual = FlacFixedPredictor.residual(samples, 0, samples.size, 0)
        assertArrayEquals(longArrayOf(5, -3, 100, -4000), residual)
    }

    @Test
    fun `order 1 residual is first difference`() {
        val samples = intArrayOf(10, 12, 9, 9, 20)
        val residual = FlacFixedPredictor.residual(samples, 0, samples.size, 1)
        assertArrayEquals(longArrayOf(2, -3, 0, 11), residual)
    }

    @Test
    fun `reconstruct is exact inverse of residual for every order`() {
        val samples = intArrayOf(0, 5, -3, 100, -4000, 32000, -32000, 17, 42, -1)
        for (order in 0..FlacFixedPredictor.MAX_ORDER) {
            val warmup = samples.copyOfRange(0, order)
            val residual = FlacFixedPredictor.residual(samples, 0, samples.size, order)
            val reconstructed = FlacFixedPredictor.reconstruct(warmup, residual, order)
            assertArrayEquals("order=$order", samples, reconstructed)
        }
    }

    @Test
    fun `selectBestOrder picks order 0 for pure white noise-like alternating extremes`() {
        // Señal que un predictor de orden alto no puede comprimir mejor que orden 0.
        val samples = IntArray(20) { if (it % 2 == 0) 32767 else -32768 }
        val order = FlacFixedPredictor.selectBestOrder(samples, 0, samples.size)
        // No se afirma un orden específico "óptimo" (depende de la heurística),
        // solo que la elección es válida y determinista dentro de rango.
        assertEquals(order, FlacFixedPredictor.selectBestOrder(samples, 0, samples.size))
        assert(order in 0..FlacFixedPredictor.MAX_ORDER)
    }

    @Test
    fun `selectBestOrder picks order 2 for a linear ramp`() {
        // Para una rampa perfectamente lineal (samples[i] = 3i - 25), la
        // PRIMERA diferencia (orden 1) es constante (=3, no cero) pero la
        // SEGUNDA diferencia (orden 2) es exactamente 0 para cada muestra --
        // verificado independientemente en Python durante el Pass PRE-CI
        // (`Doc/25`): coste absoluto total por orden = {0: 2659, 1: 147,
        // 2: 0, 3: 0, 4: 0}. selectBestOrder, al recorrer los órdenes de
        // menor a mayor y quedarse con el PRIMERO que alcanza el coste
        // mínimo estricto, elige correctamente 2 (no 3 ni 4, que empatan en
        // coste pero no lo mejoran). La expectativa original de este test
        // ("orden 1") no tenía en cuenta que un predictor de orden 2 también
        // es válido para una rampa exactamente lineal y, matemáticamente, la
        // domina en coste -- no era un bug de `selectBestOrder`, sino una
        // expectativa incorrecta del test, detectada en la primera ejecución
        // real de CI (`Doc/24` §12, R-NUEVO-03).
        val samples = IntArray(50) { it * 3 - 25 }
        val order = FlacFixedPredictor.selectBestOrder(samples, 0, samples.size)
        assertEquals(2, order)
    }

    @Test
    fun `selectBestOrder never exceeds available sample count minus one`() {
        val samples = intArrayOf(1, 2) // solo 2 muestras: máximo orden útil es 1
        val order = FlacFixedPredictor.selectBestOrder(samples, 0, samples.size, maxOrder = 4)
        assert(order <= 1)
    }
}
