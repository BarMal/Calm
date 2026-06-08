package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmThemeTest {
    @Test
    fun surfaceIsFullyOpaque() {
        assertEquals(255, (CalmTheme.SURFACE ushr 24) and 0xFF)
    }

    @Test
    fun surfaceContainerIsFullyOpaque() {
        assertEquals(255, (CalmTheme.SURFACE_CONTAINER ushr 24) and 0xFF)
    }

    @Test
    fun surfaceIsDark() {
        val r = ((CalmTheme.SURFACE shr 16) and 0xFF) / 255.0
        val g = ((CalmTheme.SURFACE shr 8) and 0xFF) / 255.0
        val b = (CalmTheme.SURFACE and 0xFF) / 255.0
        // Relative luminance via sRGB linearisation
        fun linearise(c: Double) = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * linearise(r) + 0.7152 * linearise(g) + 0.0722 * linearise(b)
        assertTrue("Expected luminance < 0.15 but was $luminance", luminance < 0.15)
    }

    @Test
    fun surfaceContainerIsDarkerThanInk() {
        fun brightness(color: Int): Int =
            ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)
        assertTrue(
            "SURFACE_CONTAINER should be darker than INK",
            brightness(CalmTheme.SURFACE_CONTAINER) < brightness(CalmTheme.INK)
        )
    }
}
