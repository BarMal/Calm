package dev.barna.calm

import kotlin.math.roundToInt

internal object PageOverviewSafety {
    fun reorderedSlots(order: List<PageSlot>, from: Int, to: Int): List<PageSlot>? {
        if (from !in order.indices || to !in order.indices || from == to) return null
        return order.toMutableList().apply {
            val moved = removeAt(from)
            add(to.coerceIn(0, size), moved)
        }
    }

    fun targetEntryIndex(entryIndex: Int, lastEntryIndex: Int, cardWidth: Int, dragX: Float): Int {
        if (lastEntryIndex < 0) return 0
        if (cardWidth <= 0) return entryIndex.coerceIn(0, lastEntryIndex)
        return (entryIndex + (dragX / (cardWidth * 0.72f)).roundToInt())
            .coerceIn(0, lastEntryIndex)
    }
}
