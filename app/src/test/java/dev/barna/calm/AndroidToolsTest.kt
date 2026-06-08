package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidToolsTest {
    @Test
    fun friendlyPackageNameUsesReadableFinalSegment() {
        assertEquals("Outlook", friendlyPackageName("com.microsoft.office.outlook"))
        assertEquals("Youtube Music", friendlyPackageName("app.revanced.android.apps.youtube_music"))
        assertEquals("My App", friendlyPackageName("dev.barna.myApp"))
    }

    @Test
    fun iconBitmapDimensionUsesMinWhenIntrinsicSizeIsNegative() {
        // AdaptiveIconDrawable.intrinsicWidth returns -1 on most Android implementations
        assertEquals(MIN_ICON_BITMAP_SIZE, iconBitmapDimension(-1))
    }

    @Test
    fun iconBitmapDimensionUsesMinWhenIntrinsicSizeIsZero() {
        assertEquals(MIN_ICON_BITMAP_SIZE, iconBitmapDimension(0))
    }

    @Test
    fun iconBitmapDimensionClampsSmallLegacyIconsToMin() {
        // Legacy hdpi icons (intrinsicWidth ~192px) scaled to ~520px card backgrounds = 2.7× blur.
        // Must clamp to MIN so the upscale factor stays ≤ 1.
        assertEquals(MIN_ICON_BITMAP_SIZE, iconBitmapDimension(192))
    }

    @Test
    fun iconBitmapDimensionPreservesIntrinsicSizesLargerThanMin() {
        assertEquals(768, iconBitmapDimension(768))
        assertEquals(1024, iconBitmapDimension(1024))
    }

    @Test
    fun minIconBitmapSizeIsAtLeast512() {
        // Card backgrounds scale to ~520px; source bitmap must be at least this size to avoid upscaling.
        assertTrue(MIN_ICON_BITMAP_SIZE >= 512)
    }
}
