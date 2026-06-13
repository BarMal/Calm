package dev.barna.calm

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class ChapterCarouselController(
    private val activity: MainActivity,
    private val notificationCardDisplayCache: NotificationCardDisplayCache,
    private val onNavigateToPage: (Int) -> Unit,
) {
    private var carousel: HorizontalScrollView? = null
    private var carouselRow: LinearLayout? = null
    private var selectedPosition = -1
    private var paddedCarouselWidth = -1
    private var lastScrollTarget = Int.MIN_VALUE

    fun clear() {
        carousel = null
        carouselRow = null
        selectedPosition = -1
        paddedCarouselWidth = -1
        lastScrollTarget = Int.MIN_VALUE
    }

    fun create(pages: List<ChapterPage>, selectedPosition: Int, style: ChapterSpineStyle): View {
        paddedCarouselWidth = -1
        lastScrollTarget = Int.MIN_VALUE
        val spine = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = activity.dp(12)
                bottomMargin = activity.dp(18)
            }
        }
        spine.addView(spineLine())
        carousel = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, activity.dp(3), 0, activity.dp(3))
            setBackgroundColor(Color.TRANSPARENT)
        }
        carouselRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        carousel?.addView(
            carouselRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        spine.addView(carousel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        spine.addView(spineLine())
        renderItems(pages, selectedPosition, style)
        carousel?.post {
            updateCenterPadding()
            centerItem(selectedPosition, smooth = false)
        }
        return spine
    }

    fun update(pages: List<ChapterPage>, position: Int, style: ChapterSpineStyle) {
        val c = carousel ?: return
        if (carouselRow == null || pages.isEmpty()) return
        updateSelection(pages, position, style)
        c.post {
            updateCenterPadding()
            centerItem(position)
        }
    }

    fun scrollToPosition(position: Int, offset: Float) {
        val row = carouselRow ?: return
        val c = carousel ?: return
        if (row.childCount == 0 || row.childCount <= position) return
        updateCenterPadding()
        val current = row.getChildAt(position)
        val currentCenter = c.paddingLeft + current.left + (current.width / 2f)
        val nextCenter = if (position + 1 < row.childCount) {
            val next = row.getChildAt(position + 1)
            c.paddingLeft + next.left + (next.width / 2f)
        } else {
            currentCenter
        }
        val interpolatedCenter = currentCenter + ((nextCenter - currentCenter) * offset.coerceIn(0f, 1f))
        val target = (interpolatedCenter - (c.width / 2f)).toInt().coerceAtLeast(0)
        scrollCarouselTo(c, target, smooth = false)
    }

    private fun renderItems(pages: List<ChapterPage>, selectedPosition: Int, style: ChapterSpineStyle) {
        val row = carouselRow ?: return
        row.removeAllViews()
        this.selectedPosition = selectedPosition
        lastScrollTarget = Int.MIN_VALUE
        pages.forEachIndexed { index, page ->
            val selected = index == selectedPosition
            val item = carouselItem(page, index, selected, style)
            row.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = activity.dp(1)
                rightMargin = activity.dp(1)
            })
        }
        row.post {
            updateCenterPadding()
            centerItem(selectedPosition)
        }
    }

    private fun updateSelection(pages: List<ChapterPage>, selectedPosition: Int, style: ChapterSpineStyle) {
        val row = carouselRow ?: return
        if (this.selectedPosition == selectedPosition) return
        val previousPosition = this.selectedPosition
        this.selectedPosition = selectedPosition
        if (previousPosition in 0 until row.childCount) {
            configureItem(row.getChildAt(previousPosition) as TextView, pages[previousPosition], previousPosition, false, style)
        }
        if (selectedPosition in 0 until row.childCount) {
            configureItem(row.getChildAt(selectedPosition) as TextView, pages[selectedPosition], selectedPosition, true, style)
        }
    }

    private fun carouselItem(page: ChapterPage, index: Int, selected: Boolean, style: ChapterSpineStyle): TextView {
        return TextView(activity).apply {
            textSize = (if (selected) 18 else 14).toFloat()
            setTextColor(if (selected) CalmTheme.INK else CalmTheme.MUTED_INK)
            typeface = Typeface.DEFAULT
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = true
            configureItem(this, page, index, selected, style)
        }
    }

    private fun configureItem(item: TextView, page: ChapterPage, index: Int, selected: Boolean, style: ChapterSpineStyle) {
        item.apply {
            text = ChapterSpineFormatter.displayText(page, style).orEmpty()
            textSize = (if (selected) 18 else 14).toFloat()
            setTextColor(if (selected) CalmTheme.INK else CalmTheme.MUTED_INK)
            typeface = Typeface.DEFAULT
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = android.view.Gravity.CENTER
            setSingleLine(style.titleMode != ChapterSpineTitleMode.SPLIT)
            maxLines = if (style.titleMode == ChapterSpineTitleMode.SPLIT) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(activity.dp(if (selected) 12 else 8), activity.dp(8), activity.dp(if (selected) 12 else 8), activity.dp(8))
            alpha = if (selected) 1f else 0.5f
            background = null
            setCompoundDrawables(null, null, null, null)
            page.chapter?.let { chapter ->
                compoundDrawablePadding = activity.dp(6)
                notificationCardDisplayCache.cachedChapterMaskedIcon(chapter)?.let { icon ->
                    setCompoundDrawables(icon.toSizedDrawable(activity, activity.dp(if (selected) 20 else 16)), null, null, null)
                }
            }
            maxWidth = activity.dp(if (selected) 176 else 126)
            minWidth = activity.dp(if (selected) 118 else 74)
            setOnClickListener { onNavigateToPage(index) }
        }
    }

    private fun centerItem(position: Int, smooth: Boolean = true) {
        val row = carouselRow ?: return
        val c = carousel ?: return
        if (row.childCount <= position) return
        updateCenterPadding()
        val child = row.getChildAt(position)
        val viewportCenter = c.width / 2
        val childCenter = c.paddingLeft + child.left + (child.width / 2)
        val target = maxOf(0, childCenter - viewportCenter)
        scrollCarouselTo(c, target, smooth)
    }

    private fun scrollCarouselTo(c: HorizontalScrollView, target: Int, smooth: Boolean) {
        if (target == lastScrollTarget) return
        lastScrollTarget = target
        if (smooth) {
            c.smoothScrollTo(target, 0)
        } else {
            c.scrollTo(target, 0)
        }
    }

    private fun updateCenterPadding() {
        val c = carousel ?: return
        if (c.width <= 0) return
        if (paddedCarouselWidth == c.width) return
        val sidePadding = c.width / 2
        if (c.paddingLeft == sidePadding && c.paddingRight == sidePadding) {
            paddedCarouselWidth = c.width
            return
        }
        c.setPadding(sidePadding, c.paddingTop, sidePadding, c.paddingBottom)
        paddedCarouselWidth = c.width
        lastScrollTarget = Int.MIN_VALUE
    }

    private fun spineLine(): View {
        return View(activity).apply {
            setBackgroundColor(Color.argb(52, Color.red(CalmTheme.ACCENT), Color.green(CalmTheme.ACCENT), Color.blue(CalmTheme.ACCENT)))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, activity.dp(1)))
        }
    }
}
