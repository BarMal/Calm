package dev.barna.calm

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.WindowInsets
import android.widget.FrameLayout

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}

fun matchParentParams(): FrameLayout.LayoutParams {
    return FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )
}

fun Drawable.toBitmap(): Bitmap {
    val width = maxOf(1, intrinsicWidth)
    val height = maxOf(1, intrinsicHeight)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

fun Drawable.toUnmaskedIconBitmap(): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
        val width = intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ICON_BITMAP_SIZE
        val height = intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ICON_BITMAP_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background?.let { layer ->
            layer.setBounds(0, 0, canvas.width, canvas.height)
            layer.draw(canvas)
        }
        foreground?.let { layer ->
            layer.setBounds(0, 0, canvas.width, canvas.height)
            layer.draw(canvas)
        }
        return bitmap
    }
    return toBitmap()
}

fun Bitmap.toRectangularCardArtwork(): Bitmap {
    if (!hasTransparentCorners()) return this

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(averageVisibleColor())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val enlargedWidth = width * 1.52f
    val enlargedHeight = height * 1.52f
    val enlargedBounds = RectF(
        (width - enlargedWidth) / 2f,
        (height - enlargedHeight) / 2f,
        (width + enlargedWidth) / 2f,
        (height + enlargedHeight) / 2f,
    )
    paint.alpha = 178
    canvas.drawBitmap(this, null, enlargedBounds, paint)
    paint.alpha = 255
    canvas.drawBitmap(this, 0f, 0f, paint)
    return output
}

fun Activity.statusBarHeightFallback(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = window.decorView.rootWindowInsets
        if (insets != null) {
            return insets.getInsets(WindowInsets.Type.statusBars()).top
        }
    }
    return dp(28)
}

private const val DEFAULT_ICON_BITMAP_SIZE = 144

private fun Bitmap.hasTransparentCorners(): Boolean {
    if (width <= 0 || height <= 0) return false
    val sample = minOf(width, height, 12)
    val left = 0 until sample
    val right = (width - sample).coerceAtLeast(0) until width
    val top = 0 until sample
    val bottom = (height - sample).coerceAtLeast(0) until height
    val corners = arrayOf(
        left to top,
        right to top,
        left to bottom,
        right to bottom,
    )
    var transparent = 0
    var total = 0
    corners.forEach { (xs, ys) ->
        xs.forEach { x ->
            ys.forEach { y ->
                total++
                if (Color.alpha(getPixel(x, y)) < 64) transparent++
            }
        }
    }
    return total > 0 && transparent > total / 3
}

private fun Bitmap.averageVisibleColor(): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    val stepX = maxOf(1, width / 48)
    val stepY = maxOf(1, height / 48)
    var x = 0
    while (x < width) {
        var y = 0
        while (y < height) {
            val pixel = getPixel(x, y)
            if (Color.alpha(pixel) >= 64) {
                red += Color.red(pixel).toLong()
                green += Color.green(pixel).toLong()
                blue += Color.blue(pixel).toLong()
                count++
            }
            y += stepY
        }
        x += stepX
    }
    if (count == 0L) return Color.rgb(40, 36, 44)
    return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

fun roman(value: Int): String {
    val numerals = arrayOf(
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
    )
    if (value > 0 && value <= numerals.size) {
        return numerals[value - 1]
    }
    return value.toString()
}

fun friendlyPackageName(packageName: String): String {
    val finalSegment = packageName
        .split('.')
        .lastOrNull { it.isNotBlank() }
        ?.replace(Regex("[-_]+"), " ")
        ?.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        ?.trim()
        .orEmpty()
    if (finalSegment.isBlank()) return "App"

    return finalSegment
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { char -> char.titlecase() }
        }
}
