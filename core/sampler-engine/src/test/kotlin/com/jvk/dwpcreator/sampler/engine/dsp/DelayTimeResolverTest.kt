package com.jvk.dwpcreator.sampler.engine.dsp

import com.jvk.dwpcreator.sampler.api.rhythm.DelaySync
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import org.junit.Assert.assertEquals
import org.junit.Test

class DelayTimeResolverTest {

    @Test
    fun effectiveTimeIsManualWithoutSyncAndRhythmicWithIt() {
        val manual = SamplerFxState(delayTimeMs = 333f, delaySync = false)
        assertEquals(333f, DelayTimeResolver.effectiveTimeMs(manual), 0f)
        val synced = SamplerFxState(delayTimeMs = 333f, delaySync = true, delayBpm = 100f, delayDivision = DelaySync.NAMES.indexOf("1/4"))
        assertEquals(600f, DelayTimeResolver.effectiveTimeMs(synced), 0.01f)
    }
}
