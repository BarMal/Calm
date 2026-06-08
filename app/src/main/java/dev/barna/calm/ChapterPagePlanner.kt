package dev.barna.calm

class ChapterPagePlanner {
    fun buildPages(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedApps: List<AppEntry>,
        pinnedChapterPackages: Set<String> = emptySet(),
    ): List<ChapterPage> {
        val pages = ArrayList<ChapterPage>()
        var chapterNumber = 1

        val chapterByPackage = notificationChapters.associateBy { it.packageName }
        val appEntryByPackage = appEntries.associateBy { it.packageName }

        // Resolve pinned chapters: live chapter if available, otherwise a stub from app entries.
        // Packages not installed (not in appEntries) are silently ignored.
        val pinnedChapters = pinnedChapterPackages.mapNotNull { pkg ->
            chapterByPackage[pkg] ?: appEntryByPackage[pkg]?.let { entry -> stubChapter(entry) }
        }
        val pinnedPackageSet = pinnedChapters.map { it.packageName }.toSet()

        val unpinnedChapters = notificationChapters.filter { it.packageName !in pinnedPackageSet }

        val allChapters = pinnedChapters + unpinnedChapters

        val (workChapters, standardChapters) = if (preferences.placeWorkNotificationChaptersBeforeApps) {
            allChapters.partition { it.isWorkProfile }
        } else {
            emptyList<AppChapter>() to allChapters
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

    private fun stubChapter(entry: AppEntry): AppChapter {
        return AppChapter(
            packageName = entry.packageName,
            label = entry.label,
            notifications = emptyList(),
            launchable = true,
            hueColor = entry.hueColor,
            isWorkProfile = entry.isWorkProfile,
        )
    }
}
