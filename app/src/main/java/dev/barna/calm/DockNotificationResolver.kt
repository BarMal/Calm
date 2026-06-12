package dev.barna.calm

data class DockNotificationSummary(
    val count: Int,
    val latestTitle: String,
    val latestText: String,
)

data class DockNotificationTarget(
    val chapter: AppChapter,
    val summary: DockNotificationSummary,
)

class DockNotificationResolver {
    fun targetFor(app: AppEntry, chapters: List<AppChapter>): DockNotificationTarget? {
        val chapter = chapters.firstOrNull { chapter ->
            chapter.launcherIdentityKey == app.identityKey ||
                (chapter.packageName == app.packageName && chapter.isWorkProfile == app.isWorkProfile) ||
                chapter.identityKey == app.notificationSourceKey
        } ?: return null
        if (chapter.notifications.isEmpty()) return null
        val latest = chapter.notifications.maxByOrNull { notification -> notification.postTime }
        return DockNotificationTarget(
            chapter = chapter,
            summary = DockNotificationSummary(
                count = chapter.notifications.size,
                latestTitle = latest?.title?.ifBlank { chapter.label }.orEmpty().ifBlank { chapter.label },
                latestText = latest?.bodyText().orEmpty(),
            ),
        )
    }
}
