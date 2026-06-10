package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView

class CardRenderer(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val cardSpec: CalmCardSpec,
    private val cardRenderAssetCache: CardRenderAssetCache,
    private val activePreferences: () -> LauncherUiPreferences,
) {
    private val sideIconCache = HashMap<Int, Bitmap?>()

    fun cardHeight(): Int = activity.dp(cardSpec.heightDp)
    fun cardStep(): Int = activity.dp(cardSpec.stepDp)
    fun cardCornerRadius(): Int = activity.dp(activePreferences().cardCornerRadiusDp)

    fun clearIconCache() = sideIconCache.clear()

    fun cardSideIcon(drawableRes: Int): Bitmap? {
        return sideIconCache.getOrPut(drawableRes) {
            activity.getDrawable(drawableRes)?.toBitmap()
        }
    }

    fun stackCard(
        text: String,
        hueColor: Int,
        tinted: Boolean,
        sideImage: Bitmap? = null,
        sideImageAlpha: Int = DEFAULT_ICON_BACKGROUND_ALPHA,
        sideImageRenderKey: String? = null,
        precomputedIconRenderData: AppIconCardRenderData? = null,
    ): TextView {
        val prefs = activePreferences()
        val cornerRadius = cardCornerRadius()
        return label(text).apply {
            val showImageAsBackground = sideImage != null && prefs.useCardIconBackgrounds
            val iconRenderData = if (showImageAsBackground) {
                // Prefer render data precomputed off the main thread (during background preload); only
                // sample on the UI thread as a fallback when none was supplied.
                precomputedIconRenderData ?: cardRenderAssetCache.iconRenderData(
                    imageKey = sideImageRenderKey ?: "bitmap-${sideImage.generationId}",
                    image = sideImage,
                    style = CardRenderStyleKey(
                        radiusPx = cornerRadius,
                        hueColor = hueColor,
                        tintCards = tinted,
                        imageAlpha = sideImageAlpha,
                        imageBlur = prefs.cardIconBlur,
                        useIconBackgrounds = prefs.useCardIconBackgrounds,
                    ),
                )
            } else {
                null
            }
            setLineSpacing(activity.dp(2).toFloat(), 1.0f)
            setPadding(
                activity.dp(cardSpec.horizontalPaddingDp),
                activity.dp(cardSpec.verticalPaddingDp),
                activity.dp(if (showImageAsBackground) 116 else cardSpec.horizontalPaddingDp),
                activity.dp(cardSpec.verticalPaddingDp),
            )
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            background = drawables.cardWithSideImage(
                cornerRadius,
                hueColor,
                tinted,
                sideImage.takeIf { showImageAsBackground },
                sideImageAlpha,
                prefs.cardIconBlur,
                iconRenderData,
                prefs.cardAppearance,
            )
            if (sideImage != null && !showImageAsBackground) {
                compoundDrawablePadding = activity.dp(14)
                setCompoundDrawables(null, null, sideImage.toCardIconDrawable(), null)
            }
            elevation = activity.dp(2).toFloat()
        }
    }

    private fun label(text: String): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(CalmTheme.INK)
            textSize = cardSpec.titleSp.toFloat()
            typeface = Typeface.DEFAULT
            includeFontPadding = true
        }
    }

    private fun Bitmap.toCardIconDrawable(): BitmapDrawable {
        return BitmapDrawable(activity.resources, this).apply {
            val size = activity.dp(cardSpec.iconSizeDp)
            setBounds(0, 0, size, size)
            alpha = 214
        }
    }

    companion object {
        // Alpha at which app-card icon backgrounds are drawn. Precomputed render data must use the
        // same value so it matches what stackCard would otherwise compute on the main thread.
        const val DEFAULT_ICON_BACKGROUND_ALPHA = 64
    }
}
