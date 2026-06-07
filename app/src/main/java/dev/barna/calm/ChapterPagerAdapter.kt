package dev.barna.calm

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class ChapterPagerAdapter(
    private val pages: List<ChapterPage>,
    private val pageFactory: (ChapterPage) -> View,
) : RecyclerView.Adapter<PageHolder>() {
    private val pageViews = LinkedHashMap<String, View>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val container = FrameLayout(parent.context)
        container.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return PageHolder(container)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val page = pages[position]
        val view = preload(position) ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        holder.container.removeAllViews()
        holder.container.addView(view, matchParentParams())
    }

    fun preload(position: Int): View? {
        val page = pages.getOrNull(position) ?: return null
        return pageViews.getOrPut(page.key) { pageFactory(page) }
    }

    override fun onViewRecycled(holder: PageHolder) {
        holder.container.removeAllViews()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = pages.size
}
