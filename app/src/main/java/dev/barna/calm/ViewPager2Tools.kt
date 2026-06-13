package dev.barna.calm

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

internal fun ViewPager2.recyclerViewOrNull(): RecyclerView? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is RecyclerView) return child
    }
    return null
}
