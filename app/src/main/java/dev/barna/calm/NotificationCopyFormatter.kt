package dev.barna.calm

class NotificationCopyFormatter {
    fun notificationSummary(notificationCount: Int): String {
        return if (notificationCount == 1) "1 active note" else "$notificationCount active notes"
    }

    fun groupedDismissToast(isGroup: Boolean): String {
        return if (isGroup) "Dismissed notification group" else "Dismissed notification"
    }

    fun chapterClearedToast(chapterLabel: String): String {
        return "Cleared $chapterLabel"
    }

    fun excludedToast(chapterLabel: String): String {
        return "Excluded $chapterLabel"
    }
}
