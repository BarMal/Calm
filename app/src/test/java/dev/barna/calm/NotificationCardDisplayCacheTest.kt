package dev.barna.calm

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class NotificationCardDisplayCacheTest {
    private val resolver = FakeNotificationCardAssetResolver()
    private val cache = NotificationCardDisplayCache(resolver)
    private val chapter = AppChapter(
        packageName = "chat.pkg",
        label = "Chat",
        notifications = emptyList(),
        launchable = true,
        hueColor = 0,
        identityKey = "chat.pkg:user:0",
        launcherIdentityKey = "chat.pkg:Main:user:0",
    )

    @Test
    fun computesNotificationDisplayDataOncePerSnapshot() {
        var timeFormatCalls = 0
        val item = NotificationCardItem(listOf(notification(key = "one", title = "Alex", text = "Ping", postTime = 10L)))

        val first = cache.getOrCreate(item, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }
        val second = cache.getOrCreate(item, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }

        assertSame(first, second)
        assertEquals(1, timeFormatCalls)
        assertEquals("Alex\nPing\ntime-10", first.text)
        assertEquals(1, resolver.appIconCalls)
    }

    @Test
    fun refreshesWhenNotificationContentChanges() {
        var timeFormatCalls = 0
        val first = NotificationCardItem(listOf(notification(key = "one", title = "Alex", text = "Ping", postTime = 10L)))
        val changed = NotificationCardItem(listOf(notification(key = "one", title = "Alex", text = "Updated", postTime = 10L)))

        cache.getOrCreate(first, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }
        val data = cache.getOrCreate(changed, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }

        assertEquals(2, timeFormatCalls)
        assertEquals("Alex\nUpdated\ntime-10", data.text)
    }

    @Test
    fun clearForcesRecalculation() {
        var timeFormatCalls = 0
        val item = NotificationCardItem(listOf(notification(key = "one", title = "Alex", text = "Ping", postTime = 10L)))

        cache.getOrCreate(item, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }
        cache.clear()
        cache.getOrCreate(item, chapter) { time ->
            timeFormatCalls++
            "time-$time"
        }

        assertEquals(2, timeFormatCalls)
    }

    @Test
    fun cachedChapterMaskedIconDoesNotResolveIconOnUiPath() {
        assertEquals(null, cache.cachedChapterMaskedIcon(chapter))
        assertEquals(0, resolver.maskedIconCalls)

        val resolved = cache.chapterMaskedIcon(chapter)
        val cached = cache.cachedChapterMaskedIcon(chapter)

        assertSame(resolved, cached)
        assertEquals(1, resolver.maskedIconCalls)
    }

    @Test
    fun duplicatePreloadRequestsOnlyScheduleOneJobWhilePending() {
        val executor = QueuedExecutor()
        val chapterWithNotifications = AppChapter(
            packageName = chapter.packageName,
            label = chapter.label,
            notifications = listOf(notification(key = "one", title = "Alex", text = "Ping", postTime = 10L)),
            launchable = chapter.launchable,
            hueColor = chapter.hueColor,
            identityKey = chapter.identityKey,
            launcherIdentityKey = chapter.launcherIdentityKey,
        )

        cache.preload(listOf(chapterWithNotifications), { true }, { "time-$it" }, executor)
        cache.preload(listOf(chapterWithNotifications), { true }, { "time-$it" }, executor)

        assertEquals(1, executor.tasks.size)
    }

    private fun notification(
        key: String,
        title: String,
        text: String,
        postTime: Long,
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = key,
            packageName = "chat.pkg",
            title = title,
            text = text,
            subText = "",
            conversationTitle = "",
            postTime = postTime,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}

private class FakeNotificationCardAssetResolver : NotificationCardAssetResolver {
    var appIconCalls = 0
    var maskedIconCalls = 0
    override fun resolveAppIconBitmap(chapter: AppChapter): Bitmap? {
        appIconCalls++
        return null
    }

    override fun resolveMaskedAppIconBitmap(chapter: AppChapter): Bitmap? {
        maskedIconCalls++
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

private class QueuedExecutor : Executor {
    val tasks = ArrayList<Runnable>()
    override fun execute(command: Runnable) {
        tasks.add(command)
    }
}
