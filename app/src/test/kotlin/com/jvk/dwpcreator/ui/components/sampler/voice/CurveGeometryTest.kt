package com.jvk.dwpcreator.ui.components.sampler.voice

import com.jvk.dwpcreator.sampler.api.state.AdsrState
import com.jvk.dwpcreator.sampler.api.state.LfoShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveGeometryTest {

    @Test
    fun adsrCurveStartsAtRestPeaksAndReturnsToRest() {
        val points = AdsrCurveGeometry.points(AdsrState(10f, 200f, 0.4f, 300f))
        assertEquals(CurvePoint(0f, 0f), points.first())
        assertEquals(CurvePoint(1f, 0f), points.last())
        assertEquals(1f, points.maxOf { it.y }, 0f)
        assertTrue(points.all { it.y in 0f..1f })
    }

    @Test
    fun adsrCurveTimeAxisNeverGoesBackwards() {
        for (adsr in listOf(AdsrState(3f, 20_000f, 1f, 15f), AdsrState(10_000f, 10f, 0f, 20_000f), AdsrState(1f, 400f, 0.5f, 5f))) {
            val points = AdsrCurveGeometry.points(adsr)
            for (i in 1 until points.size) assertTrue("$adsr en $i", points[i].x >= points[i - 1].x)
            assertTrue(points.all { it.x in 0f..1f })
        }
    }

    @Test
    fun adsrCurveHoldsAtTheSustainLevel() {
        val points = AdsrCurveGeometry.points(AdsrState(10f, 100f, 0.4f, 100f))
        assertTrue(points.any { it.y == 0.4f })
        // El tramo de sostenido es plano: dos puntos consecutivos al nivel del sostenido.
        assertTrue(points.zipWithNext().any { (a, b) -> a.y == 0.4f && b.y == 0.4f && b.x > a.x })
    }

    @Test
    fun adsrCurveWithZeroSustainDecaysToTheFloor() {
        val points = AdsrCurveGeometry.points(AdsrState(5f, 100f, 0f, 100f))
        assertTrue(points.zipWithNext().any { (a, b) -> a.y == 0f && b.y == 0f && b.x > a.x })
    }

    @Test
    fun adsrCurveSkipsTheDecayWhenSustainIsFull() {
        val full = AdsrCurveGeometry.points(AdsrState(10f, 20_000f, 1f, 100f))
        // Con sostenido pleno el decay no ocupa ancho: tras el pico, la curva se queda en 1.
        assertEquals(1f, full[1].y, 0f)
        assertEquals(1f, full[2].y, 0f)
    }

    @Test
    fun adsrAxisIsSquareRootCompressedSoALongReleaseDoesNotCrushTheAttack() {
        val points = AdsrCurveGeometry.points(AdsrState(100f, 100f, 0.5f, 10_000f))
        val attackEnd = points[1].x
        assertTrue("el ataque debe ser visible: $attackEnd", attackEnd > 0.05f)
    }

    @Test
    fun lfoPreviewHasTheExpectedPointCountAndRange() {
        val points = LfoPreviewGeometry.points(LfoShape.SINE, 0f, cycles = 2, samplesPerCycle = 64)
        assertEquals(129, points.size)
        assertEquals(0f, points.first().x, 0f)
        assertEquals(1f, points.last().x, 0f)
        assertTrue(points.all { it.y in -1.001f..1.001f })
    }

    @Test
    fun lfoPreviewShapesStartAsTheirDefinitionSays() {
        assertEquals(0f, LfoPreviewGeometry.points(LfoShape.SINE, 0f).first().y, 0.002f)
        assertEquals(1f, LfoPreviewGeometry.points(LfoShape.SAW, 0f).first().y, 1e-6f)
        assertEquals(1f, LfoPreviewGeometry.points(LfoShape.SQUARE, 0f).first().y, 0f)
        assertEquals(1f, LfoPreviewGeometry.points(LfoShape.SINE, 0.25f).first().y, 0.002f)
    }

    @Test
    fun squarePreviewOnlyTakesTheTwoExtremes() {
        val values = LfoPreviewGeometry.points(LfoShape.SQUARE, 0f).map { it.y }.toSet()
        assertEquals(setOf(1f, -1f), values)
    }

    @Test
    fun randomShapesAreStableBetweenCalls() {
        for (shape in listOf(LfoShape.SAMPLE_HOLD, LfoShape.SMOOTH_RANDOM)) {
            assertEquals(LfoPreviewGeometry.points(shape, 0f), LfoPreviewGeometry.points(shape, 0f))
        }
        assertTrue(LfoPreviewGeometry.points(LfoShape.SAMPLE_HOLD, 0f).map { it.y }.toSet().size > 1)
    }
}
