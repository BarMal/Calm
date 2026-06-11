package dev.barna.calm

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController

class NotificationArtworkExtractor(private val context: Context) {
    fun artwork(notification: Notification): Bitmap? {
        NotificationExtras.bitmapExtra(context, notification, Notification.EXTRA_PICTURE)?.let { return it }
        messagingImage(notification)?.let { return it }
        mediaArtwork(notification)?.let { return it }
        return notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
    }

    /** Image attached to a MessagingStyle message (e.g. a photo sent in a chat). */
    private fun messagingImage(notification: Notification): Bitmap? {
        val messages = NotificationExtras.messagingMessages(notification)
        for (index in messages.indices.reversed()) {
            val message = messages[index]
            val mimeType = message.dataMimeType ?: continue
            if (!mimeType.startsWith("image/")) continue
            val uri = message.dataUri ?: continue
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun mediaArtwork(notification: Notification): Bitmap? {
        val token = NotificationExtras.mediaSessionToken(notification) ?: return null
        val metadata = runCatching { MediaController(context, token).metadata }.getOrNull() ?: return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }
}
