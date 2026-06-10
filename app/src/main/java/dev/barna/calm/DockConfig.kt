package dev.barna.calm

data class DockConfig(
    val enabled: Boolean = false,
    val itemCount: Int = DEFAULT_ITEM_COUNT,
    val verticalPaddingDp: Int = DEFAULT_VERTICAL_PADDING_DP,
    val horizontalPaddingDp: Int = DEFAULT_HORIZONTAL_PADDING_DP,
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val iconSpacingDp: Int = DEFAULT_ICON_SPACING_DP,
    val iconCornerRadiusDp: Int = DEFAULT_ICON_CORNER_DP,
) {
    companion object {
        const val DEFAULT_ITEM_COUNT = 5
        const val MIN_ITEM_COUNT = 3
        const val MAX_ITEM_COUNT = 8

        const val DEFAULT_VERTICAL_PADDING_DP = 12
        const val MIN_VERTICAL_PADDING_DP = 0
        const val MAX_VERTICAL_PADDING_DP = 32

        const val DEFAULT_HORIZONTAL_PADDING_DP = 20
        const val MIN_HORIZONTAL_PADDING_DP = 0
        const val MAX_HORIZONTAL_PADDING_DP = 48

        const val DEFAULT_ICON_SIZE_DP = 44
        const val MIN_ICON_SIZE_DP = 32
        const val MAX_ICON_SIZE_DP = 64

        const val DEFAULT_ICON_SPACING_DP = 6
        const val MIN_ICON_SPACING_DP = 0
        const val MAX_ICON_SPACING_DP = 24

        const val DEFAULT_ICON_CORNER_DP = 12
        const val MIN_ICON_CORNER_DP = 0
        const val MAX_ICON_CORNER_DP = 32
    }
}
