package dev.barna.calm

enum class ChapterSpineTitleMode {
    SPLIT,
    COMBINED,
    TITLE_ONLY,
    SPINE_ONLY,
    ICONS_ONLY,
    HIDDEN,
}

enum class ChapterSpinePosition {
    TOP,
    BOTTOM,
    HIDDEN,
}

data class ChapterSpineStyle(
    val titleMode: ChapterSpineTitleMode = ChapterSpineTitleMode.COMBINED,
    val position: ChapterSpinePosition = ChapterSpinePosition.TOP,
) {
    val visible: Boolean get() = titleMode != ChapterSpineTitleMode.HIDDEN && position != ChapterSpinePosition.HIDDEN

    companion object {
        val DEFAULT = ChapterSpineStyle(titleMode = ChapterSpineTitleMode.TITLE_ONLY)
    }
}

object ChapterSpineFormatter {
    fun displayText(page: ChapterPage, style: ChapterSpineStyle): String? {
        if (!style.visible) return null
        return when (style.titleMode) {
            ChapterSpineTitleMode.SPLIT -> "${page.marker}\n${page.title}"
            ChapterSpineTitleMode.COMBINED -> "${page.marker}  ${page.title}"
            ChapterSpineTitleMode.TITLE_ONLY -> page.title
            ChapterSpineTitleMode.SPINE_ONLY -> page.marker
            ChapterSpineTitleMode.ICONS_ONLY -> if (page.chapter == null) page.marker else ""
            ChapterSpineTitleMode.HIDDEN -> null
        }
    }
}
