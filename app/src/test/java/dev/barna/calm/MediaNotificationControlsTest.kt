package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationControlsTest {
    @Test
    fun detectsPreviousPlaybackAndNextActions() {
        val controls = MediaNotificationControls.from(
            listOf(
                notification(
                    actions = listOf(
                        action("Previous"),
                        action("Pause"),
                        action("Next"),
                    ),
                ),
            ),
        )

        assertTrue(controls.hasAnyAction)
        assertEquals("Previous", controls.previous?.label)
        assertEquals("Pause", controls.playPause?.label)
        assertEquals("Next", controls.next?.label)
    }

    @Test
    fun ignoresNonMediaActions() {
        val controls = MediaNotificationControls.from(
            listOf(
                notification(
                    actions = listOf(
                        action("Reply"),
                        action("Mark as read"),
                    ),
                ),
            ),
        )

        assertFalse(controls.hasAnyAction)
    }

    private fun action(label: String): NotificationAction {
        return NotificationAction(label, null, emptyList())
    }

    private fun notification(
        actions: List<NotificationAction>,
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = "key",
            packageName = "dev.barna.test",
            title = "Title",
            text = "Text",
            subText = "",
            conversationTitle = "",
            postTime = 1L,
            contentIntent = null,
            backgroundImage = null,
            actions = actions,
        )
    }
}
