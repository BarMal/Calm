package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {
    @Test
    fun titleFilterMatchesPackageAndExactNormalizedTitle() {
        val filter = NotificationFilter.title("pkg", "Calendar")

        assertTrue(filter.matches(notification(packageName = "pkg", title = " calendar ")))
        assertFalse(filter.matches(notification(packageName = "other", title = "Calendar")))
        assertFalse(filter.matches(notification(packageName = "pkg", title = "Other")))
    }

    @Test
    fun bodyFilterUsesResolvedBodyText() {
        val filter = NotificationFilter.body("pkg", "Meeting soon")

        assertTrue(filter.matches(notification(packageName = "pkg", text = "", subText = "meeting soon")))
    }

    @Test
    fun containsFilterMatchesTitleSubstring() {
        val filter = NotificationFilter.titleContains(AppIdentity.packageOnly("pkg").notificationSourceKey, "pkg", "WhatsApp messages")

        assertTrue(filter.matches(notification(packageName = "pkg", title = "6 WhatsApp messages from 2 chats")))
        assertFalse(filter.matches(notification(packageName = "pkg", title = "Signal messages")))
    }

    @Test
    fun wildcardFilterMatchesIgnoredTitleSegments() {
        val filter = NotificationFilter.titleWildcard(
            AppIdentity.packageOnly("pkg").notificationSourceKey,
            "pkg",
            "{?} WhatsApp messages from {?} chats",
        )

        assertTrue(filter.matches(notification(packageName = "pkg", title = "6 WhatsApp messages from 2 chats")))
        assertTrue(filter.matches(notification(packageName = "pkg", title = "12 WhatsApp messages from 4 chats")))
        assertFalse(filter.matches(notification(packageName = "pkg", title = "12 Signal messages from 4 chats")))
    }

    @Test
    fun emptyContentFilterMatchesOnlyBlankTitleAndBlankBody() {
        val filter = NotificationFilter.emptyContent(AppIdentity.notificationKey("pkg", 10), "pkg")

        assertTrue(filter.matches(notification(packageName = "pkg", userSerial = 10, title = "", text = "", subText = "")))
        assertFalse(filter.matches(notification(packageName = "pkg", userSerial = 10, title = "Title", text = "", subText = "")))
        assertFalse(filter.matches(notification(packageName = "pkg", userSerial = 10, title = "", text = "Body", subText = "")))
        assertFalse(filter.matches(notification(packageName = "pkg", userSerial = 0, title = "", text = "", subText = "")))
    }

    @Test
    fun filterRoundTripsThroughEncodedPreferenceValue() {
        val filter = NotificationFilter.body("pkg", "Body text")

        assertEquals(filter, NotificationFilter.decode(filter.encode()))
    }

    @Test
    fun flexibleFilterRoundTripsThroughEncodedPreferenceValue() {
        val filter = NotificationFilter.bodyWildcard(
            AppIdentity.packageOnly("pkg").notificationSourceKey,
            "pkg",
            "Order {?} is ready",
        )

        assertEquals(filter, NotificationFilter.decode(filter.encode()))
    }

    @Test
    fun digitRunsCanBeGeneralizedIntoWildcardPattern() {
        assertEquals(
            "{?} WhatsApp messages from {?} chats",
            NotificationFilterPattern.generalizeNumbers("6 WhatsApp messages from 2 chats"),
        )
        assertEquals(
            "Invoice {?} / {?}",
            NotificationFilterPattern.generalizeNumbers("Invoice 12345 / 99"),
        )
    }

    @Test
    fun profileAwareFilterOnlyMatchesThatNotificationSource() {
        val workSource = AppIdentity.notificationKey("pkg", 10)
        val filter = NotificationFilter.title(workSource, "pkg", "Calendar")

        assertTrue(filter.matches(notification(packageName = "pkg", userSerial = 10, title = "calendar")))
        assertFalse(filter.matches(notification(packageName = "pkg", userSerial = 0, title = "calendar")))
    }

    private fun notification(
        packageName: String = "pkg",
        userSerial: Long = AppIdentity.LEGACY_USER_SERIAL,
        title: String = "",
        text: String = "",
        subText: String = "",
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = "key",
            packageName = packageName,
            userSerial = userSerial,
            title = title,
            text = text,
            subText = subText,
            conversationTitle = "",
            postTime = 1L,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}
