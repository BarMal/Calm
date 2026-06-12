package dev.barna.calm

enum class CardStackSectionMode {
    TITLE_CARDS,
    FOLDERS,
}

enum class SectionTitleHeight {
    COMPACT,
    NORMAL,
    TALL,
}

enum class SectionTitleUnderline {
    OFF,
    TITLE,
    FULL,
}

data class SectionTitleCardStyle(
    val transparentBackground: Boolean = true,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val height: SectionTitleHeight = SectionTitleHeight.NORMAL,
    val underline: SectionTitleUnderline = SectionTitleUnderline.FULL,
)
