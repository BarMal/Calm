package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class AppIconCardDrawable(
    private val base: Drawable,
    private val icon: Bitmap,
    private val radius: Float,
    private val imageAlpha: Int = 64,
    private val blurStrength: Int = 0,
    private val renderData: AppIconCardRenderData = AppIconCardRenderData.from(icon, imageAlpha),
) : Drawable() {
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = imageAlpha.coerceIn(0, 255)
    }
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val iconMatrix = Matrix()
    private val rect = RectF()
    private val clipPath = Path()
    private val leftColor = renderData.leftColor
    private val midColor = renderData.midColor
    private val nearEdgeColor = renderData.nearEdgeColor
    private val edgeColor = renderData.edgeColor
    private val washEndAlpha = renderData.washEndAlpha
    private val veilMidAlpha = renderData.veilMidAlpha
    private val veilEndAlpha = renderData.veilEndAlpha

    // Shaders and geometry are rebuilt only when bounds dimensions change.
    private var cachedBoundsWidth = -1
    private var cachedBoundsHeight = -1
    private var cachedGlow1Cx = 0f
    private var cachedGlow1Cy = 0f
    private var cachedGlow1Radius = 0f
    private var cachedGlow2Cx = 0f
    private var cachedGlow2Cy = 0f
    private var cachedGlow2Radius = 0f
    private var cachedGlow1Shader: RadialGradient? = null
    private var cachedGlow2Shader: RadialGradient? = null
    private var cachedBlurDistance = 0f
    private var cachedBlurAlpha = 0

    override fun draw(canvas: Canvas) {
        base.bounds = bounds
        base.draw(canvas)
        if (icon.width <= 0 || icon.height <= 0 || bounds.isEmpty) return

        if (bounds.width() != cachedBoundsWidth || bounds.height() != cachedBoundsHeight) {
            rebuildBoundsDependentState()
        }

        rect.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(rect, washPaint)
        glowPaint.shader = cachedGlow1Shader
        canvas.drawCircle(cachedGlow1Cx, cachedGlow1Cy, cachedGlow1Radius, glowPaint)
        glowPaint.shader = cachedGlow2Shader
        canvas.drawCircle(cachedGlow2Cx, cachedGlow2Cy, cachedGlow2Radius, glowPaint)
        drawIcon(canvas)
        canvas.drawRect(rect, veilPaint)
        canvas.restoreToCount(checkpoint)
    }

    private fun rebuildBoundsDependentState() {
        cachedBoundsWidth = bounds.width()
        cachedBoundsHeight = bounds.height()

        washPaint.shader = LinearGradient(
            bounds.left.toFloat(), 0f, bounds.right.toFloat(), 0f,
            intArrayOf(
                Color.argb(6, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)),
                Color.argb(18, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)),
                Color.argb(36, Color.red(midColor), Color.green(midColor), Color.blue(midColor)),
                Color.argb((washEndAlpha * 0.58f).toInt(), Color.red(nearEdgeColor), Color.green(nearEdgeColor), Color.blue(nearEdgeColor)),
                Color.argb((washEndAlpha * 0.82f).toInt(), Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb(washEndAlpha, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
            ),
            floatArrayOf(0f, 0.20f, 0.44f, 0.64f, 0.82f, 1f),
            Shader.TileMode.CLAMP,
        )

        cachedGlow1Cx = bounds.right - cachedBoundsHeight * GLOW1_CENTER_OFFSET
        cachedGlow1Cy = bounds.centerY().toFloat()
        cachedGlow1Radius = cachedBoundsHeight * GLOW1_RADIUS_FACTOR
        cachedGlow1Shader = RadialGradient(
            cachedGlow1Cx, cachedGlow1Cy, cachedGlow1Radius,
            intArrayOf(
                Color.argb(34, Color.red(midColor), Color.green(midColor), Color.blue(midColor)),
                Color.argb((34 * 0.42f).toInt(), Color.red(midColor), Color.green(midColor), Color.blue(midColor)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )

        cachedGlow2Cx = bounds.right - cachedBoundsHeight * GLOW2_CENTER_OFFSET
        cachedGlow2Cy = bounds.centerY().toFloat()
        cachedGlow2Radius = cachedBoundsHeight * GLOW2_RADIUS_FACTOR
        cachedGlow2Shader = RadialGradient(
            cachedGlow2Cx, cachedGlow2Cy, cachedGlow2Radius,
            intArrayOf(
                Color.argb(46, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb((46 * 0.42f).toInt(), Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )

        val targetSize = cachedBoundsHeight * ICON_TARGET_SIZE_FACTOR
        val scale = maxOf(targetSize / icon.width.toFloat(), targetSize / icon.height.toFloat())
        val iconLeft = bounds.right - targetSize + (targetSize * ICON_LEFT_INSET_FRACTION)
        val iconTop = bounds.top + (cachedBoundsHeight - targetSize) / 2f
        iconMatrix.reset()
        iconMatrix.setScale(scale, scale)
        iconMatrix.postTranslate(iconLeft, iconTop)

        val fadeStart = iconLeft - (targetSize * ICON_FADE_START_FACTOR)
        val fadeEnd = iconLeft + (targetSize * ICON_FADE_END_FACTOR)
        maskPaint.shader = LinearGradient(
            fadeStart, 0f, fadeEnd, 0f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(12, 255, 255, 255),
                Color.argb(56, 255, 255, 255),
                Color.argb(145, 255, 255, 255),
                Color.WHITE,
            ),
            floatArrayOf(0f, 0.30f, 0.60f, 0.84f, 1f),
            Shader.TileMode.CLAMP,
        )

        val strength = maxOf(blurStrength.coerceIn(0, 100), BLUR_STRENGTH_FLOOR)
        cachedBlurDistance = targetSize * (BLUR_DISTANCE_BASE_FRACTION + (strength / 100f) * BLUR_DISTANCE_SCALE)
        cachedBlurAlpha = (imageAlpha * (BLUR_ALPHA_BASE_FRACTION + (strength / 100f) * BLUR_ALPHA_SCALE)).toInt().coerceIn(BLUR_ALPHA_MIN, BLUR_ALPHA_MAX)

        veilPaint.shader = LinearGradient(
            bounds.left.toFloat(), 0f, bounds.right.toFloat(), 0f,
            intArrayOf(
                Color.argb(0, 0, 0, 0),
                Color.argb(veilMidAlpha / 2, 4, 4, 8),
                Color.argb(veilEndAlpha, 4, 4, 8),
            ),
            floatArrayOf(0f, VEIL_START_FRACTION, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
        iconPaint.alpha = (imageAlpha * (alpha / 255f)).toInt().coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        base.colorFilter = colorFilter
        iconPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val GLOW1_CENTER_OFFSET = 0.74f
        const val GLOW1_RADIUS_FACTOR = 0.92f
        const val GLOW2_CENTER_OFFSET = 0.18f
        const val GLOW2_RADIUS_FACTOR = 0.72f
        const val ICON_TARGET_SIZE_FACTOR = 1.18f
        const val ICON_LEFT_INSET_FRACTION = 0.06f
        const val ICON_FADE_START_FACTOR = 0.92f
        const val ICON_FADE_END_FACTOR = 0.96f
        const val VEIL_START_FRACTION = 0.74f
        const val BLUR_STRENGTH_FLOOR = 38
        const val BLUR_DISTANCE_BASE_FRACTION = 0.018f
        const val BLUR_DISTANCE_SCALE = 0.052f
        const val BLUR_ALPHA_BASE_FRACTION = 0.12f
        const val BLUR_ALPHA_SCALE = 0.28f
        const val BLUR_ALPHA_MIN = 18
        const val BLUR_ALPHA_MAX = 180
    }

    private fun drawIcon(canvas: Canvas) {
        val layer = canvas.saveLayer(rect, null)
        blurPaint.alpha = cachedBlurAlpha
        canvas.save()
        canvas.translate(-cachedBlurDistance, 0f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(cachedBlurDistance * 2f, 0f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(-cachedBlurDistance, -cachedBlurDistance)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(0f, cachedBlurDistance * 2f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.restore()
        canvas.drawBitmap(icon, iconMatrix, iconPaint)
        canvas.drawRect(rect, maskPaint)
        canvas.restoreToCount(layer)
    }
}

data class AppIconCardRenderData(
    val leftColor: Int,
    val midColor: Int,
    val nearEdgeColor: Int,
    val edgeColor: Int,
    val washEndAlpha: Int,
    val veilMidAlpha: Int,
    val veilEndAlpha: Int,
) {
    companion object {
        fun from(icon: Bitmap, imageAlpha: Int): AppIconCardRenderData {
            val highAlpha = imageAlpha >= HIGH_IMAGE_ALPHA_THRESHOLD
            return AppIconCardRenderData(
                leftColor = sampleRegionColor(icon, 0f, LEFT_REGION_END),
                midColor = sampleRegionColor(icon, MID_REGION_START, MID_REGION_END),
                nearEdgeColor = sampleRegionColor(icon, NEAR_EDGE_REGION_START, NEAR_EDGE_REGION_END),
                edgeColor = sampleRegionColor(icon, EDGE_REGION_START, 1f),
                washEndAlpha = if (highAlpha) 126 else 78,
                veilMidAlpha = if (highAlpha) 14 else 26,
                veilEndAlpha = if (highAlpha) 44 else 92,
            )
        }

        private const val HIGH_IMAGE_ALPHA_THRESHOLD = 120
        private const val LEFT_REGION_END = 0.36f
        private const val MID_REGION_START = 0.28f
        private const val MID_REGION_END = 0.60f
        private const val NEAR_EDGE_REGION_START = 0.50f
        private const val NEAR_EDGE_REGION_END = 0.82f
        private const val EDGE_REGION_START = 0.68f

        private fun sampleRegionColor(bitmap: Bitmap, startRatio: Float, endRatio: Float): Int {
            if (bitmap.width <= 0 || bitmap.height <= 0) return Color.rgb(64, 60, 70)
            var red = 0L
            var green = 0L
            var blue = 0L
            var weight = 0L
            val startX = (bitmap.width * startRatio).toInt().coerceIn(0, bitmap.width - 1)
            val endX = (bitmap.width * endRatio).toInt().coerceIn(startX + 1, bitmap.width)
            for (x in startX until endX) {
                for (y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha < 32) continue
                    red += Color.red(pixel).toLong() * alpha
                    green += Color.green(pixel).toLong() * alpha
                    blue += Color.blue(pixel).toLong() * alpha
                    weight += alpha.toLong()
                }
            }
            if (weight == 0L) return Color.rgb(64, 60, 70)
            return Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
        }
    }
}
