package dev.barna.calm

object NotificationCardGrouper {
    fun cards(
        notifications: List<CalmNotificationListenerService.CalmNotification>,
        groupingEnabled: Boolean,
    ): List<NotificationCardItem> {
        val pruned = pruneSummaries(notifications)
        if (!groupingEnabled) {
            return pruned.map { NotificationCardItem(listOf(it)) }
        }

        return pruned
            .groupBy { groupingKey(it) }
            .values
            .flatMap { group ->
                if (group.size > 1) {
                    listOf(NotificationCardItem(group.sortedByDescending { it.postTime }))
                } else {
                    group.map { NotificationCardItem(listOf(it)) }
                }
            }
            .sortedByDescending { item -> item.notifications.maxOf { it.postTime } }
    }

    private fun pruneSummaries(
        notifications: List<CalmNotificationListenerService.CalmNotification>,
    ): List<CalmNotificationListenerService.CalmNotification> {
        if (notifications.size <= 1) {
            return notifications
        }
        return notifications.filterNot { notification ->
            val title = notification.title.trim()
            val body = notification.bodyText().trim()
            isCountSummary(title) || isCountSummary(body)
        }.ifEmpty { notifications }
    }

    private fun isCountSummary(text: String): Boolean {
        return Regex("^\\d+\\s+new\\s+.+", RegexOption.IGNORE_CASE).matches(text) ||
            Regex("^\\d+\\s+messages?$", RegexOption.IGNORE_CASE).matches(text)
    }

    private fun groupingKey(notification: CalmNotificationListenerService.CalmNotification): String {
        val conversation = conversationName(notification)
        return listOf(notification.packageName, conversation)
            .joinToString("::")
            .trim()
            .lowercase()
            .ifBlank { notification.key }
    }

    fun conversationName(notification: CalmNotificationListenerService.CalmNotification): String {
        return notification.conversationTitle
            .ifBlank { notification.title }
            .trim()
    }
}
