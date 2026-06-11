package dev.barna.calm

import kotlin.math.ceil

object ClassicWidgetSpanCalculator {
    private const val CELL_WIDTH_DP = 78
    private const val CELL_HEIGHT_DP = 92

    fun spanFor(minWidthDp: Int, minHeightDp: Int): Pair<Int, Int> {
        val width = ceil(minWidthDp.coerceAtLeast(1) / CELL_WIDTH_DP.toFloat()).toInt()
            .coerceIn(1, ClassicGridItem.GRID_COLUMNS)
        val height = ceil(minHeightDp.coerceAtLeast(1) / CELL_HEIGHT_DP.toFloat()).toInt()
            .coerceIn(1, ClassicGridItem.DEFAULT_GRID_ROWS)
        return width to height
    }
}
