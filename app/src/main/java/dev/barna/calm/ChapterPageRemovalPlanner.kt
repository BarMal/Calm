package dev.barna.calm

class ChapterPageRemovalPlanner {
    fun selectPageAfterRemoval(pages: List<ChapterPage>, removedKey: String): String {
        val removedIndex = pages.indexOfFirst { it.key == removedKey }
        if (removedIndex < 0) return fallbackPage(pages)
        return pages.getOrNull(removedIndex + 1)?.key
            ?: pages.getOrNull(removedIndex - 1)?.key
            ?: fallbackPage(pages)
    }

    private fun fallbackPage(pages: List<ChapterPage>): String {
        return pages.firstOrNull { it.key == CalmTheme.OVERVIEW_KEY }?.key
            ?: pages.firstOrNull()?.key
            ?: CalmTheme.OVERVIEW_KEY
    }
}
