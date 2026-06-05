package dev.barna.calm

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class ChapterPagerAdapter(
    private val pages: List<ChapterPage>,
    private val pageFactory: (ChapterPage) -> View,
) : RecyclerView.Adapter<PageHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val container = FrameLayout(parent.context)
        container.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return PageHolder(container)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.container.removeAllViews()
        holder.container.addView(pageFactory(pages[position]), matchParentParams())
    }

    override fun getItemCount(): Int = pages.size
}
