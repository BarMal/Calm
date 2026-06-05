package dev.barna.calm

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession

class NotificationArtworkExtractor(private val context: Context) {
    fun artwork(notification: Notification): Bitmap? {
        notificationBitmapExtra(notification, Notification.EXTRA_PICTURE)?.let { return it }
        mediaArtwork(notification)?.let { return it }
        notificationBitmapExtra(notification, Notification.EXTRA_LARGE_ICON)?.let { return it }
        return notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
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
