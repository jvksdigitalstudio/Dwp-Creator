package com.jvk.dwpcreator.sampler.engine.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class GlideTest {

    @Test
    fun travelsLinearlyToZeroInTheRequestedFrames() {
        val glide = Glide()
        glide.begin(12f, 4_800f)
        glide.advance(2_400)
        assertEquals(6f, glide.offsetSemitones, 0.001f)
        glide.advance(2_400)
        assertEquals(0f, glide.offsetSemitones, 0f)
    }

    @Test
    fun worksInBothDirections() {
        val glide = Glide()
        glide.begin(-7f, 1_000f)
        glide.advance(500)
        assertEquals(-3.5f, glide.offsetSemitones, 0.001f)
        glide.advance(10_000)
        assertEquals(0f, glide.offsetSemitones, 0f)
    }

    @Test
    fun zeroTimeJumpsStraightToTheTarget() {
        val glide = Glide()
        glide.begin(5f, 0f)
        assertEquals(0f, glide.offsetSemitones, 0f)
    }

    @Test
    fun resetCancelsAnyGlideInProgress() {
        val glide = Glide()
        glide.begin(5f, 1_000f)
        glide.reset()
        glide.advance(100)
        assertEquals(0f, glide.offsetSemitones, 0f)
    }

    @Test
    fun aNewBeginReplacesTheCurrentTravel() {
        val glide = Glide()
        glide.begin(10f, 1_000f)
        glide.advance(500)
        glide.begin(glide.offsetSemitones + 4f, 1_000f)
        assertEquals(9f, glide.offsetSemitones, 0.001f)
        glide.advance(1_000)
        assertEquals(0f, glide.offsetSemitones, 0f)
    }
}
