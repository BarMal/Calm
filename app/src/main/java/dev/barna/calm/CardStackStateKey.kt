package dev.barna.calm

object CardStackStateKey {
    const val AGENDA = "agenda"
    const val OVERVIEW_CALENDAR = "overview:calendar"
    const val OVERVIEW_NOTIFICATIONS = "overview:notifications"

    fun appLibrary(pageKey: String, scope: AppLibraryScope, query: String): String {
        return listOf("app-library", pageKey, scope.name, query).joinToString(":")
    }

    fun appEntries(prefix: String, apps: List<AppEntry>): String {
        return "$prefix:${apps.joinToString("|") { app -> app.identityKey }}"
    }

    fun notifications(chapter: AppChapter): String {
        return "notifications:${chapter.identityKey}"
    }
}
