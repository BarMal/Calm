package dev.barna.calm

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Renders the Widgets page: a scrollable column of hosted home-screen widgets plus an add button. */
class WidgetsPageController(
    private val activity: MainActivity,
    private val widgetHost: WidgetHostController,
    private val drawables: CalmDrawables,
    private val widgetIds: () -> List<Int>,
    private val requestAddWidget: () -> Unit,
    private val removeWidget: (Int) -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    fun buildPage(): LinearLayout {
        val page = barePagePanel(activity.dp(20))
        page.addView(
            label("Widgets", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                tag = CalmAnimationTags.CHROME
                setPadding(0, activity.dp(8), 0, activity.dp(14))
            },
        )
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        page.addView(
            ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val ids = widgetIds()
        if (ids.isEmpty()) {
            column.addView(note("No widgets yet. Tap “Add widget” to place one."))
        } else {
            ids.forEach { id ->
                widgetHost.createView(id)?.let { hostView -> column.addView(widgetSlot(id, hostView)) }
            }
        }
        column.addView(addButton())
        return page
    }

    private fun widgetSlot(widgetId: Int, hostView: View): View {
        return FrameLayout(activity).apply {
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
            addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            isLongClickable = true
            setOnLongClickListener {
                removeWidget(widgetId)
                true
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        }
    }

    private fun addButton(): View {
        return label("＋  Add widget", 16, CalmTheme.INK, Typeface.NORMAL).apply {
            tag = CalmAnimationTags.CHROME
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            isClickable = true
            setOnClickListener { requestAddWidget() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun note(message: String): TextView {
        return label(message, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        }
    }
}
