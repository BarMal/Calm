package dev.barna.calm

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.net.Uri
import android.os.Bundle

class NotificationArtworkExtractor(private val context: Context) {
    fun artwork(notification: Notification): Bitmap? {
        notificationBitmapExtra(notification, Notification.EXTRA_PICTURE)?.let { return it }
        messagingImage(notification)?.let { return it }
        mediaArtwork(notification)?.let { return it }
        notificationBitmapExtra(notification, Notification.EXTRA_LARGE_ICON)?.let { return it }
        return notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
    }

    /** Image attached to a MessagingStyle message (e.g. a photo sent in a chat). */
    private fun messagingImage(notification: Notification): Bitmap? {
        @Suppress("DEPRECATION")
        val messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        for (index in messages.indices.reversed()) {
            val bundle = messages[index] as? Bundle ?: continue
            val mimeType = bundle.getString("type") ?: continue
            if (!mimeType.startsWith("image/")) continue
            @Suppress("DEPRECATION")
            val uri = bundle.getParcelable<Uri>("uri") ?: continue
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun notificationBitmapExtra(notification: Notification, key: String): Bitmap? {
        return when (val value = notification.extras.get(key)) {
            is Bitmap -> value
            is Icon -> value.loadDrawable(context)?.toBitmap()
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun mediaArtwork(notification: Notification): Bitmap? {
        val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
            ?: return null
        val metadata = runCatching { MediaController(context, token).metadata }.getOrNull() ?: return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }
}
