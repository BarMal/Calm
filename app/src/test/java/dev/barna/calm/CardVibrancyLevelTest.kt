package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class CardVibrancyLevelTest {

    @Test
    fun defaultVibrancyLeavesAlphaUnchanged() {
        // vibrancy=50: baseAlpha * 2 * (100-50) / 100 = baseAlpha * 1.0
        val color = 0x520F0F14.toInt() // CalmTheme.GLASS: alpha=82, r=15, g=15, b=20
        val result = CardVibrancyLevel.applyTo(color, 50)
        assertEquals(82, (result ushr 24) and 0xFF)
    }

    @Test
    fun zeroVibrancyDoublesAlpha() {
        // vibrancy=0: baseAlpha * 2 * 100 / 100 = baseAlpha * 2, clamped at 255
        val color = 0x520F0F14.toInt() // alpha=82
        val result = CardVibrancyLevel.applyTo(color, 0)
        assertEquals(164, (result ushr 24) and 0xFF)
    }

    @Test
    fun maxVibrancyZerosAlpha() {
        // vibrancy=100: baseAlpha * 2 * 0 / 100 = 0
        val color = 0x520F0F14.toInt()
        val result = CardVibrancyLevel.applyTo(color, 100)
        assertEquals(0, (result ushr 24) and 0xFF)
    }

    @Test
    fun alphaIsClampedAt255() {
        // Full alpha=255, vibrancy=0 → 255*2*100/100 = 510, clamped to 255
        val color = 0xFF808080.toInt()
        val result = CardVibrancyLevel.applyTo(color, 0)
        assertEquals(255, (result ushr 24) and 0xFF)
    }

    @Test
    fun rgbChannelsArePreserved() {
        val r = 15; val g = 20; val b = 200
        val color = (0x40 shl 24) or (r shl 16) or (g shl 8) or b
        val result = CardVibrancyLevel.applyTo(color, 50)
        assertEquals(r, (result ushr 16) and 0xFF)
        assertEquals(g, (result ushr 8) and 0xFF)
        assertEquals(b, result and 0xFF)
    }

    @Test
    fun outOfRangeVibrancyIsCoerced() {
        val color = 0x520F0F14.toInt() // alpha=82
        // vibrancy=-10 should be treated as 0: alpha doubled
        val resultLow = CardVibrancyLevel.applyTo(color, -10)
        assertEquals(164, (resultLow ushr 24) and 0xFF)
        // vibrancy=110 should be treated as 100: alpha zeroed
        val resultHigh = CardVibrancyLevel.applyTo(color, 110)
        assertEquals(0, (resultHigh ushr 24) and 0xFF)
    }

    @Test
    fun midpointVibrancy25IncreasesOpacity() {
        // vibrancy=25: baseAlpha * 2 * 75 / 100 = baseAlpha * 1.5
        val color = 0x640F0F14.toInt() // alpha=100
        val result = CardVibrancyLevel.applyTo(color, 25)
        assertEquals(150, (result ushr 24) and 0xFF)
    }
}
