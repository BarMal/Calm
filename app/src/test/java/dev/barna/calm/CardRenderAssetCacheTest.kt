package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardRenderAssetCacheTest {
    @Test
    fun reusesRenderDataForSameIconAndStyleConfiguration() {
        val cache = CardRenderAssetCache()
        val icon = icon()
        val style = style(imageBlur = 20)

        val first = cache.iconRenderData("app:mail", icon, style)
        val second = cache.iconRenderData("app:mail", icon, style)

        assertSame(first, second)
    }

    @Test
    fun reusesRenderDataAcrossStyleFieldsThatDoNotAffectColours() {
        val cache = CardRenderAssetCache()
        val icon = icon()

        // imageBlur (and radius/hue/tint) don't affect the sampled colours, so the render data is
        // shared rather than recomputed when only those fields differ.
        val blurred = cache.iconRenderData("app:mail", icon, style(imageBlur = 20))
        val sharp = cache.iconRenderData("app:mail", icon, style(imageBlur = 0))

        assertSame(blurred, sharp)
    }

    @Test
    fun keepsSeparateEntriesForDifferentImageAlpha() {
        val cache = CardRenderAssetCache()
        val icon = icon()

        val low = cache.iconRenderData("app:mail", icon, style(imageBlur = 0, imageAlpha = 64))
        val high = cache.iconRenderData("app:mail", icon, style(imageBlur = 0, imageAlpha = 200))

        assertNotSame(low, high)
    }

    @Test
    fun clearForcesRenderDataToBeRecomputed() {
        val cache = CardRenderAssetCache()
        val icon = icon()
        val style = style(imageBlur = 20)

        val first = cache.iconRenderData("app:mail", icon, style)
        cache.clear()
        val second = cache.iconRenderData("app:mail", icon, style)

        assertNotSame(first, second)
    }

    private fun icon(): Bitmap {
        return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 80, 120))
        }
    }

    private fun style(imageBlur: Int, imageAlpha: Int = 64): CardRenderStyleKey {
        return CardRenderStyleKey(
            radiusPx = 24,
            hueColor = Color.rgb(40, 80, 120),
            tintCards = true,
            imageAlpha = imageAlpha,
            imageBlur = imageBlur,
            useIconBackgrounds = true,
        )
    }
}
