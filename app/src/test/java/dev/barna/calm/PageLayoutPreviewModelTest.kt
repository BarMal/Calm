package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutPreviewModelTest {
    @Test
    fun segmentsMirrorConfiguredOrderAndState() {
        val layout = LauncherPageLayout(
            order = listOf(PageSlot.NOTIFICATIONS, PageSlot.OVERVIEW, PageSlot.APPS),
            disabled = setOf(PageSlot.APPS),
            defaultHome = PageSlot.OVERVIEW,
        )

        val segments = PageLayoutPreviewModel.segments(layout)

        assertEquals(listOf(PageSlot.NOTIFICATIONS, PageSlot.OVERVIEW, PageSlot.APPS), segments.map { it.slot })
        assertTrue(segments[1].home)
        assertTrue(segments[2].enabled)
    }

    @Test
    fun classicPagesSegmentUsesGridShortLabel() {
        val segments = PageLayoutPreviewModel.segments(
            LauncherPageLayout(
                order = listOf(PageSlot.CLASSIC_PAGES),
                disabled = emptySet(),
                defaultHome = PageSlot.CLASSIC_PAGES,
            ),
        )

        assertEquals("Classic", segments.single().label)
        assertEquals("Grid", segments.single().shortLabel)
        assertTrue(segments.single().home)
    }
}
