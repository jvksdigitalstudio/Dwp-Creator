package com.jvk.dwpcreator.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelaySyncTest {

    private val max = StereoDelayEffect.MAX_DELAY_SECONDS * 1000f - 10f

    private fun index(name: String) = DelaySync.NAMES.indexOf(name).also { assertTrue(name, it >= 0) }

    @Test
    fun straightTripletAndDottedDivisionsAt120Bpm() {
        assertEquals(500f, DelaySync.resolveMs(120f, index("1/4"), max), 0.01f)
        assertEquals(250f, DelaySync.resolveMs(120f, index("1/8"), max), 0.01f)
        assertEquals(375f, DelaySync.resolveMs(120f, index("1/8."), max), 0.01f)
        assertEquals(500f / 3f, DelaySync.resolveMs(120f, index("1/8T"), max), 0.01f)
        // 1/1 a 120 BPM = 2000 ms excede el máximo (1990 ms) -> se pliega a 1000 ms (1/2).
        assertEquals(1000f, DelaySync.resolveMs(120f, index("1/1"), max), 0.01f)
    }

    @Test
    fun divisionsThatExceedTheMaximumFoldDownByOctaves() {
        // 1/1 a 40 BPM = 6000 ms -> 3000 -> 1500 ms (<= 1990 ms).
        assertEquals(1500f, DelaySync.resolveMs(40f, index("1/1"), max), 0.01f)
        for (i in 0..DelaySync.LAST_INDEX) {
            for (bpm in listOf(20f, 40f, 90f, 240f, 400f)) {
                val ms = DelaySync.resolveMs(bpm, i, max)
                assertTrue("bpm=$bpm div=$i -> $ms", ms in 1f..max)
            }
        }
    }

    @Test
    fun effectiveTimeIsManualWithoutSyncAndRhythmicWithIt() {
        val manual = SamplerFxState(delayTimeMs = 333f, delaySync = false)
        assertEquals(333f, DelaySync.effectiveTimeMs(manual), 0f)
        val synced = SamplerFxState(delayTimeMs = 333f, delaySync = true, delayBpm = 100f, delayDivision = index("1/4"))
        assertEquals(600f, DelaySync.effectiveTimeMs(synced), 0.01f)
    }

    @Test
    fun namesAndBeatsStayInStep() {
        assertEquals(10, DelaySync.NAMES.size)
        assertEquals(DelaySync.NAMES.size - 1, DelaySync.LAST_INDEX)
        assertEquals("1/8", DelaySync.name(DelaySync.DEFAULT_INDEX))
        assertEquals("1/16T", DelaySync.name(-4))
        assertEquals("1/1", DelaySync.name(99))
    }
}
