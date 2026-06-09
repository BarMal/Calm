package dev.barna.calm

enum class PageSortOrder {
    APP_NAME_ASC,
    APP_NAME_DESC,
    NOTIFICATION_AGE_NEWEST,
    NOTIFICATION_AGE_OLDEST;

    companion object {
        val DEFAULT = APP_NAME_ASC

        fun decode(value: String): PageSortOrder =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
