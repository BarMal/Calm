package dev.barna.calm

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

class CalmNotificationListenerService : NotificationListenerService() {
    data class CalmNotification(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val subText: String,
        val postTime: Long,
        val contentIntent: PendingIntent?,
        val backgroundImage: Bitmap?,
    )

    override fun onListenerConnected() {
        synchronized(lock) {
            currentService = this
        }
        refreshSnapshot()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onDestroy() {
        synchronized(lock) {
            if (currentService === this) {
                currentService = null
                currentNotifications = emptyList()
            }
        }
        notifyListeners()
        super.onDestroy()
    }

    private fun refreshSnapshot() {
        val next = ArrayList<CalmNotification>()
        activeNotifications?.forEach { status ->
            toCalmNotification(status)?.let(next::add)
        }
        synchronized(lock) {
            currentNotifications = next
        }
        notifyListeners()
    }

    private fun toCalmNotification(status: StatusBarNotification?): CalmNotification? {
        val notification = status?.notification ?: return null
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty() && notification.tickerText != null) {
            text = notification.tickerText.toString()
        }

        return CalmNotification(
            status.key,
            status.packageName,
            title,
            text,
            subText,
            status.postTime,
            notification.contentIntent,
            notificationBackground(notification),
        )
    }

    private fun notificationBackground(notification: Notification): Bitmap? {
        val picture = notification.extras.get(Notification.EXTRA_PICTURE)
        if (picture is Bitmap) return picture

        val largeIconExtra = notification.extras.get(Notification.EXTRA_LARGE_ICON)
        if (largeIconExtra is Bitmap) return largeIconExtra

        val drawable = notification.getLargeIcon()?.loadDrawable(this) ?: return null
        return drawable.toBitmap()
    }

    private fun notifyListeners() {
        listeners.forEach { it.run() }
    }

    companion object {
        private val lock = Any()
        private val listeners = CopyOnWriteArraySet<Runnable>()
        private var currentService: CalmNotificationListenerService? = null
        private var currentNotifications: List<CalmNotification> = emptyList()

        @JvmStatic
        fun addListener(listener: Runnable) {
            listeners.add(listener)
        }

        @JvmStatic
        fun removeListener(listener: Runnable) {
            listeners.remove(listener)
        }

        @JvmStatic
        fun snapshot(): List<CalmNotification> {
            synchronized(lock) {
                return ArrayList(currentNotifications)
            }
        }

        @JvmStatic
        fun isConnected(): Boolean {
            synchronized(lock) {
                return currentService != null
            }
        }

        @JvmStatic
        fun clearPackage(packageName: String) {
            val service = synchronized(lock) { currentService } ?: return
            service.activeNotifications?.forEach { status ->
                if (status != null && packageName == status.packageName) {
                    service.cancelNotification(status.key)
                }
            }
            service.refreshSnapshot()
        }
    }
}
