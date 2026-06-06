package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCopyFormatterTest {
    private val formatter = NotificationCopyFormatter()

    @Test
    fun notificationSummaryUsesSingularAndPlural() {
        assertEquals("1 active note", formatter.notificationSummary(1))
        assertEquals("0 active notes", formatter.notificationSummary(0))
        assertEquals("2 active notes", formatter.notificationSummary(2))
    }

    @Test
    fun dismissToastDistinguishesGroups() {
        assertEquals("Dismissed notification", formatter.groupedDismissToast(false))
        assertEquals("Dismissed notification group", formatter.groupedDismissToast(true))
    }

    @Test
    fun chapterToastsIncludeLabel() {
        assertEquals("Cleared Messages", formatter.chapterClearedToast("Messages"))
        assertEquals("Excluded Messages", formatter.excludedToast("Messages"))
    }
}
