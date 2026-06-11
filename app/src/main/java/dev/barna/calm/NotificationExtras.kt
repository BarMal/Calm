package dev.barna.calm

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable

object NotificationExtras {
    fun messagingMessages(notification: Notification): List<Notification.MessagingStyle.Message> {
        val messages = parcelableArray(notification.extras, Notification.EXTRA_MESSAGES) ?: return emptyList()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).orEmpty()
    }

    fun senderName(message: Notification.MessagingStyle.Message): String {
        message.senderPerson?.name?.toString()?.let { return it }
        @Suppress("DEPRECATION")
        return message.sender?.toString().orEmpty()
    }

    fun bitmapExtra(context: Context, notification: Notification, key: String): Bitmap? {
        return bitmapExtra(context, notification.extras, key)
    }

    fun mediaSessionToken(notification: Notification): MediaSession.Token? {
        return parcelable(notification.extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
    }

    private fun bitmapExtra(context: Context, extras: Bundle, key: String): Bitmap? {
        parcelable(extras, key, Bitmap::class.java)?.let { return it }
        return parcelable(extras, key, Icon::class.java)?.loadDrawable(context)?.toBitmap()
    }

    private fun parcelableArray(extras: Bundle, key: String): Array<Parcelable>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(key, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(key)
        }
    }

    private fun <T : Parcelable> parcelable(extras: Bundle, key: String, type: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(key, type)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(key)
        }
    }
}
