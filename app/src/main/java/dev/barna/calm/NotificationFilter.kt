package dev.barna.calm

data class NotificationFilter(
    val kind: Kind,
    val packageName: String,
    val value: String,
    val sourceKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
) {
    enum class Kind {
        APP,
        TITLE,
        BODY,
        EMPTY_CONTENT,
    }

    fun matches(notification: CalmNotificationListenerService.CalmNotification): Boolean {
        if (notification.packageName != packageName) return false
        if (sourceKey != AppIdentity.packageOnly(packageName).notificationSourceKey && notification.sourceKey != sourceKey) return false
        return when (kind) {
            Kind.APP -> true
            Kind.TITLE -> normalized(notification.title) == normalized(value)
            Kind.BODY -> normalized(notification.bodyText()) == normalized(value)
            Kind.EMPTY_CONTENT -> normalized(notification.title).isEmpty() && normalized(notification.bodyText()).isEmpty()
        }
    }

    fun encode(): String {
        return listOf(kind.name, packageName, sourceKey, value).joinToString(SEPARATOR)
    }

    companion object {
        private const val SEPARATOR = "\u001f"

        fun decode(encoded: String): NotificationFilter? {
            val parts = encoded.split(SEPARATOR, limit = 4)
            if (parts.size != 3 && parts.size != 4) return null
            val kind = runCatching { Kind.valueOf(parts[0]) }.getOrNull() ?: return null
            return if (parts.size == 3) {
                NotificationFilter(kind, parts[1], parts[2])
            } else {
                NotificationFilter(kind, parts[1], parts[3], parts[2])
            }
        }

        fun title(packageName: String, title: String): NotificationFilter {
            return NotificationFilter(Kind.TITLE, packageName, title)
        }

        fun title(sourceKey: String, packageName: String, title: String): NotificationFilter {
            return NotificationFilter(Kind.TITLE, packageName, title, sourceKey)
        }

        fun body(packageName: String, body: String): NotificationFilter {
            return NotificationFilter(Kind.BODY, packageName, body)
        }

        fun body(sourceKey: String, packageName: String, body: String): NotificationFilter {
            return NotificationFilter(Kind.BODY, packageName, body, sourceKey)
        }

        fun emptyContent(sourceKey: String, packageName: String): NotificationFilter {
            return NotificationFilter(Kind.EMPTY_CONTENT, packageName, "", sourceKey)
        }

        fun app(packageName: String): NotificationFilter {
            return NotificationFilter(Kind.APP, packageName, "")
        }

        fun app(sourceKey: String, packageName: String): NotificationFilter {
            return NotificationFilter(Kind.APP, packageName, "", sourceKey)
        }

        private fun normalized(value: String): String {
            return value.trim().lowercase()
        }
    }
}
