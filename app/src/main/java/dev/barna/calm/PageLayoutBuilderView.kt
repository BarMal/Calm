package dev.barna.calm

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Visual page-layout editor: a draggable list of pages with schematic miniature previews, an
 * enable toggle, and a "set home" action per page. Reordering and toggles persist immediately.
 */
class PageLayoutBuilderView(
    context: Context,
    private val settings: LauncherSettings,
) : RecyclerView(context) {
    private val slots: MutableList<PageSlot> = settings.pageLayout().order.toMutableList()
    private val slotAdapter = SlotAdapter()

    init {
        layoutManager = LinearLayoutManager(context)
        adapter = slotAdapter
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        ItemTouchHelper(DragCallback()).attachToRecyclerView(this)
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            slots.add(to, slots.removeAt(from))
            slotAdapter.notifyItemMoved(from, to)
            settings.setPageLayoutOrder(slots)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        override fun isLongPressDragEnabled() = true
    }

    private inner class SlotAdapter : RecyclerView.Adapter<SlotViewHolder>() {
        override fun getItemCount() = slots.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SlotViewHolder()
        override fun onBindViewHolder(holder: SlotViewHolder, position: Int) = holder.bind(slots[position])
    }

    private inner class SlotViewHolder : RecyclerView.ViewHolder(LinearLayout(context)) {
        private val row = itemView as LinearLayout
        private val preview = FrameLayout(context)
        private val title = textView(18, CalmTheme.INK)
        private val home = textView(13, CalmTheme.INK).apply {
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            isClickable = true
        }
        private val enable = Switch(context)
        private var slot: PageSlot? = null

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, context.dp(6), 0, context.dp(6))
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            row.addView(preview, LinearLayout.LayoutParams(context.dp(58), context.dp(40)).apply { rightMargin = context.dp(14) })
            row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(home)
            row.addView(enable)
            home.setOnClickListener {
                slot?.let { settings.setDefaultHomeSlot(it); slotAdapter.notifyDataSetChanged() }
            }
            enable.setOnClickListener {
                slot?.let { settings.setPageSlotEnabled(it, enable.isChecked) }
            }
        }

        fun bind(pageSlot: PageSlot) {
            slot = pageSlot
            val layout = settings.pageLayout()
            val isHome = layout.defaultHome == pageSlot
            title.text = if (isHome) "${pageSlot.displayName()}  ·  Home" else pageSlot.displayName()
            home.text = if (isHome) "Home" else "Set home"
            home.alpha = if (isHome) 1f else 0.7f
            enable.isChecked = pageSlot !in layout.disabled
            renderPreview(pageSlot)
        }

        private fun renderPreview(pageSlot: PageSlot) {
            preview.removeAllViews()
            preview.background = rounded(CalmTheme.QUIET_GLASS, context.dp(8))
            preview.setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            preview.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            when (pageSlot) {
                PageSlot.APPS -> column.addView(grid())
                PageSlot.CONTACTS -> {
                    column.addView(bar(0.5f, CalmTheme.ACCENT))
                    column.addView(bar(0.85f))
                    column.addView(bar(0.7f))
                }
                PageSlot.NOTIFICATIONS -> {
                    column.addView(bar(0.95f))
                    column.addView(bar(0.8f))
                    column.addView(bar(0.6f))
                }
                else -> {
                    column.addView(bar(0.6f, CalmTheme.ACCENT))
                    column.addView(bar(0.9f))
                    column.addView(bar(0.75f))
                }
            }
        }
    }

    private fun bar(widthFraction: Float, color: Int = CalmTheme.MUTED_INK): View {
        // Preview is ~58dp wide with 6dp padding each side, leaving ~46dp of content width.
        val contentWidthDp = 46
        return View(context).apply {
            background = rounded(color, context.dp(2))
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(context.dp((contentWidthDp * widthFraction).toInt()), context.dp(4)).apply {
                topMargin = context.dp(3)
            }
        }
    }

    private fun grid(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            repeat(2) {
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        repeat(3) {
                            addView(
                                View(context).apply {
                                    background = rounded(CalmTheme.MUTED_INK, context.dp(2))
                                    alpha = 0.5f
                                },
                                LinearLayout.LayoutParams(context.dp(8), context.dp(8)).apply {
                                    rightMargin = context.dp(3)
                                    topMargin = context.dp(3)
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun textView(sizeSp: Int, color: Int): TextView {
        return TextView(context).apply {
            setTextColor(color)
            textSize = sizeSp.toFloat()
        }
    }

    private fun rounded(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }
    }
}
