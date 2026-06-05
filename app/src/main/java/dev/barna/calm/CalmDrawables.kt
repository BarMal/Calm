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

    fun notificationCard(radius: Int, hueColor: Int, tintCards: Boolean): Drawable {
        val base = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(214, 24, 22, 28),
                Color.argb(196, 10, 10, 15),
                Color.argb(212, 4, 4, 8),
            ),
        ).apply {
            cornerRadius = radius.toFloat()
            setStroke(context.dp(1), Color.argb(if (tintCards) 48 else 42, 255, 246, 226))
        }
        val frost = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(34, 255, 252, 240), Color.argb(10, 255, 252, 240), Color.argb(4, 255, 252, 240)),
        ).apply { cornerRadius = radius.toFloat() }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(24, 255, 249, 235), Color.argb(6, 255, 249, 235), Color.TRANSPARENT),
        ).apply { cornerRadius = radius.toFloat() }

        if (!tintCards || hueColor == 0) {
            return LayerDrawable(arrayOf(base, frost, gloss)).apply {
                setLayerInset(1, context.dp(1), context.dp(1), context.dp(1), 0)
                setLayerInset(2, context.dp(1), context.dp(1), context.dp(1), 0)
            }
        }

        val hue = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(72, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                Color.argb(16, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                Color.argb(48, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
            ),
        ).apply { cornerRadius = radius.toFloat() }

        return LayerDrawable(arrayOf(base, hue, frost, gloss)).apply {
            setLayerInset(2, context.dp(1), context.dp(1), context.dp(1), 0)
            setLayerInset(3, context.dp(1), context.dp(1), context.dp(1), 0)
        }
    }

    fun notificationCardWithImage(radius: Int, image: Bitmap, hueColor: Int, tintCards: Boolean): Drawable {
        val imageDrawable = RoundedBitmapDrawable(image, radius.toFloat()).apply {
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
            cornerRadius = radius.toFloat()
            setStroke(context.dp(1), Color.argb(if (tintCards) 54 else 44, 255, 246, 226))
        }
        val hue = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(if (tintCards) 74 else 28, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
                Color.TRANSPARENT,
                Color.argb(if (tintCards) 56 else 22, Color.red(hueColor), Color.green(hueColor), Color.blue(hueColor)),
            ),
        ).apply { cornerRadius = radius.toFloat() }
        val frost = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(28, 255, 252, 240), Color.argb(8, 255, 252, 240), Color.argb(4, 255, 252, 240)),
        ).apply { cornerRadius = radius.toFloat() }
        val gloss = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(28, 255, 249, 235), Color.argb(6, 255, 249, 235), Color.TRANSPARENT),
        ).apply { cornerRadius = radius.toFloat() }

        return LayerDrawable(arrayOf(imageDrawable, shade, hue, frost, gloss)).apply {
            setLayerInset(3, context.dp(1), context.dp(1), context.dp(1), 0)
            setLayerInset(4, context.dp(1), context.dp(1), context.dp(1), 0)
        }
    }

    fun cardWithSideImage(
        radius: Int,
        hueColor: Int,
        tintCards: Boolean,
        image: Bitmap?,
        imageAlpha: Int = 64,
        imageBlur: Int = 0,
    ): Drawable {
        val base = notificationCard(radius, hueColor, tintCards)
        return if (image == null) {
            base
        } else {
            AppIconCardDrawable(base, image, radius.toFloat(), imageAlpha, imageBlur)
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
