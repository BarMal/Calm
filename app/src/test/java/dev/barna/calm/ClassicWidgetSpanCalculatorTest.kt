package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassicWidgetSpanCalculatorTest {
    @Test
    fun spanForRoundsUpToGridCells() {
        assertEquals(2 to 2, ClassicWidgetSpanCalculator.spanFor(minWidthDp = 79, minHeightDp = 93))
    }

    @Test
    fun spanForClampsToGridBounds() {
        assertEquals(
            ClassicGridItem.GRID_COLUMNS to ClassicGridItem.DEFAULT_GRID_ROWS,
            ClassicWidgetSpanCalculator.spanFor(minWidthDp = 9999, minHeightDp = 9999),
        )
    }

    @Test
    fun spanForUsesAtLeastOneCell() {
        assertEquals(1 to 1, ClassicWidgetSpanCalculator.spanFor(minWidthDp = 0, minHeightDp = 0))
    }
}
