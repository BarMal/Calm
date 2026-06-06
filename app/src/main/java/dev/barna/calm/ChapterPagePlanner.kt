package dev.barna.calm

class ChapterPagePlanner {
    fun buildPages(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedApps: List<AppEntry>,
    ): List<ChapterPage> {
        val pages = ArrayList<ChapterPage>()
        var chapterNumber = 1
        val (workChapters, standardChapters) = if (preferences.placeWorkNotificationChaptersBeforeApps) {
            notificationChapters.partition { it.isWorkProfile }
        } else {
            emptyList<AppChapter>() to notificationChapters
        }

        workChapters.forEach { chapter ->
            pages.add(ChapterPage.notifications(chapter, roman(chapterNumber)))
            chapterNumber++
        }

        if (preferences.splitAppsByProfile) {
            if (appEntries.any { it.isWorkProfile }) {
                pages.add(ChapterPage.workApps(CalmTheme.WORK_APP_LIBRARY_KEY, roman(chapterNumber)))
                chapterNumber++
            }
            pages.add(ChapterPage.personalApps(CalmTheme.PERSONAL_APP_LIBRARY_KEY, roman(chapterNumber)))
            chapterNumber++
        } else {
            pages.add(ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY).withMarker(roman(chapterNumber)))
            chapterNumber++
        }

        if (pinnedApps.isNotEmpty()) {
            pages.add(ChapterPage.pinned(CalmTheme.PINNED_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        pages.add(ChapterPage.overview(CalmTheme.OVERVIEW_KEY).withMarker(roman(chapterNumber)))
        chapterNumber++
        standardChapters.forEach { chapter ->
            pages.add(ChapterPage.notifications(chapter, roman(chapterNumber)))
            chapterNumber++
        }
        return pages
    }
}
