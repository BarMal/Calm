package dev.barna.calm

class LauncherRenderModelFactory(
    private val pageStateFactory: LauncherPageStateFactory = LauncherPageStateFactory(),
    private val dockResolver: DockResolver = DockResolver(),
) {
    fun create(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedKeys: Set<String>,
        dockKeys: List<String> = emptyList(),
        hasCalendarPermission: Boolean,
        calendarEvents: List<CalendarEvent>,
    ): LauncherRenderModel {
        val pageState = pageStateFactory.create(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
        )
        return LauncherRenderModel(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedApps = pageState.pinnedApps,
            pages = pageState.pages,
            dockKeys = dockKeys,
            dockApps = dockResolver.resolve(appEntries, dockKeys),
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
    val pages: List<ChapterPage>,
    val dockKeys: List<String> = emptyList(),
    val dockApps: List<AppEntry> = emptyList(),
    val hasCalendarPermission: Boolean,
    val calendarEvents: List<CalendarEvent>,
)
