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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class AppIconCardDrawable(
    private val base: Drawable,
    private val icon: Bitmap,
    private val radius: Float,
    private val imageAlpha: Int = 64,
    private val blurStrength: Int = 0,
) : Drawable() {
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = imageAlpha.coerceIn(0, 255)
    }
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val iconMatrix = Matrix()
    private val rect = RectF()
    private val clipPath = Path()
    private val edgeColor = sampleRightEdgeColor(icon)
    private val washEndAlpha = if (imageAlpha >= 120) 128 else 84
    private val veilMidAlpha = if (imageAlpha >= 120) 18 else 36
    private val veilEndAlpha = if (imageAlpha >= 120) 52 else 108

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
        val targetSize = bounds.height() * 1.12f
        val scale = maxOf(targetSize / icon.width.toFloat(), targetSize / icon.height.toFloat())
        val left = bounds.right - targetSize + (targetSize * 0.08f)
        val top = bounds.top + (bounds.height() - targetSize) / 2f

        iconMatrix.reset()
        iconMatrix.setScale(scale, scale)
        iconMatrix.postTranslate(left, top)

        val layer = canvas.saveLayer(rect, null)
        drawBlurredIconCopies(canvas, targetSize)
        canvas.drawBitmap(icon, iconMatrix, iconPaint)
        val fadeStart = left - (targetSize * 0.62f)
        val fadeEnd = left + (targetSize * 0.92f)
        maskPaint.shader = LinearGradient(
            fadeStart,
            0f,
            fadeEnd,
            0f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(28, 255, 255, 255),
                Color.argb(94, 255, 255, 255),
                Color.argb(178, 255, 255, 255),
                Color.WHITE,
            ),
            floatArrayOf(0f, 0.22f, 0.50f, 0.76f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, maskPaint)
        maskPaint.shader = null
        canvas.restoreToCount(layer)
    }

    private fun drawBlurredIconCopies(canvas: Canvas, targetSize: Float) {
        if (blurStrength <= 0) return
        val distance = targetSize * (0.012f + (blurStrength.coerceIn(0, 100) / 100f) * 0.052f)
        val alpha = (imageAlpha * (0.08f + (blurStrength.coerceIn(0, 100) / 100f) * 0.26f)).toInt().coerceIn(0, 180)
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
                Color.argb(8, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb(16, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb(32, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb((washEndAlpha * 0.58f).toInt(), Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
                Color.argb(washEndAlpha, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)),
            ),
            floatArrayOf(0f, 0.24f, 0.48f, 0.74f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, washPaint)
        washPaint.shader = null
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
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, veilPaint)
        veilPaint.shader = null
    }

    private fun sampleRightEdgeColor(bitmap: Bitmap): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val startX = (bitmap.width * 0.72f).toInt().coerceIn(0, bitmap.width - 1)
        for (x in startX until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha < 48) continue
                red += Color.red(pixel).toLong()
                green += Color.green(pixel).toLong()
                blue += Color.blue(pixel).toLong()
                count++
            }
        }
        if (count == 0L) return Color.rgb(64, 60, 70)
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }
}
