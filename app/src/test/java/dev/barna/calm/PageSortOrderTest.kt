package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PageSortOrderTest {
    @Test
    fun fourDistinctValues() {
        assertEquals(4, PageSortOrder.entries.size)
    }

    @Test
    fun defaultIsAppNameAsc() {
        assertEquals(PageSortOrder.APP_NAME_ASC, PageSortOrder.DEFAULT)
    }

    @Test
    fun decodeEncodesRoundTrip() {
        for (order in PageSortOrder.entries) {
            assertEquals(order, PageSortOrder.decode(order.name))
        }
    }

    @Test
    fun decodeUnknownReturnsDefault() {
        assertEquals(PageSortOrder.DEFAULT, PageSortOrder.decode("completely_unknown"))
    }

    @Test
    fun appNameAscAndDescAreDistinct() {
        assertNotEquals(PageSortOrder.APP_NAME_ASC, PageSortOrder.APP_NAME_DESC)
    }

    @Test
    fun notificationAgeVariantsAreDistinct() {
        assertNotEquals(PageSortOrder.NOTIFICATION_AGE_NEWEST, PageSortOrder.NOTIFICATION_AGE_OLDEST)
    }
}
