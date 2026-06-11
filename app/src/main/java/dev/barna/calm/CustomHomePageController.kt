package dev.barna.calm

import android.app.AlertDialog
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Renders a custom home page: a fixed-column grid of app icons and widgets, drag-to-reposition. */
class CustomHomePageController(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val drawables: CalmDrawables,
    private val widgetHost: WidgetHostController,
    private val appEntries: () -> List<AppEntry>,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val requestAddWidget: () -> Unit,
    private val onChanged: () -> Unit,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    private val cellHeight get() = activity.dp(92)

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
            when (item.type) {
                GridItemType.APP -> byApp[item.ref]?.let { app -> gridView.addView(appCell(item, app)) }
                GridItemType.WIDGET -> item.ref.toIntOrNull()?.let { id ->
                    widgetHost.createView(id)?.let { hostView -> gridView.addView(widgetCell(item, hostView)) }
                }
            }
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

        val removeZone = removeZone()
        page.addView(removeZone)
        installDrag(page, gridView, removeZone, grid)
        return page
    }

    private fun installDrag(page: View, gridView: GridLayout, removeZone: View, grid: HomeGrid) {
        page.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> { removeZone.visibility = View.VISIBLE; true }
                DragEvent.ACTION_DRAG_ENDED -> { removeZone.visibility = View.GONE; true }
                else -> true
            }
        }
        gridView.setOnDragListener { view, event ->
            if (event.action == DragEvent.ACTION_DROP) handleGridDrop(view as GridLayout, grid, event)
            true
        }
        removeZone.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) (event.localState as? HomeGridItem)?.let { removeItem(grid, it) }
            true
        }
    }

    private fun handleGridDrop(gridView: GridLayout, grid: HomeGrid, event: DragEvent) {
        val item = event.localState as? HomeGridItem ?: return
        val cellWidth = (gridView.width / grid.columns).coerceAtLeast(1)
        val column = (event.x / cellWidth).toInt().coerceIn(0, grid.columns - 1)
        val row = (event.y / cellHeight.coerceAtLeast(1)).toInt().coerceAtLeast(0)
        if (column == item.column && row == item.row) return
        if (grid.canPlace(item, column, row)) {
            settings.setHomeGrid(grid.moving(item, column, row))
            onChanged()
        }
    }

    private fun removeItem(grid: HomeGrid, item: HomeGridItem) {
        if (item.type == GridItemType.WIDGET) item.ref.toIntOrNull()?.let { widgetHost.deleteWidgetId(it) }
        settings.setHomeGrid(grid.without(item))
        onChanged()
    }

    private fun startDrag(view: View, item: HomeGridItem) {
        view.startDragAndDrop(ClipData.newPlainText("", ""), View.DragShadowBuilder(view), item, 0)
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
            addView(pillButton("＋ App") { showAddAppDialog(grid) })
            addView(pillButton("＋ Widget") { requestAddWidget() }.apply {
                (layoutParams as LinearLayout.LayoutParams).leftMargin = activity.dp(8)
            })
        }
    }

    private fun pillButton(text: String, onClick: () -> Unit): TextView {
        return label(text, 14, CalmTheme.INK, Typeface.NORMAL).apply {
            setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(14))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun removeZone(): View {
        return label("✕  Drag here to remove", 14, CalmTheme.INK, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = activity.dp(10)
            }
        }
    }

    private fun appCell(item: HomeGridItem, app: AppEntry): View {
        return LinearLayout(activity).apply {
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
            setOnLongClickListener { startDrag(this, item); true }
            layoutParams = cellParams(item)
        }
    }

    private fun widgetCell(item: HomeGridItem, hostView: View): View {
        return FrameLayout(activity).apply {
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(14))
            setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnLongClickListener { startDrag(this, item); true }
            layoutParams = cellParams(item)
        }
    }

    private fun cellParams(item: HomeGridItem): GridLayout.LayoutParams {
        return GridLayout.LayoutParams(
            GridLayout.spec(item.row, item.rowSpan),
            GridLayout.spec(item.column, item.columnSpan, GridLayout.FILL, 1f),
        ).apply {
            width = 0
            height = cellHeight * item.rowSpan
            setMargins(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2))
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
