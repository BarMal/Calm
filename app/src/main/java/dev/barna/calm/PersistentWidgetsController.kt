package dev.barna.calm

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Renders the persistent-widgets band — widgets shown across every page (outside the pager). */
class PersistentWidgetsController(
    private val activity: MainActivity,
    private val widgetHost: WidgetHostController,
    private val drawables: CalmDrawables,
    private val widgetIds: () -> List<Int>,
    private val requestAddWidget: () -> Unit,
    private val removeWidget: (Int) -> Unit,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    /** The band, or null when there is nothing to show (no widgets and not in an add-able state). */
    fun buildBand(): View {
        val column = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.GLASS, activity.dp(24))
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
        }
        val ids = widgetIds()
        ids.forEach { id ->
            widgetHost.createView(id)?.let { hostView ->
                column.addView(widgetSlot(id, hostView))
            }
        }
        column.addView(
            label(if (ids.isEmpty()) "＋  Add persistent widget" else "＋  Add", 14, CalmTheme.INK, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
                isClickable = true
                setOnClickListener { requestAddWidget() }
            },
        )
        return column
    }

    private fun widgetSlot(widgetId: Int, hostView: View): View {
        return FrameLayout(activity).apply {
            addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            isLongClickable = true
            setOnLongClickListener { removeWidget(widgetId); true }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(8)
            }
        }
    }
}
