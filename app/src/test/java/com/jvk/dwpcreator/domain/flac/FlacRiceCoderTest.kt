package com.jvk.dwpcreator.domain.flac

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class FlacRiceCoderTest {

    private fun roundTrip(residual: LongArray): LongArray {
        val bw = FlacBitWriter()
        FlacRiceCoder.encode(bw, residual)
        bw.byteAlign()
        val br = FlacBitReader(bw.toByteArray())
        return FlacRiceCoder.decode(br, residual.size)
    }

    @Test
    fun `round trips all zeros`() {
        val residual = LongArray(100) { 0L }
        assertArrayEquals(residual, roundTrip(residual))
    }

    @Test
    fun `round trips small positive and negative values`() {
        val residual = longArrayOf(0, 1, -1, 2, -2, 3, -3, 100, -100)
        assertArrayEquals(residual, roundTrip(residual))
    }

    @Test
    fun `round trips random small residuals (typical case)`() {
        val rnd = Random(7)
        val residual = LongArray(2000) { rnd.nextInt(-50, 50).toLong() }
        assertArrayEquals(residual, roundTrip(residual))
    }

    @Test
    fun `round trips pathological large alternating residuals (forces escape path)`() {
        // 500 millones: sigue siendo ~18x el residual máximo real posible con el
        // audio de 24-bit que soporta este proyecto (~28 bits en el peor caso de
        // un predictor FIXED de orden 4), y sigue forzando la vía de escape frente
        // a Rice coding -- pero cabe en los 31 bits que admite el campo de ancho
        // de esa vía (ver FlacRiceCoder.MAX_UNENCODED_BIT_WIDTH). El valor
        // original (±2.000.000.000) requería 32 bits, más de lo que ese campo de
        // 5 bits puede representar en absoluto -- no un bug de esta implementación,
        // sino un límite real de la especificación de FLAC para RESIDUAL_CODING_METHOD=0
        // (detectado en el Pass PRE-CI, `Doc/25`, mediante la primera ejecución real de CI).
        val residual = LongArray(50) { if (it % 2 == 0) 500_000_000L else -500_000_000L }
        assertArrayEquals(residual, roundTrip(residual))
    }

    @Test
    fun `round trips a single huge outlier among small values`() {
        val residual = LongArray(200) { 1L }
        residual[100] = 1_000_000_000L
        assertArrayEquals(residual, roundTrip(residual))
    }

    @Test
    fun `rejects residuals that need more bits than the escape width field can represent`() {
        // Int.MIN_VALUE requiere 32 bits en complemento a dos (y esta
        // implementación reserva conservadoramente 1 bit extra de margen,
        // 33 en total) para representarse sin pérdida -- más de los 31 bits
        // que admite el campo de 5 bits de la vía de escape de FLAC
        // (RESIDUAL_CODING_METHOD=0). Esto nunca ocurre con el audio de
        // 8/16/24-bit que soporta este proyecto (Doc/23 §15; peor caso real
        // ~28 bits), así que el codificador debe rechazar explícitamente
        // esta entrada en vez de truncar el campo en silencio y corromper
        // el stream -- exactamente el bug real detectado en el Pass PRE-CI
        // (`Doc/25`) mediante la primera ejecución real de CI, donde este
        // test (antes llamado "round trips extreme Int boundary values" y
        // esperando un round-trip que la especificación de FLAC no permite
        // representar) fallaba silenciosamente en vez de fallar con un error
        // claro.
        val residual = longArrayOf(Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(), 0L)
        assertThrows(FlacEncodingException::class.java) { roundTrip(residual) }
    }

    @Test
    fun `round trips a single-element residual`() {
        val residual = longArrayOf(-12345L)
        assertArrayEquals(residual, roundTrip(residual))
    }
}
