package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertFalse(segments[2].enabled)
    }

}
