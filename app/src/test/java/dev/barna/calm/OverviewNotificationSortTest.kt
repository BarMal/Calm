package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewNotificationSortTest {
    @Test
    fun chaptersAreSortedByMostRecentNotificationDescending() {
        val older = chapter("com.old", notifications = listOf(notification(postTime = 1000L)))
        val newer = chapter("com.new", notifications = listOf(notification(postTime = 5000L)))
        val middle = chapter("com.mid", notifications = listOf(notification(postTime = 2500L)))

        val sorted = listOf(older, newer, middle)
            .filter { it.notifications.isNotEmpty() }
            .sortedByDescending { c -> c.notifications.maxOf { it.postTime } }

        assertEquals(listOf("com.new", "com.mid", "com.old"), sorted.map { it.packageName })
    }

    @Test
    fun chaptersWithNoNotificationsAreExcluded() {
        val withNotes = chapter("com.notes", notifications = listOf(notification(postTime = 1000L)))
        val empty = chapter("com.empty", notifications = emptyList())

        val filtered = listOf(withNotes, empty).filter { it.notifications.isNotEmpty() }

        assertEquals(1, filtered.size)
        assertEquals("com.notes", filtered[0].packageName)
    }

    @Test
    fun mostRecentNotificationDrivesChapterOrder() {
        val chapterA = chapter("com.a", notifications = listOf(
            notification(postTime = 100L),
            notification(postTime = 900L),
        ))
        val chapterB = chapter("com.b", notifications = listOf(
            notification(postTime = 1000L),
        ))

        val sorted = listOf(chapterA, chapterB)
            .filter { it.notifications.isNotEmpty() }
            .sortedByDescending { c -> c.notifications.maxOf { it.postTime } }

        // chapterB has most recent notification (1000 > 900)
        assertEquals("com.b", sorted[0].packageName)
        assertEquals("com.a", sorted[1].packageName)
    }

    @Test
    fun singleChapterWithNotificationsIsRetained() {
        val chapter = chapter("com.single", notifications = listOf(notification(postTime = 42L)))

        val sorted = listOf(chapter)
            .filter { it.notifications.isNotEmpty() }
            .sortedByDescending { c -> c.notifications.maxOf { it.postTime } }

        assertEquals(1, sorted.size)
        assertTrue(sorted[0].notifications.isNotEmpty())
    }

    private fun chapter(
        packageName: String,
        notifications: List<CalmNotificationListenerService.CalmNotification>,
    ): AppChapter = AppChapter(
        packageName = packageName,
        label = packageName,
        notifications = notifications,
        launchable = true,
        hueColor = 0,
    )

    private fun notification(postTime: Long): CalmNotificationListenerService.CalmNotification =
        CalmNotificationListenerService.CalmNotification(
            key = "key-$postTime",
            packageName = "com.example",
            title = "Notification",
            text = "body",
            subText = "",
            conversationTitle = "",
            postTime = postTime,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
}
