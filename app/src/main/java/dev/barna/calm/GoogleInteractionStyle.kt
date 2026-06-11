package dev.barna.calm

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

object GoogleInteractionStyle {
    data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outlineVariant: Int,
        val primary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val error: Int,
    )

    private val lightPalette = Palette(
        background = Color.rgb(248, 250, 253),
        surface = Color.WHITE,
        surfaceContainer = Color.rgb(241, 244, 249),
        surfaceContainerHigh = Color.rgb(232, 234, 237),
        onSurface = Color.rgb(32, 33, 36),
        onSurfaceVariant = Color.rgb(95, 99, 104),
        outlineVariant = Color.rgb(218, 220, 224),
        primary = Color.rgb(11, 87, 208),
        primaryContainer = Color.rgb(211, 227, 253),
        onPrimaryContainer = Color.rgb(4, 30, 73),
        error = Color.rgb(179, 38, 30),
    )

    private val darkPalette = Palette(
        background = Color.rgb(19, 19, 20),
        surface = Color.rgb(31, 31, 32),
        surfaceContainer = Color.rgb(42, 42, 43),
        surfaceContainerHigh = Color.rgb(54, 54, 55),
        onSurface = Color.rgb(227, 227, 227),
        onSurfaceVariant = Color.rgb(196, 199, 197),
        outlineVariant = Color.rgb(68, 71, 70),
        primary = Color.rgb(168, 199, 250),
        primaryContainer = Color.rgb(8, 66, 160),
        onPrimaryContainer = Color.rgb(211, 227, 253),
        error = Color.rgb(242, 184, 181),
    )

    fun palette(context: Context): Palette = if (isDarkMode(context)) darkPalette else lightPalette

    fun isDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun background(context: Context): Int = palette(context).background
    fun surface(context: Context): Int = palette(context).surface
    fun surfaceContainerHigh(context: Context): Int = palette(context).surfaceContainerHigh
    fun onSurface(context: Context): Int = palette(context).onSurface
    fun onSurfaceVariant(context: Context): Int = palette(context).onSurfaceVariant
    fun outlineVariant(context: Context): Int = palette(context).outlineVariant
    fun primary(context: Context): Int = palette(context).primary
    fun primaryContainer(context: Context): Int = palette(context).primaryContainer
    fun error(context: Context): Int = palette(context).error

    fun dialogBuilder(context: Context): AlertDialog.Builder {
        val theme = if (isDarkMode(context)) {
            android.R.style.Theme_Material_Dialog_Alert
        } else {
            android.R.style.Theme_Material_Light_Dialog_Alert
        }
        return AlertDialog.Builder(context, theme)
    }

    fun rowBackground(context: Context, radiusDp: Int = 22): RippleDrawable {
        val colors = palette(context)
        return ripple(context, rounded(colors.surface, context.dp(radiusDp)).apply {
            setStroke(context.dp(1), colors.outlineVariant)
        })
    }

    fun chipBackground(context: Context, selected: Boolean = false, radiusDp: Int = 999): RippleDrawable {
        val colors = palette(context)
        val fill = if (selected) colors.primaryContainer else colors.surfaceContainer
        val stroke = if (selected) colors.primary else colors.outlineVariant
        return ripple(context, rounded(fill, context.dp(radiusDp)).apply {
            setStroke(context.dp(1), stroke)
        })
    }

    fun popupMenu(
        context: Context,
        source: View,
        anchor: Pair<Int, Int>,
        actions: List<ContextAction>,
        destructiveLabels: Set<String> = setOf("Remove"),
    ): PopupWindow {
        source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val colors = palette(context)
        var popup: PopupWindow? = null
        val width = context.dp(248)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(colors.surface, context.dp(18)).apply {
                setStroke(context.dp(1), colors.outlineVariant)
            }
            setPadding(0, context.dp(8), 0, context.dp(8))
            actions.forEach { action ->
                addView(
                    TextView(context).apply {
                        text = action.label
                        setTextColor(if (action.label in destructiveLabels) colors.error else colors.onSurface)
                        textSize = 16f
                        typeface = Typeface.DEFAULT
                        gravity = Gravity.CENTER_VERTICAL
                        includeFontPadding = false
                        minHeight = context.dp(48)
                        background = ripple(context, ColorDrawable(Color.TRANSPARENT))
                        setPadding(context.dp(20), 0, context.dp(20), 0)
                        setOnClickListener {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            action.action.run()
                            popup?.dismiss()
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(48)),
                )
            }
        }
        popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = context.dp(8).toFloat()
        }
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val location = clampedPopupLocation(context, source, anchor, width, content.measuredHeight)
        popup.showAtLocation(source.rootView, Gravity.NO_GRAVITY, location.first, location.second)
        return popup
    }

    fun mapSettingsTextColor(context: Context, color: Int): Int {
        val colors = palette(context)
        return when (color) {
            CalmTheme.INK -> colors.onSurface
            CalmTheme.MUTED_INK -> colors.onSurfaceVariant
            CalmTheme.ACCENT -> colors.primary
            else -> color
        }
    }

    private fun ripple(context: Context, content: android.graphics.drawable.Drawable): RippleDrawable {
        return RippleDrawable(ColorStateList.valueOf(withAlpha(primary(context), 26)), content, null)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun clampedPopupLocation(
        context: Context,
        source: View,
        anchor: Pair<Int, Int>,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val bounds = Rect()
        source.rootView.getWindowVisibleDisplayFrame(bounds)
        val margin = context.dp(8)
        val below = anchor.second + context.dp(10)
        val above = anchor.second - height - context.dp(10)
        val minX = bounds.left + margin
        val maxX = maxOf(minX, bounds.right - width - margin)
        val minY = bounds.top + margin
        val maxY = maxOf(minY, bounds.bottom - height - margin)
        val x = (anchor.first - width / 2).coerceIn(minX, maxX)
        val y = if (below + height <= bounds.bottom - margin) below else above
        return x to y.coerceIn(minY, maxY)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
