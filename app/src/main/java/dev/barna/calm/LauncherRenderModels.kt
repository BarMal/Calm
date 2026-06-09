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
        pinnedChapterPackages: Set<String> = emptySet(),
        dockConfig: DockConfig = DockConfig(),
        dockKeys: List<String> = emptyList(),
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
        val resolvedDockApps = if (dockConfig.enabled) {
            dockResolver.resolve(appEntries, dockKeys)
        } else {
            emptyList()
        }
        return LauncherRenderModel(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedApps = pageState.pinnedApps,
            pinnedChapterPackages = pinnedChapterPackages,
            pages = pageState.pages,
            dockConfig = dockConfig,
            dockKeys = dockKeys,
            dockApps = resolvedDockApps,
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
    val dockConfig: DockConfig = DockConfig(),
    val dockKeys: List<String> = emptyList(),
    val dockApps: List<AppEntry> = emptyList(),
    val hasCalendarPermission: Boolean,
    val calendarEvents: List<CalendarEvent>,
)
