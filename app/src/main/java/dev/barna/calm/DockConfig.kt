package dev.barna.calm

data class DockConfig(
    val enabled: Boolean = false,
    val itemCount: Int = DEFAULT_ITEM_COUNT,
    val itemSpan: Int = DEFAULT_ITEM_SPAN,
    val verticalPaddingDp: Int = DEFAULT_VERTICAL_PADDING_DP,
    val horizontalPaddingDp: Int = DEFAULT_HORIZONTAL_PADDING_DP,
) {
    companion object {
        const val DEFAULT_ITEM_COUNT = 5
        const val MIN_ITEM_COUNT = 3
        const val MAX_ITEM_COUNT = 8

        const val DEFAULT_ITEM_SPAN = 1
        const val MIN_ITEM_SPAN = 1
        const val MAX_ITEM_SPAN = 2
        const val ITEM_CELL_WIDTH_DP = 56

        const val DEFAULT_VERTICAL_PADDING_DP = 12
        const val MIN_VERTICAL_PADDING_DP = 0
        const val MAX_VERTICAL_PADDING_DP = 32

        const val DEFAULT_HORIZONTAL_PADDING_DP = 20
        const val MIN_HORIZONTAL_PADDING_DP = 0
        const val MAX_HORIZONTAL_PADDING_DP = 48

        fun itemWidthDp(span: Int): Int {
            return ITEM_CELL_WIDTH_DP * span.coerceIn(MIN_ITEM_SPAN, MAX_ITEM_SPAN)
        }

        fun showsItemLabels(span: Int): Boolean {
            return span.coerceIn(MIN_ITEM_SPAN, MAX_ITEM_SPAN) > MIN_ITEM_SPAN
        }
    }
}
