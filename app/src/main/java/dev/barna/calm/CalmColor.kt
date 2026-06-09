package dev.barna.calm

import android.graphics.Bitmap

object CalmColor {
    @JvmStatic
    fun lerp(start: Float, end: Float, amount: Float): Float {
        return start + ((end - start) * clamp01(amount))
    }

    @JvmStatic
    fun blend(start: Int, end: Int, amount: Float): Int {
        val clampedAmount = clamp01(amount)
        return rgb(
            lerp(red(start).toFloat(), red(end).toFloat(), clampedAmount).toInt(),
            lerp(green(start).toFloat(), green(end).toFloat(), clampedAmount).toInt(),
            lerp(blue(start).toFloat(), blue(end).toFloat(), clampedAmount).toInt(),
        )
    }

    @JvmStatic
    fun clamp01(value: Float): Float {
        if (value < 0f) {
            return 0f
        }
        if (value > 1f) {
            return 1f
        }
        return value
    }

    @JvmStatic
    fun dominant(bitmap: Bitmap, fallbackColor: Int): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        val stepX = maxOf(1, bitmap.width / DOMINANT_SAMPLE_GRID)
        val stepY = maxOf(1, bitmap.height / DOMINANT_SAMPLE_GRID)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (alpha(pixel) >= MIN_PIXEL_ALPHA) {
                    val r = red(pixel)
                    val g = green(pixel)
                    val b = blue(pixel)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val saturation = max - min
                    val brightness = (r + g + b) / 3
                    if (saturation >= MIN_VIABLE_SATURATION && brightness >= MIN_VIABLE_BRIGHTNESS && brightness <= MAX_VIABLE_BRIGHTNESS) {
                        val pixelWeight = maxOf(1, saturation)
                        red += r.toLong() * pixelWeight
                        green += g.toLong() * pixelWeight
                        blue += b.toLong() * pixelWeight
                        weight += pixelWeight.toLong()
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (weight == 0L) {
            return fallbackColor
        }
        return rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    }

    private fun alpha(color: Int): Int = color ushr 24 and 0xff

    private fun red(color: Int): Int = color shr 16 and 0xff

    private fun green(color: Int): Int = color shr 8 and 0xff

    private fun blue(color: Int): Int = color and 0xff

    private fun rgb(red: Int, green: Int, blue: Int): Int {
        return -0x1000000 or (red and 0xff shl 16) or (green and 0xff shl 8) or (blue and 0xff)
    }

    private const val DOMINANT_SAMPLE_GRID = 24
    private const val MIN_PIXEL_ALPHA = 96
    private const val MIN_VIABLE_SATURATION = 22
    private const val MIN_VIABLE_BRIGHTNESS = 28
    private const val MAX_VIABLE_BRIGHTNESS = 236
}
