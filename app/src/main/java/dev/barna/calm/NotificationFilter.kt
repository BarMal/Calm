package dev.barna.calm

data class NotificationFilter(
    val kind: Kind,
    val packageName: String,
    val value: String,
    val sourceKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
    val matchMode: MatchMode = MatchMode.EXACT,
) {
    enum class Kind {
        APP,
        TITLE,
        BODY,
        EMPTY_CONTENT,
    }

    enum class MatchMode {
        EXACT,
        CONTAINS,
        WILDCARD,
    }

    fun matches(notification: CalmNotificationListenerService.CalmNotification): Boolean {
        if (notification.packageName != packageName) return false
        if (sourceKey != AppIdentity.packageOnly(packageName).notificationSourceKey && notification.sourceKey != sourceKey) return false
        return when (kind) {
            Kind.APP -> true
            Kind.TITLE -> textMatches(notification.title)
            Kind.BODY -> textMatches(notification.bodyText())
            Kind.EMPTY_CONTENT -> normalized(notification.title).isEmpty() && normalized(notification.bodyText()).isEmpty()
        }
    }

    fun encode(): String {
        if (matchMode == MatchMode.EXACT) {
            return listOf(kind.name, packageName, sourceKey, value).joinToString(SEPARATOR)
        }
        return listOf(kind.name, packageName, sourceKey, matchMode.name, value).joinToString(SEPARATOR)
    }

    private fun textMatches(candidate: String): Boolean {
        val normalizedCandidate = normalized(candidate)
        val normalizedValue = normalized(value)
        return when (matchMode) {
            MatchMode.EXACT -> normalizedCandidate == normalizedValue
            MatchMode.CONTAINS -> normalizedValue.isNotEmpty() && normalizedCandidate.contains(normalizedValue)
            MatchMode.WILDCARD -> wildcardRegex(normalizedValue).matches(normalizedCandidate)
        }
    }

    companion object {
        private const val SEPARATOR = "\u001f"
        private const val ANY_PLACEHOLDER = "{?}"

        fun decode(encoded: String): NotificationFilter? {
            val parts = encoded.split(SEPARATOR, limit = 5)
            if (parts.size !in 3..5) return null
            val kind = runCatching { Kind.valueOf(parts[0]) }.getOrNull() ?: return null
            return when (parts.size) {
                3 -> NotificationFilter(kind, parts[1], parts[2])
                4 -> NotificationFilter(kind, parts[1], parts[3], parts[2])
                else -> {
                    val matchMode = runCatching { MatchMode.valueOf(parts[3]) }.getOrNull() ?: return null
                    NotificationFilter(kind, parts[1], parts[4], parts[2], matchMode)
                }
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

        fun titleContains(sourceKey: String, packageName: String, title: String): NotificationFilter {
            return NotificationFilter(Kind.TITLE, packageName, title, sourceKey, MatchMode.CONTAINS)
        }

        fun bodyContains(sourceKey: String, packageName: String, body: String): NotificationFilter {
            return NotificationFilter(Kind.BODY, packageName, body, sourceKey, MatchMode.CONTAINS)
        }

        fun titleWildcard(sourceKey: String, packageName: String, title: String): NotificationFilter {
            return NotificationFilter(Kind.TITLE, packageName, title, sourceKey, MatchMode.WILDCARD)
        }

        fun bodyWildcard(sourceKey: String, packageName: String, body: String): NotificationFilter {
            return NotificationFilter(Kind.BODY, packageName, body, sourceKey, MatchMode.WILDCARD)
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
            return value.trim().replace(Regex("\\s+"), " ").lowercase()
        }

        private fun wildcardRegex(pattern: String): Regex {
            val regex = buildString {
                append("^")
                var index = 0
                while (index < pattern.length) {
                    when {
                        pattern.startsWith(ANY_PLACEHOLDER, index) -> {
                            append(".*")
                            index += ANY_PLACEHOLDER.length
                        }
                        pattern[index] == '*' -> {
                            append(".*")
                            index += 1
                        }
                        else -> {
                            append(Regex.escape(pattern[index].toString()))
                            index += 1
                        }
                    }
                }
                append("$")
            }
            return Regex(regex)
        }
    }
}
