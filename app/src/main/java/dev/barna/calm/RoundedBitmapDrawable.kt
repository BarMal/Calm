package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

class RoundedBitmapDrawable(
    private val bitmap: Bitmap,
    private val radius: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val rect = RectF()
    private val shaderMatrix = Matrix()

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        updateShaderMatrix(rect)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun updateShaderMatrix(target: RectF) {
        if (bitmap.width <= 0 || bitmap.height <= 0 || target.isEmpty) return
        val scale = maxOf(target.width() / bitmap.width.toFloat(), target.height() / bitmap.height.toFloat())
        val dx = target.left + (target.width() - bitmap.width * scale) / 2f
        val dy = target.top + (target.height() - bitmap.height * scale) / 2f
        shaderMatrix.reset()
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(dx, dy)
        (paint.shader as? BitmapShader)?.setLocalMatrix(shaderMatrix)
    }
}
