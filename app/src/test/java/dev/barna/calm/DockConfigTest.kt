package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockConfigTest {
    @Test
    fun defaultConfigIsDisabled() {
        assertFalse(DockConfig().enabled)
    }

    @Test
    fun defaultStyleIsClassic() {
        assertEquals(DockStyle.CLASSIC, DockConfig().style)
    }

    @Test
    fun defaultItemCountIsFive() {
        assertEquals(5, DockConfig().itemCount)
    }

    @Test
    fun defaultItemSpanIsOneCell() {
        assertEquals(1, DockConfig().itemSpan)
    }

    @Test
    fun defaultPaddingsAreNonZero() {
        assertTrue(DockConfig().verticalPaddingDp > 0)
        assertTrue(DockConfig().horizontalPaddingDp > 0)
    }

    @Test
    fun itemCountBoundsContainDefault() {
        assertTrue(DockConfig.MIN_ITEM_COUNT <= DockConfig.DEFAULT_ITEM_COUNT)
        assertTrue(DockConfig.DEFAULT_ITEM_COUNT <= DockConfig.MAX_ITEM_COUNT)
    }

    @Test
    fun paddingBoundsContainDefaults() {
        assertTrue(DockConfig.MIN_VERTICAL_PADDING_DP <= DockConfig.DEFAULT_VERTICAL_PADDING_DP)
        assertTrue(DockConfig.DEFAULT_VERTICAL_PADDING_DP <= DockConfig.MAX_VERTICAL_PADDING_DP)
        assertTrue(DockConfig.MIN_HORIZONTAL_PADDING_DP <= DockConfig.DEFAULT_HORIZONTAL_PADDING_DP)
        assertTrue(DockConfig.DEFAULT_HORIZONTAL_PADDING_DP <= DockConfig.MAX_HORIZONTAL_PADDING_DP)
    }

    @Test
    fun itemSpanBoundsContainDefault() {
        assertTrue(DockConfig.MIN_ITEM_SPAN <= DockConfig.DEFAULT_ITEM_SPAN)
        assertTrue(DockConfig.DEFAULT_ITEM_SPAN <= DockConfig.MAX_ITEM_SPAN)
    }

    @Test
    fun oneCellDockItemsHideLabels() {
        assertFalse(DockConfig.showsItemLabels(1))
        assertEquals(56, DockConfig.itemWidthDp(1))
    }

    @Test
    fun twoCellDockItemsShowLabels() {
        assertTrue(DockConfig.showsItemLabels(2))
        assertEquals(112, DockConfig.itemWidthDp(2))
    }

    @Test
    fun dockItemWidthClampsUnsupportedSpans() {
        assertEquals(56, DockConfig.itemWidthDp(0))
        assertEquals(112, DockConfig.itemWidthDp(99))
    }

    @Test
    fun enabledConfigCanBeConstructed() {
        val config = DockConfig(enabled = true, style = DockStyle.HYBRID, itemCount = 4, itemSpan = 2, verticalPaddingDp = 8, horizontalPaddingDp = 16)
        assertTrue(config.enabled)
        assertEquals(DockStyle.HYBRID, config.style)
        assertEquals(4, config.itemCount)
        assertEquals(2, config.itemSpan)
        assertEquals(8, config.verticalPaddingDp)
        assertEquals(16, config.horizontalPaddingDp)
    }

    @Test
    fun configsWithSameValuesAreEqual() {
        val a = DockConfig(enabled = true, itemCount = 4, verticalPaddingDp = 8, horizontalPaddingDp = 16)
        val b = DockConfig(enabled = true, itemCount = 4, verticalPaddingDp = 8, horizontalPaddingDp = 16)
        assertEquals(a, b)
    }

    @Test
    fun disabledConfigWithItemsIsDistinctFromEnabled() {
        val off = DockConfig(enabled = false, itemCount = 5)
        val on  = DockConfig(enabled = true,  itemCount = 5)
        assertTrue(off != on)
    }
}
