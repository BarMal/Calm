package dev.barna.calm

data class NotificationCardItem(
    val notifications: List<CalmNotificationListenerService.CalmNotification>,
) {
    val primary: CalmNotificationListenerService.CalmNotification = notifications.first()
    val isGroup: Boolean = notifications.size > 1

    fun title(): String {
        val baseTitle = NotificationCardGrouper.conversationName(primary).ifBlank { "Untitled notification" }
        return if (isGroup) "$baseTitle (${notifications.size})" else baseTitle
    }

    fun previewText(): String {
        if (!isGroup) {
            return primary.bodyText().ifBlank { primary.subText }
        }
        return notifications
            .joinToString("\n") { notification ->
                val sender = notification.title.takeUnless {
                    it.isBlank() || it == notification.conversationTitle
                }.orEmpty()
                val body = notification.bodyText().ifBlank { notification.subText }
                if (sender.isBlank()) body else "$sender: $body"
            }
            .trim()
    }

    fun fullText(): String {
        return notifications.joinToString("\n\n") { notification ->
            val body = notification.bodyText().ifBlank { notification.subText }
            if (body.isBlank()) {
                notification.title.ifBlank { "Untitled notification" }
            } else {
                "${notification.title.ifBlank { "Untitled notification" }}\n$body"
            }
        }
    }

    fun allActions(): List<NotificationAction> {
        return notifications
            .flatMap { it.actions }
            .distinctBy { it.label.lowercase() }
    }
}

fun CalmNotificationListenerService.CalmNotification.bodyText(): String {
    return text.ifBlank { subText }
}
