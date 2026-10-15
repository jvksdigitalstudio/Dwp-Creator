package com.jvk.dwpcreator.session.project

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportProgressPacerTest {

    private val pacer = ExportProgressPacer()

    @Test
    fun spreadsTheTargetAnimationTimeAcrossTheSamples() {
        assertEquals(18L, pacer.stepDelayMs(total = 48)) // 900 ms / 48
    }

    @Test
    fun fewSamplesAreCappedSoTheBarDoesNotCrawl() {
        assertEquals(ExportProgressPacer.MAX_STEP_MS, pacer.stepDelayMs(total = 1))
        assertEquals(ExportProgressPacer.MAX_STEP_MS, pacer.stepDelayMs(total = 0))
    }

    @Test
    fun manySamplesKeepAMinimumVisibleStep() {
        assertEquals(ExportProgressPacer.MIN_STEP_MS, pacer.stepDelayMs(total = 5000))
    }
}
