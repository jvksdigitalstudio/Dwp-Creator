package com.jvk.dwpcreator.session.fx

import com.jvk.dwpcreator.sampler.api.params.SamplerParams
import com.jvk.dwpcreator.sampler.api.state.SamplerFxState
import com.jvk.dwpcreator.session.testing.FakePlayback
import com.jvk.dwpcreator.session.testing.RecordingFxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FxControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val playback = FakePlayback()
    private val store = RecordingFxStore()
    private val fx = FxController(scope, playback, store)

    private val param = SamplerParams.params.first()

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun theStoredStateIsHandedToTheEngineOnCreation() {
        assertEquals(1, playback.fxStates.size)
        assertEquals(store.load(), playback.fxStates.single())
    }

    @Test
    fun settingAParameterUpdatesTheStateAndTheEngineImmediately() {
        val target = param.scale.max

        fx.setParam(param, target)

        val expected = param.write(SamplerFxState(), target)
        assertEquals(expected, fx.state.value)
        assertEquals(expected, playback.fxStates.last())
    }

    @Test
    fun resettingAParameterRestoresItsDefault() {
        fx.setParam(param, param.scale.max)

        fx.resetParam(param)

        assertEquals(param.scale.default, param.read(fx.state.value), 1e-4f)
    }

    @Test
    fun nothingIsPersistedUntilTheDebounceElapsesOrFlushIsCalled() {
        fx.setParam(param, param.scale.max)

        assertNull(store.saved)

        fx.flush()

        assertNotNull(store.saved)
        assertEquals(fx.state.value, store.saved)
    }
}
