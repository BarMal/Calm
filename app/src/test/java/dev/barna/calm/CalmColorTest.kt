package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class CalmColorTest {
    @Test
    fun clamp01BoundsValuesToUnitRange() {
        assertEquals(0f, CalmColor.clamp01(-0.5f), 0f)
        assertEquals(0f, CalmColor.clamp01(0f), 0f)
        assertEquals(0.42f, CalmColor.clamp01(0.42f), 0f)
        assertEquals(1f, CalmColor.clamp01(1f), 0f)
        assertEquals(1f, CalmColor.clamp01(1.5f), 0f)
    }

    @Test
    fun lerpClampsAmountBeforeInterpolating() {
        assertEquals(10f, CalmColor.lerp(10f, 20f, -1f), 0f)
        assertEquals(15f, CalmColor.lerp(10f, 20f, 0.5f), 0f)
        assertEquals(20f, CalmColor.lerp(10f, 20f, 2f), 0f)
    }

    @Test
    fun blendReturnsStartAndEndAtBounds() {
        val start = 0xff102030.toInt()
        val end = 0xff405060.toInt()

        assertEquals(start, CalmColor.blend(start, end, 0f))
        assertEquals(end, CalmColor.blend(start, end, 1f))
    }

    @Test
    fun blendInterpolatesRgbChannels() {
        val start = 0xff000000.toInt()
        val end = 0xff204060.toInt()

        assertEquals(0xff102030.toInt(), CalmColor.blend(start, end, 0.5f))
    }
}
