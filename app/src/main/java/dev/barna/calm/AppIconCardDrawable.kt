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
    private val edgeColor = renderData.edgeColor
    private val washEndAlpha = renderData.washEndAlpha
    private val veilMidAlpha = renderData.veilMidAlpha
    private val veilEndAlpha = renderData.veilEndAlpha

    override fun draw(canvas: Canvas) {
        base.bounds = bounds
        base.draw(canvas)
        if (icon.width <= 0 || icon.height <= 0 || bounds.isEmpty) return

        rect.set(bounds)
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        drawIconWash(canvas)
        drawIconGlows(canvas)
        drawIcon(canvas)
        drawRightVeil(canvas)
        canvas.restoreToCount(checkpoint)
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

    private fun drawIcon(canvas: Canvas) {
        val targetSize = bounds.height() * 1.18f
        val scale = maxOf(targetSize / icon.width.toFloat(), targetSize / icon.height.toFloat())
        val left = bounds.right - targetSize + (targetSize * 0.06f)
        val top = bounds.top + (bounds.height() - targetSize) / 2f

        iconMatrix.reset()
        iconMatrix.setScale(scale, scale)
        iconMatrix.postTranslate(left, top)

        val layer = canvas.saveLayer(rect, null)
        drawBlurredIconCopies(canvas, targetSize)
        canvas.drawBitmap(icon, iconMatrix, iconPaint)
        val fadeStart = left - (targetSize * 0.72f)
        val fadeEnd = left + (targetSize * 0.96f)
        maskPaint.shader = LinearGradient(
            fadeStart,
            0f,
            fadeEnd,
            0f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(18, 255, 255, 255),
                Color.argb(70, 255, 255, 255),
                Color.argb(152, 255, 255, 255),
                Color.WHITE,
            ),
            floatArrayOf(0f, 0.24f, 0.52f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, maskPaint)
        maskPaint.shader = null
        canvas.restoreToCount(layer)
    }

    private fun drawBlurredIconCopies(canvas: Canvas, targetSize: Float) {
        val strength = maxOf(blurStrength.coerceIn(0, 100), 28)
        val distance = targetSize * (0.018f + (strength / 100f) * 0.052f)
        val alpha = (imageAlpha * (0.12f + (strength / 100f) * 0.28f)).toInt().coerceIn(18, 180)
        blurPaint.alpha = alpha
        canvas.save()
        canvas.translate(-distance, 0f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(distance * 2f, 0f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(-distance, -distance)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.translate(0f, distance * 2f)
        canvas.drawBitmap(icon, iconMatrix, blurPaint)
        canvas.restore()
    }

    private fun drawIconWash(canvas: Canvas) {
        washPaint.shader = LinearGradient(
            bounds.left.toFloat(),
            0f,
            bounds.right.toFloat(),
            0f,
            intArrayOf(
                Color.argb(6, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)),
                Color.argb(18, Color.red(leftColor), Color.green(leftColor), Color.blue(leftColor)),
                Color.argb(38, Color.red(midColor), Color.green(midColor), Color.blue(midColor)),
                Color.argb((washEndAlpha * 0.68f).toInt(), Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb(washEndAlpha, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
            ),
            floatArrayOf(0f, 0.22f, 0.48f, 0.74f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, washPaint)
        washPaint.shader = null
    }

    private fun drawIconGlows(canvas: Canvas) {
        drawGlow(canvas, bounds.right - bounds.height() * 0.74f, bounds.centerY().toFloat(), bounds.height() * 0.92f, midColor, 34)
        drawGlow(canvas, bounds.right - bounds.height() * 0.18f, bounds.centerY().toFloat(), bounds.height() * 0.72f, edgeColor, 46)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        glowPaint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb((alpha * 0.42f).toInt(), Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawRightVeil(canvas: Canvas) {
        veilPaint.shader = LinearGradient(
            bounds.left.toFloat(),
            0f,
            bounds.right.toFloat(),
            0f,
            intArrayOf(
                Color.argb(0, 0, 0, 0),
                Color.argb(veilMidAlpha / 2, 4, 4, 8),
                Color.argb(veilEndAlpha, 4, 4, 8),
            ),
            floatArrayOf(0f, 0.74f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, veilPaint)
        veilPaint.shader = null
    }

}

data class AppIconCardRenderData(
    val leftColor: Int,
    val midColor: Int,
    val edgeColor: Int,
    val washEndAlpha: Int,
    val veilMidAlpha: Int,
    val veilEndAlpha: Int,
) {
    companion object {
        fun from(icon: Bitmap, imageAlpha: Int): AppIconCardRenderData {
            return AppIconCardRenderData(
                leftColor = sampleRegionColor(icon, 0f, 0.36f),
                midColor = sampleRegionColor(icon, 0.32f, 0.72f),
                edgeColor = sampleRegionColor(icon, 0.64f, 1f),
                washEndAlpha = if (imageAlpha >= 120) 126 else 78,
                veilMidAlpha = if (imageAlpha >= 120) 14 else 26,
                veilEndAlpha = if (imageAlpha >= 120) 44 else 92,
            )
        }

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
