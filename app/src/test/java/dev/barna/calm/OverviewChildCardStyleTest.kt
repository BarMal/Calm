package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverviewChildCardStyleTest {
    @Test
    fun sameNotificationGetsStableHue() {
        val item = NotificationCardItem(listOf(notification("one", "Alex", "Ping")))

        val first = OverviewChildCardStyle.hueColor(0xFF223344.toInt(), item)
        val second = OverviewChildCardStyle.hueColor(0xFF223344.toInt(), item)

        assertEquals(first, second)
    }

    @Test
    fun differentNotificationsCanGetDifferentHuesWithinSameChapter() {
        val left = NotificationCardItem(listOf(notification("one", "Alex", "Ping")))
        val right = NotificationCardItem(listOf(notification("two", "Blair", "Lunch?")))

        assertNotEquals(
            OverviewChildCardStyle.hueColor(0xFF223344.toInt(), left),
            OverviewChildCardStyle.hueColor(0xFF223344.toInt(), right),
        )
    }

    private fun notification(
        key: String,
        title: String,
        text: String,
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = key,
            packageName = "chat.pkg",
            title = title,
            text = text,
            subText = "",
            conversationTitle = "",
            postTime = 10L,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}
