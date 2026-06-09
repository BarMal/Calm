package dev.barna.calm

class LauncherRenderModelFactory(
    private val pageStateFactory: LauncherPageStateFactory = LauncherPageStateFactory(),
) {
    fun create(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedKeys: Set<String>,
        pinnedChapterPackages: Set<String> = emptySet(),
        hasCalendarPermission: Boolean,
        calendarEvents: List<CalendarEvent>,
    ): LauncherRenderModel {
        val pageState = pageStateFactory.create(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedChapterPackages = pinnedChapterPackages,
        )
        return LauncherRenderModel(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedApps = pageState.pinnedApps,
            pinnedChapterPackages = pinnedChapterPackages,
            pages = pageState.pages,
            hasCalendarPermission = hasCalendarPermission,
            calendarEvents = calendarEvents,
        )
    }
}

data class LauncherRenderModel(
    val preferences: LauncherUiPreferences,
    val notificationChapters: List<AppChapter>,
    val appEntries: List<AppEntry>,
    val pinnedKeys: Set<String>,
    val pinnedApps: List<AppEntry>,
    val pinnedChapterPackages: Set<String> = emptySet(),
    val pages: List<ChapterPage>,
    val hasCalendarPermission: Boolean,
    val calendarEvents: List<CalendarEvent>,
)
