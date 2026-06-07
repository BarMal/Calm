package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPageSelectionResolverTest {
    private val resolver = ChapterPageSelectionResolver()

    @Test
    fun restoresSelectedPageWhenItStillExists() {
        val pages = listOf(
            ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY),
            ChapterPage.overview(CalmTheme.OVERVIEW_KEY),
            ChapterPage.notifications(chapter("mail"), "III"),
        )

        val selection = resolver.resolve(pages, "mail")

        assertEquals(2, selection.index)
        assertEquals("mail", selection.key)
    }

    @Test
    fun fallsBackToOverviewIndexWhenSelectedPageDisappears() {
        val pages = listOf(
            ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY),
            ChapterPage.overview(CalmTheme.OVERVIEW_KEY),
        )

        val selection = resolver.resolve(pages, "missing")

        assertEquals(1, selection.index)
        assertEquals(CalmTheme.OVERVIEW_KEY, selection.key)
    }

    @Test
    fun fallsBackToFirstPageWhenOverviewIsUnavailable() {
        val pages = listOf(ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY))

        val selection = resolver.resolve(pages, "missing")

        assertEquals(0, selection.index)
        assertEquals(CalmTheme.APP_LIBRARY_KEY, selection.key)
    }

    private fun chapter(key: String): AppChapter {
        return AppChapter(
            packageName = key,
            label = key,
            notifications = emptyList(),
            launchable = true,
            hueColor = 0,
            identityKey = key,
        )
    }
}
