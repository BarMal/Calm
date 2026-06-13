package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageOverviewSafetyTest {
    @Test
    fun reorderSlotsRejectsOutOfRangeIndices() {
        val order = listOf(PageSlot.OVERVIEW, PageSlot.APPS, PageSlot.CLASSIC_PAGES)

        assertNull(PageOverviewSafety.reorderedSlots(order, from = -1, to = 1))
        assertNull(PageOverviewSafety.reorderedSlots(order, from = 0, to = 3))
    }

    @Test
    fun reorderSlotsMovesWithinBounds() {
        val order = listOf(PageSlot.OVERVIEW, PageSlot.APPS, PageSlot.CLASSIC_PAGES)

        val reordered = PageOverviewSafety.reorderedSlots(order, from = 0, to = 2)

        assertEquals(listOf(PageSlot.APPS, PageSlot.CLASSIC_PAGES, PageSlot.OVERVIEW), reordered)
    }

    @Test
    fun targetEntryIndexKeepsCurrentIndexWhenCardWidthIsInvalid() {
        assertEquals(
            2,
            PageOverviewSafety.targetEntryIndex(entryIndex = 2, lastEntryIndex = 4, cardWidth = 0, dragX = 500f),
        )
    }

    @Test
    fun targetEntryIndexClampsToEntryBounds() {
        assertEquals(
            4,
            PageOverviewSafety.targetEntryIndex(entryIndex = 2, lastEntryIndex = 4, cardWidth = 200, dragX = 1000f),
        )
        assertEquals(
            0,
            PageOverviewSafety.targetEntryIndex(entryIndex = 2, lastEntryIndex = 4, cardWidth = 200, dragX = -1000f),
        )
    }
}
