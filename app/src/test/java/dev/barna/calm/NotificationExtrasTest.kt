package dev.barna.calm

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationExtrasTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun messagingMessagesReadsMessagingStyleMessages() {
        val sender = Person.Builder().setName("Ada").build()
        val notification = Notification.Builder(context, "calm")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(
                Notification.MessagingStyle(sender)
                    .addMessage("Hello", 123L, sender),
            )
            .build()

        val messages = NotificationExtras.messagingMessages(notification)

        assertEquals(1, messages.size)
        assertEquals("Hello", messages.single().text.toString())
        assertEquals("Ada", NotificationExtras.senderName(messages.single()))
        assertEquals(123L, messages.single().timestamp)
    }

    @Test
    fun bitmapExtraReadsBitmapPayload() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val notification = Notification.Builder(context, "calm")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
            .apply { extras.putParcelable(Notification.EXTRA_PICTURE, bitmap) }

        assertSame(bitmap, NotificationExtras.bitmapExtra(context, notification, Notification.EXTRA_PICTURE))
    }
}
