package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityCopyTest {
    @Test
    fun cardStackDescriptionIncludesTopCard() {
        assertEquals(
            "Card stack. Top card: Mail from Ana. Swipe vertically to browse cards.",
            AccessibilityCopy.cardStackDescription("Mail from Ana"),
        )
    }

    @Test
    fun cardStackDescriptionHandlesBlankContent() {
        assertEquals(
            "Card stack. Swipe vertically to browse cards.",
            AccessibilityCopy.cardStackDescription(""),
        )
    }

    @Test
    fun pageOverviewDescriptionExplainsTapAndMove() {
        assertEquals(
            "Overview page. Tap to open. Long press to move or manage.",
            AccessibilityCopy.pageOverviewCardDescription("Overview"),
        )
    }

    @Test
    fun notificationBadgeDescriptionPluralizes() {
        assertEquals("1 notification", AccessibilityCopy.notificationBadgeDescription(1))
        assertEquals("4 notifications", AccessibilityCopy.notificationBadgeDescription(4))
    }
}
