package dev.barna.calm

import java.text.Collator

class ChapterPagePlanner {
    fun buildPages(
        preferences: LauncherUiPreferences,
        notificationChapters: List<AppChapter>,
        appEntries: List<AppEntry>,
        pinnedApps: List<AppEntry>,
        pinnedChapterPackages: Set<String> = emptySet(),
        classicPages: List<ClassicLauncherPageDefinition> = emptyList(),
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

        val unpinnedChapters = notificationChapters
            .filter { it.packageName !in pinnedPackageSet }
            .let { sorted(it, preferences.pageSortOrder) }

        val allChapters = pinnedChapters + unpinnedChapters

        val splitWorkChapters = preferences.splitAppsByProfile || preferences.placeWorkNotificationChaptersBeforeApps
        val (workChapters, standardChapters) = if (splitWorkChapters) {
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

        if (preferences.appGroupingEnabled && preferences.hasAnyCategoryAssignments) {
            when (preferences.categoryDisplayMode) {
                CategoryDisplayMode.CARD_STACK -> {
                    pages.add(ChapterPage.categoryFolder(CalmTheme.CATEGORY_FOLDER_KEY, roman(chapterNumber)))
                    chapterNumber++
                }
                CategoryDisplayMode.DYNAMIC_PAGES -> {
                    preferences.enabledCategoriesForDynamicPages.forEach { category ->
                        pages.add(ChapterPage.categoryPage(
                            key = CalmTheme.CATEGORY_PAGE_KEY_PREFIX + category.id,
                            marker = roman(chapterNumber),
                            title = category.title,
                        ))
                        chapterNumber++
                    }
                }
            }
        }
        if (pinnedApps.isNotEmpty() || preferences.pinnedPageEnabled) {
            pages.add(ChapterPage.pinned(CalmTheme.PINNED_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        if (preferences.contactsPageEnabled) {
            pages.add(ChapterPage.contacts(CalmTheme.CONTACTS_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        if (preferences.agendaPageEnabled) {
            pages.add(ChapterPage.agenda(CalmTheme.AGENDA_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        if (preferences.alarmsPageEnabled) {
            pages.add(ChapterPage.alarms(CalmTheme.ALARMS_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        if (preferences.rssPageEnabled) {
            pages.add(ChapterPage.rss(CalmTheme.RSS_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        pages.add(ChapterPage.overview(CalmTheme.OVERVIEW_KEY).withMarker(roman(chapterNumber)))
        chapterNumber++
        if (preferences.splitAppsByProfile && notificationChapters.any { it.isWorkProfile }) {
            pages.add(ChapterPage.workOverview(CalmTheme.WORK_OVERVIEW_KEY, roman(chapterNumber)))
            chapterNumber++
        }
        classicPages.forEach { classicPage ->
            pages.add(ChapterPage.classic(classicPage, roman(chapterNumber)))
            chapterNumber++
        }
        standardChapters.forEach { chapter ->
            pages.add(ChapterPage.notifications(chapter, roman(chapterNumber)))
            chapterNumber++
        }
        return pages
    }

    private fun sorted(chapters: List<AppChapter>, order: PageSortOrder): List<AppChapter> {
        val collator = Collator.getInstance()
        return when (order) {
            PageSortOrder.APP_NAME_ASC -> chapters.sortedWith { a, b -> collator.compare(a.label, b.label) }
            PageSortOrder.APP_NAME_DESC -> chapters.sortedWith { a, b -> collator.compare(b.label, a.label) }
            PageSortOrder.NOTIFICATION_AGE_NEWEST -> chapters.sortedByDescending {
                it.notifications.maxOfOrNull { n -> n.postTime } ?: 0L
            }
            PageSortOrder.NOTIFICATION_AGE_OLDEST -> chapters.sortedBy {
                it.notifications.maxOfOrNull { n -> n.postTime } ?: Long.MAX_VALUE
            }
        }
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
