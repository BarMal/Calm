package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class PageArrangerTest {
    private val apps = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)
    private val pinned = ChapterPage.pinned(CalmTheme.PINNED_KEY, "II")
    private val overview = ChapterPage.overview(CalmTheme.OVERVIEW_KEY)
    private val classic = ChapterPage.classic(ClassicLauncherPageDefinition("classic-1", "Classic"), "III")
    private val chatA = ChapterPage.notifications(chapter("com.a", "A"), "IV")
    private val chatB = ChapterPage.notifications(chapter("com.b", "B"), "V")
    private val pages = listOf(apps, pinned, overview, chatA, chatB)

    @Test
    fun defaultArrangementReturnsPagesUnchanged() {
        assertEquals(pages, PageArranger.arrange(pages, LauncherPageLayout.DEFAULT))
    }

    @Test
    fun customOrderGroupsSlotsAndKeepsNotificationClusterTogether() {
        val layout = LauncherPageLayout(
            order = listOf(PageSlot.NOTIFICATIONS, PageSlot.CLASSIC_PAGES, PageSlot.OVERVIEW, PageSlot.APPS, PageSlot.PINNED),
            disabled = emptySet(),
            defaultHome = PageSlot.OVERVIEW,
        )
        assertEquals(listOf(chatA, chatB, classic, overview, apps, pinned), PageArranger.arrange(pages + classic, layout))
    }

    @Test
    fun classicPageUsesClassicPagesSlot() {
        assertEquals(PageSlot.CLASSIC_PAGES, PageArranger.slotOf(classic))
    }

    @Test
    fun disabledSlotIsDropped() {
        val layout = LauncherPageLayout(
            order = LauncherPageLayout.DEFAULT_ORDER,
            disabled = setOf(PageSlot.PINNED),
            defaultHome = PageSlot.OVERVIEW,
        )
        assertEquals(listOf(apps, overview, chatA, chatB), PageArranger.arrange(pages, layout))
    }

    @Test
    fun disablingEverythingFallsBackToOriginalPages() {
        val layout = LauncherPageLayout(
            order = LauncherPageLayout.DEFAULT_ORDER,
            disabled = PageSlot.entries.toSet(),
            defaultHome = PageSlot.OVERVIEW,
        )
        assertEquals(pages, PageArranger.arrange(pages, layout))
    }

    @Test
    fun firstEnabledHomeReturnsConfiguredHomeWhenEnabled() {
        val layout = LauncherPageLayout(
            order = listOf(PageSlot.APPS, PageSlot.OVERVIEW),
            disabled = setOf(PageSlot.APPS),
            defaultHome = PageSlot.OVERVIEW,
        )

        assertEquals(PageSlot.OVERVIEW, PageLayoutPolicy.firstEnabledHome(layout))
    }

    @Test
    fun firstEnabledHomeFallsBackWhenConfiguredHomeIsDisabled() {
        val layout = LauncherPageLayout(
            order = listOf(PageSlot.APPS, PageSlot.OVERVIEW, PageSlot.NOTIFICATIONS),
            disabled = setOf(PageSlot.OVERVIEW),
            defaultHome = PageSlot.OVERVIEW,
        )

        assertEquals(PageSlot.APPS, PageLayoutPolicy.firstEnabledHome(layout))
    }

    private fun chapter(packageName: String, label: String): AppChapter {
        return AppChapter(
            packageName = packageName,
            label = label,
            notifications = emptyList(),
            launchable = true,
            hueColor = 0xff123456.toInt(),
            isWorkProfile = false,
        )
    }
}
