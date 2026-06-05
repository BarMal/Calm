package dev.barna.calm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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

fun MainActivity.statusBarHeightFallback(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = window.decorView.rootWindowInsets
        if (insets != null) {
            return insets.getInsets(WindowInsets.Type.statusBars()).top
        }
    }
    return dp(28)
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
