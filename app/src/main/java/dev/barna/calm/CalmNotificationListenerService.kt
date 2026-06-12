package dev.barna.calm

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

class CalmNotificationListenerService : NotificationListenerService() {
    private val artworkExtractor by lazy { NotificationArtworkExtractor(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshSnapshot() }

    data class CalmNotification(
        val key: String,
        val cancelKey: String = key,
        val packageName: String,
        val userSerial: Long = AppIdentity.LEGACY_USER_SERIAL,
        val title: String,
        val text: String,
        val subText: String,
        val conversationTitle: String,
        val postTime: Long,
        val contentIntent: PendingIntent?,
        val backgroundImage: Bitmap?,
        val actions: List<NotificationAction>,
    ) {
        val sourceKey: String
            get() = AppIdentity.notificationKey(packageName, userSerial)
    }

    private data class ExtractedMessage(
        val sender: String,
        val text: String,
        val timestamp: Long,
    )

    override fun onListenerConnected() {
        synchronized(lock) {
            currentService = this
        }
        scheduleSnapshotRefresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        scheduleSnapshotRefresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        scheduleSnapshotRefresh()
    }

    override fun onDestroy() {
        synchronized(lock) {
            if (currentService === this) {
                currentService = null
                currentNotifications = emptyList()
                revision++
            }
        }
        mainHandler.removeCallbacks(refreshRunnable)
        notifyListeners()
        super.onDestroy()
    }

    private fun scheduleSnapshotRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, SNAPSHOT_REFRESH_DELAY_MS)
    }

    private fun refreshSnapshot() {
        val next = ArrayList<CalmNotification>()
        activeNotifications?.forEach { status ->
            next.addAll(toCalmNotifications(status))
        }
        synchronized(lock) {
            currentNotifications = next
            revision++
        }
        notifyListeners()
    }

    private fun toCalmNotifications(status: StatusBarNotification?): List<CalmNotification> {
        val notification = status?.notification ?: return emptyList()
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val conversationTitle = notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val userSerial = profileSerial(status)
        val backgroundImage = notificationBackground(notification)
        val actions = notificationActions(notification)
        val messages = messagingMessages(notification)
        if (messages.size > 1) {
            return messages.mapIndexed { index, message -> IndexedValue(index, message) }
                .takeLast(MAX_MESSAGING_CARDS)
                .map { indexedMessage ->
                val message = indexedMessage.value
                CalmNotification(
                    key = messagingMessageKey(status.key, indexedMessage.index, message.timestamp),
                    cancelKey = status.key,
                    packageName = status.packageName,
                    userSerial = userSerial,
                    title = message.sender.ifBlank { conversationTitle.ifBlank { title } },
                    text = message.text,
                    subText = subText,
                    conversationTitle = conversationTitle.ifBlank { title },
                    postTime = message.timestamp.takeIf { it > 0L } ?: status.postTime,
                    contentIntent = notification.contentIntent,
                    backgroundImage = backgroundImage,
                    actions = actions,
                )
            }
        }
        if (title.isEmpty() && text.isEmpty() && notification.tickerText != null) {
            text = notification.tickerText.toString()
        }

        return listOf(
            CalmNotification(
                key = status.key,
                cancelKey = status.key,
                packageName = status.packageName,
                userSerial = userSerial,
                title = title,
                text = text,
                subText = subText,
                conversationTitle = conversationTitle,
                postTime = status.postTime,
                contentIntent = notification.contentIntent,
                backgroundImage = backgroundImage,
                actions = actions,
            ),
        )
    }

    private fun messagingMessages(notification: Notification): List<ExtractedMessage> {
        return NotificationExtras.messagingMessages(notification)
            .mapNotNull { message ->
                val text = message.text?.toString().orEmpty()
                if (text.isBlank()) {
                    null
                } else {
                    ExtractedMessage(
                        sender = NotificationExtras.senderName(message),
                        text = text,
                        timestamp = message.timestamp,
                    )
                }
            }
    }

    private fun profileSerial(status: StatusBarNotification): Long {
        return runCatching {
            getSystemService(UserManager::class.java).getSerialNumberForUser(status.user)
        }.getOrDefault(AppIdentity.LEGACY_USER_SERIAL)
    }

    private fun notificationActions(notification: Notification): List<NotificationAction> {
        return notification.actions
            ?.mapNotNull { action ->
                val label = action.title?.toString()?.trim().orEmpty()
                if (label.isBlank()) {
                    null
                } else {
                    NotificationAction(
                        label,
                        action.actionIntent,
                        action.remoteInputs?.toList().orEmpty(),
                    )
                }
            }
            .orEmpty()
    }

    private fun notificationBackground(notification: Notification): Bitmap? {
        return artworkExtractor.artwork(notification)
    }

    private fun notifyListeners() {
        listeners.forEach { it.run() }
    }

    companion object {
        private val lock = Any()
        private val listeners = CopyOnWriteArraySet<Runnable>()
        private var currentService: CalmNotificationListenerService? = null
        private var currentNotifications: List<CalmNotification> = emptyList()
        // Bumped whenever the snapshot changes (post/remove/disconnect). Lets the launcher detect
        // notifications that were dismissed while it was paused — e.g. opening an app clears its
        // notifications — and reconcile on resume instead of showing stale cards.
        private var revision = 0
        private const val SNAPSHOT_REFRESH_DELAY_MS = 80L
        // Cap per-notification message expansion to prevent a high-volume messaging app from
        // producing an unbounded number of CalmNotification entries from a single StatusBarNotification.
        private const val MAX_MESSAGING_CARDS = 20

        internal fun messagingMessageKey(statusKey: String, originalIndex: Int, timestamp: Long): String {
            return "$statusKey|message|$originalIndex|$timestamp"
        }

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
        fun revision(): Int {
            synchronized(lock) {
                return revision
            }
        }

        @JvmStatic
        fun isConnected(): Boolean {
            synchronized(lock) {
                return currentService != null
            }
        }

        @JvmStatic
        fun clearPackage(packageName: String, userSerial: Long = AppIdentity.LEGACY_USER_SERIAL) {
            val service = synchronized(lock) { currentService } ?: return
            service.activeNotifications?.forEach { status ->
                if (status != null &&
                    packageName == status.packageName &&
                    (userSerial == AppIdentity.LEGACY_USER_SERIAL || service.profileSerial(status) == userSerial)
                ) {
                    service.cancelNotification(status.key)
                }
            }
            service.refreshSnapshot()
        }

        @JvmStatic
        fun dismissNotifications(keys: Collection<String>) {
            val service = synchronized(lock) { currentService } ?: return
            keys.distinct().forEach { key -> service.cancelNotification(key) }
            service.refreshSnapshot()
        }
    }
}
