package dev.barna.calm

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Renders a custom home page: a fixed-column grid of app icons placed by the user. */
class CustomHomePageController(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val drawables: CalmDrawables,
    private val appEntries: () -> List<AppEntry>,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val onChanged: () -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    fun buildPage(): LinearLayout {
        val grid = settings.homeGrid()
        val page = barePagePanel(activity.dp(20))
        page.addView(headerRow(grid))

        val gridView = GridLayout(activity).apply {
            columnCount = grid.columns
            clipChildren = false
            clipToPadding = false
        }
        val byApp = appEntries().associateBy { it.identityKey }
        grid.items.forEach { item ->
            byApp[item.ref]?.let { app -> gridView.addView(cell(grid, item, app)) }
        }
        page.addView(
            ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                addView(gridView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return page
    }

    private fun headerRow(grid: HomeGrid): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(8), 0, activity.dp(16))
            addView(
                label("Home", 30, CalmTheme.INK, Typeface.NORMAL),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                label("＋  Add app", 14, CalmTheme.INK, Typeface.NORMAL).apply {
                    setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
                    background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(14))
                    isClickable = true
                    setOnClickListener { showAddAppDialog(grid) }
                },
            )
        }
    }

    private fun cell(grid: HomeGrid, item: HomeGridItem, app: AppEntry): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(10))
            addView(
                ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    resolveIcon(app)?.let { setImageBitmap(it) }
                },
                LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)),
            )
            addView(
                label(app.label, 11, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, activity.dp(4), 0, 0)
                },
            )
            setOnClickListener { openAppEntry(app) }
            setOnLongClickListener {
                settings.setHomeGrid(grid.withoutItem(item.column, item.row))
                onChanged()
                true
            }
        }
        return content.apply {
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(item.row),
                GridLayout.spec(item.column, GridLayout.FILL, 1f),
            ).apply {
                width = 0
                height = activity.dp(92)
            }
        }
    }

    private fun showAddAppDialog(grid: HomeGrid) {
        val free = grid.firstFreeCell() ?: return
        val apps = appEntries()
        if (apps.isEmpty()) return
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Add app")
            .setItems(labels) { dialog, which ->
                val app = apps[which]
                settings.setHomeGrid(grid.withItem(HomeGridItem(GridItemType.APP, app.identityKey, free.first, free.second)))
                onChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
