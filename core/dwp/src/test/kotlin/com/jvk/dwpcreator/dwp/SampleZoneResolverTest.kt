package com.jvk.dwpcreator.dwp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SampleZoneResolverTest {

    private fun sample(index: Int, low: Int, root: Int, high: Int) = SampleInfo(
        index = index, name = "S$index", note = "C3", velocity = 127,
        lowKey = low, rootKey = root, highKey = high, frameCount = 1, dwpPath = ""
    )

    private val samples = listOf(
        sample(0, low = 0, root = 48, high = 49), // la más grave extiende su rango hasta la nota 0
        sample(1, low = 50, root = 50, high = 60),
        sample(2, low = 60, root = 61, high = 72) // solapa con la anterior en la nota 60
    )

    @Test
    fun findsTheSampleWhoseRangeContainsTheNote() {
        assertEquals(1, SampleZoneResolver.resolve(samples, 55)?.index)
    }

    @Test
    fun theExtendedRangeOfTheLowestSampleIsHonoured() {
        assertEquals(0, SampleZoneResolver.resolve(samples, 0)?.index)
    }

    @Test
    fun rangeBoundariesAreInclusive() {
        assertEquals(0, SampleZoneResolver.resolve(samples, 49)?.index)
        assertEquals(1, SampleZoneResolver.resolve(samples, 50)?.index)
        assertEquals(2, SampleZoneResolver.resolve(samples, 72)?.index)
    }

    @Test
    fun overlappingRangesResolveToTheFirstSample() {
        assertEquals(1, SampleZoneResolver.resolve(samples, 60)?.index)
    }

    @Test
    fun aNoteOutsideEveryRangeResolvesToNothing() {
        assertNull(SampleZoneResolver.resolve(samples, 100))
        assertNull(SampleZoneResolver.resolve(emptyList(), 60))
    }
}
