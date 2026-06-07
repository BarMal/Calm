package dev.barna.calm

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

class CardRenderAssetCache {
    private val iconRenderData = ConcurrentHashMap<String, AppIconCardRenderData>()

    fun iconRenderData(
        imageKey: String,
        image: Bitmap,
        style: CardRenderStyleKey,
    ): AppIconCardRenderData {
        val key = buildString {
            append(imageKey)
            append('|')
            append(image.width)
            append('x')
            append(image.height)
            append('|')
            append(image.generationId)
            append('|')
            append(style.configurationHash())
        }
        iconRenderData[key]?.let { return it }
        val data = AppIconCardRenderData.from(image, style.imageAlpha)
        val cached = iconRenderData.putIfAbsent(key, data) ?: data
        trim()
        return cached
    }

    fun clear() {
        iconRenderData.clear()
    }

    private fun trim() {
        if (iconRenderData.size <= MAX_ICON_RENDER_DATA) return
        iconRenderData.clear()
    }

    private companion object {
        const val MAX_ICON_RENDER_DATA = 4096
    }
}

data class CardRenderStyleKey(
    val radiusPx: Int,
    val hueColor: Int,
    val tintCards: Boolean,
    val imageAlpha: Int,
    val imageBlur: Int,
    val useIconBackgrounds: Boolean,
) {
    fun configurationHash(): String {
        return listOf(
            radiusPx,
            hueColor,
            tintCards,
            imageAlpha,
            imageBlur,
            useIconBackgrounds,
        ).joinToString(":")
    }
}
