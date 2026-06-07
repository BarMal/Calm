package dev.barna.calm

class ChapterPageSelectionResolver {
    fun resolve(
        pages: List<ChapterPage>,
        selectedKey: String,
        fallbackKey: String = CalmTheme.OVERVIEW_KEY,
    ): ChapterPageSelection {
        pages.indexOfFirst { page -> page.key == selectedKey }
            .takeIf { index -> index >= 0 }
            ?.let { index -> return ChapterPageSelection(index, selectedKey) }

        val fallbackIndex = pages.indexOfFirst { page -> page.key == fallbackKey }
            .takeIf { index -> index >= 0 }
            ?: 0
        val resolvedKey = pages.getOrNull(fallbackIndex)?.key ?: fallbackKey
        return ChapterPageSelection(fallbackIndex, resolvedKey)
    }
}

data class ChapterPageSelection(
    val index: Int,
    val key: String,
)
