package dev.barna.calm

class LauncherPageStateFactory(
    private val pinnedAppResolver: PinnedAppResolver = PinnedAppResolver(),
    private val chapterPagePlanner: ChapterPagePlanner = ChapterPagePlanner(),
) {
    fun create(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedKeys: Set<String>,
        pinnedChapterPackages: Set<String> = emptySet(),
    ): LauncherPageState {
        val pinnedApps = pinnedAppResolver.resolve(appEntries, pinnedKeys)
        val pages = chapterPagePlanner.buildPages(
            preferences = preferences,
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedApps = pinnedApps,
            pinnedChapterPackages = pinnedChapterPackages,
        )
        return LauncherPageState(
            pages = PageArranger.arrange(pages, preferences.pageLayout),
            pinnedApps = pinnedApps,
        )
    }
}

data class LauncherPageState(
    val pages: List<ChapterPage>,
    val pinnedApps: List<AppEntry>,
)
