package dev.barna.calm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity

class CalmDrawables(private val context: Context) {
    fun wallpaperShade(): Drawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(CalmTheme.SHADE_TOP, CalmTheme.SHADE_MID, CalmTheme.SHADE_BOTTOM),
        )
    }

    fun glass(color: Int, radius: Int): Drawable {
        val shadow = GradientDrawable().apply {
            setColor(CalmTheme.SHADOW)
            cornerRadius = (radius + context.dp(2)).toFloat()
        }
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(58, 34, 31, 38), color, Color.argb(66, 4, 4, 9)),
        ).apply {
            cornerRadius = radius.toFloat()
            setStroke(context.dp(1), CalmTheme.STROKE)
        }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(CalmTheme.GLOSS, Color.argb(10, 255, 248, 234), Color.TRANSPARENT),
        ).apply { cornerRadius = radius.toFloat() }
        val refraction = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(CalmTheme.REFRACTION_BLUE, Color.TRANSPARENT, CalmTheme.REFRACTION_LILAC),
        ).apply { cornerRadius = radius.toFloat() }

        return LayerDrawable(arrayOf(shadow, base, gloss, refraction)).apply {
            setLayerInset(0, 0, context.dp(1), 0, 0)
            setLayerInset(1, 0, 0, 0, 0)
            setLayerInset(2, context.dp(1), context.dp(1), context.dp(1), 0)
            setLayerInset(3, context.dp(1), context.dp(1), context.dp(1), 0)
        }
    }

    private fun scaledColor(alpha: Int, factor: Float, r: Int, g: Int, b: Int): Int {
        return Color.argb((alpha * factor).toInt().coerceIn(0, 255), r, g, b)
    }

    fun notificationCard(
        radius: Int,
        hueColor: Int,
        tintCards: Boolean,
        appearance: CardAppearance = CardAppearance.DEFAULT,
    ): Drawable {
        val r = radius.toFloat()
        val baseAlpha = if (appearance.solid) intArrayOf(255, 255, 255) else intArrayOf(214, 196, 212)
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(baseAlpha[0], 24, 22, 28),
                Color.argb(baseAlpha[1], 10, 10, 15),
                Color.argb(baseAlpha[2], 4, 4, 8),
            ),
        ).apply {
            cornerRadius = r
            setStroke(context.dp(1), Color.argb(if (tintCards) 48 else 42, 255, 246, 226))
        }

        val layers = ArrayList<Drawable>()
        val insetLayers = ArrayList<Int>()
        layers.add(base)

        if (tintCards && hueColor != 0 && appearance.tintFactor > 0f) {
            val t = appearance.tintFactor
            val red = Color.red(hueColor)
            val green = Color.green(hueColor)
            val blue = Color.blue(hueColor)
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(scaledColor(72, t, red, green, blue), scaledColor(16, t, red, green, blue), scaledColor(48, t, red, green, blue)),
                ).apply { cornerRadius = r },
            )
        }
        if (appearance.frostFactor > 0f) {
            val f = appearance.frostFactor
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(scaledColor(34, f, 255, 252, 240), scaledColor(10, f, 255, 252, 240), scaledColor(4, f, 255, 252, 240)),
                ).apply { cornerRadius = r },
            )
            insetLayers.add(layers.lastIndex)
        }
        if (appearance.glossFactor > 0f) {
            val g = appearance.glossFactor
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(scaledColor(24, g, 255, 249, 235), scaledColor(6, g, 255, 249, 235), Color.TRANSPARENT),
                ).apply { cornerRadius = r },
            )
            insetLayers.add(layers.lastIndex)
        }
        return LayerDrawable(layers.toTypedArray()).apply {
            insetLayers.forEach { setLayerInset(it, context.dp(1), context.dp(1), context.dp(1), 0) }
        }
    }

    fun notificationCardWithImage(
        radius: Int,
        image: Bitmap,
        hueColor: Int,
        tintCards: Boolean,
        appearance: CardAppearance = CardAppearance.DEFAULT,
    ): Drawable {
        val r = radius.toFloat()
        val imageDrawable = RoundedBitmapDrawable(image, r).apply {
            alpha = 112
        }
        val shade = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(210, 12, 11, 16),
                Color.argb(168, 5, 5, 9),
                Color.argb(224, 3, 3, 7),
            ),
        ).apply {
            cornerRadius = r
            setStroke(context.dp(1), Color.argb(if (tintCards) 54 else 44, 255, 246, 226))
        }

        val layers = ArrayList<Drawable>()
        val insetLayers = ArrayList<Int>()
        layers.add(imageDrawable)
        layers.add(shade)

        if (appearance.tintFactor > 0f) {
            val t = appearance.tintFactor
            val red = Color.red(hueColor)
            val green = Color.green(hueColor)
            val blue = Color.blue(hueColor)
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        scaledColor(if (tintCards) 74 else 28, t, red, green, blue),
                        Color.TRANSPARENT,
                        scaledColor(if (tintCards) 56 else 22, t, red, green, blue),
                    ),
                ).apply { cornerRadius = r },
            )
        }
        if (appearance.frostFactor > 0f) {
            val f = appearance.frostFactor
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(scaledColor(28, f, 255, 252, 240), scaledColor(8, f, 255, 252, 240), scaledColor(4, f, 255, 252, 240)),
                ).apply { cornerRadius = r },
            )
            insetLayers.add(layers.lastIndex)
        }
        if (appearance.glossFactor > 0f) {
            val g = appearance.glossFactor
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(scaledColor(28, g, 255, 249, 235), scaledColor(6, g, 255, 249, 235), Color.TRANSPARENT),
                ).apply { cornerRadius = r },
            )
            insetLayers.add(layers.lastIndex)
        }
        return LayerDrawable(layers.toTypedArray()).apply {
            insetLayers.forEach { setLayerInset(it, context.dp(1), context.dp(1), context.dp(1), 0) }
        }
    }

    fun cardWithSideImage(
        radius: Int,
        hueColor: Int,
        tintCards: Boolean,
        image: Bitmap?,
        imageAlpha: Int = 64,
        imageBlur: Int = 0,
        iconRenderData: AppIconCardRenderData? = null,
        appearance: CardAppearance = CardAppearance.DEFAULT,
    ): Drawable {
        val base = notificationCard(radius, hueColor, tintCards, appearance)
        return if (image == null) {
            base
        } else {
            AppIconCardDrawable(
                base,
                image,
                radius.toFloat(),
                imageAlpha,
                imageBlur,
                iconRenderData ?: AppIconCardRenderData.from(image, imageAlpha),
            )
        }
    }

    fun appCard(radius: Int, hueColor: Int, icon: Bitmap?): Drawable = cardWithSideImage(radius, hueColor, true, icon)

    fun glassWithHue(color: Int, radius: Int, hueColor: Int): Drawable {
        val hue = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(38, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                Color.TRANSPARENT,
                Color.argb(24, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
            ),
        ).apply { cornerRadius = radius.toFloat() }
        return LayerDrawable(arrayOf(hue, glass(color, radius)))
    }

    fun glassWithImage(color: Int, radius: Int, image: Bitmap, hueColor: Int): Drawable {
        val imageDrawable = BitmapDrawable(context.resources, image).apply {
            gravity = Gravity.END or Gravity.TOP
            alpha = 34
            setDither(true)
        }
        val hue = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(42, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                Color.TRANSPARENT,
                Color.argb(26, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
            ),
        ).apply { cornerRadius = radius.toFloat() }
        val veil = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(88, 5, 5, 9), Color.argb(48, 18, 16, 20), Color.argb(116, 3, 3, 7)),
        ).apply { cornerRadius = radius.toFloat() }

        return LayerDrawable(arrayOf(imageDrawable, hue, veil, glass(color, radius))).apply {
            setLayerInset(0, context.dp(26), context.dp(14), context.dp(8), context.dp(120))
        }
    }
}
