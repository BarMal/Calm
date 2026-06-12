package dev.barna.calm

enum class DockStyle {
    CLASSIC,
    CARD,
    HYBRID,
}

enum class DockInteractionAction {
    OPEN_APP,
    OPEN_NOTIFICATION,
    OPEN_CONTEXT_MENU,
    EXPAND,
}

data class DockConfig(
    val enabled: Boolean = false,
    val style: DockStyle = DockStyle.CLASSIC,
    val itemCount: Int = DEFAULT_ITEM_COUNT,
    val itemSpan: Int = DEFAULT_ITEM_SPAN,
    val verticalPaddingDp: Int = DEFAULT_VERTICAL_PADDING_DP,
    val horizontalPaddingDp: Int = DEFAULT_HORIZONTAL_PADDING_DP,
    val tapAction: DockInteractionAction = DEFAULT_TAP_ACTION,
    val longPressAction: DockInteractionAction = DEFAULT_LONG_PRESS_ACTION,
) {
    companion object {
        const val DEFAULT_ITEM_COUNT = 5
        const val MIN_ITEM_COUNT = 3
        const val MAX_ITEM_COUNT = 8

        const val DEFAULT_ITEM_SPAN = 1
        const val MIN_ITEM_SPAN = 1
        const val MAX_ITEM_SPAN = 2
        const val ITEM_CELL_WIDTH_DP = 56
        const val CLASSIC_ITEM_HEIGHT_DP = 56
        const val FEATURED_DOCK_CONTENT_HEIGHT_DP = 90
        const val HYBRID_DOCK_CONTENT_HEIGHT_DP = 142

        const val DEFAULT_VERTICAL_PADDING_DP = 12
        const val MIN_VERTICAL_PADDING_DP = 0
        const val MAX_VERTICAL_PADDING_DP = 32

        const val DEFAULT_HORIZONTAL_PADDING_DP = 20
        const val MIN_HORIZONTAL_PADDING_DP = 0
        const val MAX_HORIZONTAL_PADDING_DP = 48
        val DEFAULT_TAP_ACTION = DockInteractionAction.OPEN_NOTIFICATION
        val DEFAULT_LONG_PRESS_ACTION = DockInteractionAction.EXPAND

        fun itemWidthDp(span: Int): Int {
            return ITEM_CELL_WIDTH_DP * span.coerceIn(MIN_ITEM_SPAN, MAX_ITEM_SPAN)
        }

        fun showsItemLabels(span: Int): Boolean {
            return span.coerceIn(MIN_ITEM_SPAN, MAX_ITEM_SPAN) > MIN_ITEM_SPAN
        }

        fun itemSpacingDp(horizontalPaddingDp: Int): Int {
            return horizontalPaddingDp
                .coerceIn(MIN_HORIZONTAL_PADDING_DP, MAX_HORIZONTAL_PADDING_DP) / 2
        }

        fun featuredDockHeightDp(includeClassicRow: Boolean, verticalPaddingDp: Int): Int {
            val contentHeight = if (includeClassicRow) {
                HYBRID_DOCK_CONTENT_HEIGHT_DP
            } else {
                FEATURED_DOCK_CONTENT_HEIGHT_DP
            }
            val verticalPadding = verticalPaddingDp.coerceIn(MIN_VERTICAL_PADDING_DP, MAX_VERTICAL_PADDING_DP)
            return contentHeight + (verticalPadding * 2)
        }
    }
}

internal object DockGesturePolicy {
    fun nextAppIndex(currentIndex: Int, appCount: Int, direction: Int): Int {
        if (appCount <= 0) return 0
        return normalizedIndex(currentIndex + direction, appCount)
    }

    private fun normalizedIndex(index: Int, size: Int): Int {
        return ((index % size) + size) % size
    }
}
