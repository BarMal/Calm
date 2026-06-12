package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockNotificationResolverTest {
    private val resolver = DockNotificationResolver()

    @Test
    fun matchesChapterByLauncherIdentityKey() {
        val app = app(identityKey = "profile/chat")
        val chapter = chapter(launcherIdentityKey = "profile/chat")

        val target = resolver.targetFor(app, listOf(chapter))

        assertEquals(chapter, target?.chapter)
        assertEquals(2, target?.summary?.count)
        assertEquals("Newest", target?.summary?.latestTitle)
    }

    @Test
    fun matchesChapterByNotificationSourceKey() {
        val app = app(notificationSourceKey = "source/chat")
        val chapter = chapter(identityKey = "source/chat")

        val target = resolver.targetFor(app, listOf(chapter))

        assertEquals(chapter, target?.chapter)
    }

    @Test
    fun skipsAppsWithoutNotifications() {
        val app = app()
        val chapter = chapter(notifications = emptyList())

        assertNull(resolver.targetFor(app, listOf(chapter)))
    }

    private fun app(
        packageName: String = "chat.pkg",
        identityKey: String = packageName,
        notificationSourceKey: String = packageName,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = "Chat",
            hueColor = 0,
            identityKey = identityKey,
            notificationSourceKey = notificationSourceKey,
        )
    }

    private fun chapter(
        packageName: String = "chat.pkg",
        identityKey: String = packageName,
        launcherIdentityKey: String = packageName,
        notifications: List<CalmNotificationListenerService.CalmNotification> = listOf(
            notification("Older", postTime = 10),
            notification("Newest", postTime = 20),
        ),
    ): AppChapter {
        return AppChapter(
            packageName = packageName,
            label = "Chat",
            notifications = notifications,
            launchable = true,
            hueColor = 0,
            identityKey = identityKey,
            launcherIdentityKey = launcherIdentityKey,
        )
    }

    private fun notification(title: String, postTime: Long): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = title,
            packageName = "chat.pkg",
            title = title,
            text = "Body",
            subText = "",
            conversationTitle = "",
            postTime = postTime,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}
