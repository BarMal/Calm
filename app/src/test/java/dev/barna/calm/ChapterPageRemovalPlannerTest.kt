package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPageRemovalPlannerTest {
    private val planner = ChapterPageRemovalPlanner()

    @Test
    fun selectsPageToTheRightOfRemovedPage() {
        val pages = listOf(
            page("apps"),
            page("overview"),
            page("messages"),
            page("mail"),
        )

        assertEquals("mail", planner.selectPageAfterRemoval(pages, "messages"))
    }

    @Test
    fun selectsPageToTheLeftWhenRemovedPageIsLast() {
        val pages = listOf(
            page("apps"),
            page("overview"),
            page("messages"),
        )

        assertEquals("overview", planner.selectPageAfterRemoval(pages, "messages"))
    }

    @Test
    fun fallsBackToOverviewWhenRemovedPageIsUnknown() {
        val pages = listOf(
            page("apps"),
            page(CalmTheme.OVERVIEW_KEY),
            page("messages"),
        )

        assertEquals(CalmTheme.OVERVIEW_KEY, planner.selectPageAfterRemoval(pages, "missing"))
    }

    private fun page(key: String): ChapterPage {
        return if (key == CalmTheme.OVERVIEW_KEY) {
            ChapterPage.overview(CalmTheme.OVERVIEW_KEY)
        } else {
            ChapterPage.appLibrary(key)
        }
    }
}
