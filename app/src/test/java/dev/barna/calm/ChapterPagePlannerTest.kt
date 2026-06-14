package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPagePlannerTest {
    private val planner = ChapterPagePlanner()

    @Test
    fun combinedAppsAppearBeforePinnedOverviewAndNotifications() {
        val pages = planner.buildPages(
            preferences = preferences(splitAppsByProfile = false),
            notificationChapters = listOf(chapter("chat", "Chat")),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = listOf(app("browser", "Browser")),
        )

        assertEquals(listOf("Apps", "Pinned", "Overview", "Chat"), pages.map { it.title })
        assertEquals(listOf("I", "II", "III", "IV"), pages.map { it.marker })
    }

    @Test
    fun splitProfilesOnlyAddsWorkPageWhenWorkAppsExist() {
        val pages = planner.buildPages(
            preferences = preferences(splitAppsByProfile = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("personal", "Personal")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Personal apps", "Overview"), pages.map { it.title })
        assertEquals(listOf(AppLibraryScope.PERSONAL, null), pages.map { it.appScope })
    }

    @Test
    fun workNotificationsCanBePlacedBeforeApps() {
        val pages = planner.buildPages(
            preferences = preferences(placeWorkNotificationChaptersBeforeApps = true),
            notificationChapters = listOf(
                chapter("work", "Work chat", isWorkProfile = true),
                chapter("personal", "Personal chat"),
            ),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Work chat", "Apps", "Overview", "Personal chat"), pages.map { it.title })
        assertEquals(listOf("I", "II", "III", "IV"), pages.map { it.marker })
    }

    @Test
    fun splitProfilesPlacesWorkNotificationsBeforeAppPages() {
        val pages = planner.buildPages(
            preferences = preferences(splitAppsByProfile = true),
            notificationChapters = listOf(
                chapter("personal", "Personal chat"),
                chapter("work", "Work chat", isWorkProfile = true),
            ),
            appEntries = listOf(app("personal.app", "Personal"), app("work.app", "Work", work = true)),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Work chat", "Work apps", "Personal apps", "Overview", "Work", "Personal chat"), pages.map { it.title })
    }

    @Test
    fun classicPagesAppearBeforeNotificationChapters() {
        val classic = ClassicLauncherPageDefinition(id = "classic-1", title = "Classic")
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = listOf(chapter("chat", "Chat")),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
            classicPages = listOf(classic),
        )

        assertEquals(listOf("Apps", "Overview", "Classic", "Chat"), pages.map { it.title })
        assertEquals(classic, pages.first { it.classicPage != null }.classicPage)
        assertEquals(listOf("I", "II", "III", "IV"), pages.map { it.marker })
    }

    @Test
    fun agendaPageAppearsWhenEnabledBeforeOverview() {
        val pages = planner.buildPages(
            preferences = preferences(agendaPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Agenda", "Overview"), pages.map { it.title })
        assertEquals(PageSlot.AGENDA, PageArranger.slotOf(pages[1]))
        assertEquals(listOf("I", "II", "III"), pages.map { it.marker })
    }

    @Test
    fun alarmsPageAppearsWhenEnabledAfterAgendaAndBeforeOverview() {
        val pages = planner.buildPages(
            preferences = preferences(agendaPageEnabled = true, alarmsPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Agenda", "Alarms", "Overview"), pages.map { it.title })
        assertEquals(PageSlot.ALARMS, PageArranger.slotOf(pages[2]))
        assertEquals(listOf("I", "II", "III", "IV"), pages.map { it.marker })
    }

    @Test
    fun pinnedPageAppearsWhenEnabledWithoutPinnedApps() {
        val pages = planner.buildPages(
            preferences = preferences(pinnedPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Pinned", "Overview"), pages.map { it.title })
        assertEquals(PageSlot.PINNED, PageArranger.slotOf(pages[1]))
        assertEquals(listOf("I", "II", "III"), pages.map { it.marker })
    }

    @Test
    fun legacyDisabledClassicPagesStillAppearWhenAdded() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
            classicPages = listOf(ClassicLauncherPageDefinition(id = "classic-1", title = "Classic", enabled = false)),
        )

        assertEquals(listOf("Apps", "Overview", "Classic"), pages.map { it.title })
    }

    @Test
    fun categoryFolderPageAppearsAfterAppsWhenGroupingEnabledAndAssignmentsExist() {
        val pages = planner.buildPages(
            preferences = preferences(appGroupingEnabled = true, hasAnyCategoryAssignments = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Categories", "Overview"), pages.map { it.title })
        assertEquals(CalmTheme.CATEGORY_FOLDER_KEY, pages[1].key)
    }

    @Test
    fun categoryFolderPageAbsentWhenGroupingDisabled() {
        val pages = planner.buildPages(
            preferences = preferences(appGroupingEnabled = false, hasAnyCategoryAssignments = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Overview"), pages.map { it.title })
    }

    @Test
    fun categoryFolderPageAbsentWhenNoAssignments() {
        val pages = planner.buildPages(
            preferences = preferences(appGroupingEnabled = true, hasAnyCategoryAssignments = false),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Overview"), pages.map { it.title })
    }

    @Test
    fun dynamicPagesModeAddsOnePagePerEnabledCategoryWithAssignments() {
        val cats = listOf(
            AppCategory("communications", "Communications"),
            AppCategory("media", "Media"),
        )
        val pages = planner.buildPages(
            preferences = preferences(
                appGroupingEnabled = true,
                hasAnyCategoryAssignments = true,
                categoryDisplayMode = CategoryDisplayMode.DYNAMIC_PAGES,
                enabledCategoriesForDynamicPages = cats,
            ),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Communications", "Media", "Overview"), pages.map { it.title })
        assertEquals(CalmTheme.CATEGORY_PAGE_KEY_PREFIX + "communications", pages[1].key)
        assertEquals(CalmTheme.CATEGORY_PAGE_KEY_PREFIX + "media", pages[2].key)
    }

    @Test
    fun dynamicPagesModeSlotIsCategories() {
        val cats = listOf(AppCategory("media", "Media"))
        val pages = planner.buildPages(
            preferences = preferences(
                appGroupingEnabled = true,
                hasAnyCategoryAssignments = true,
                categoryDisplayMode = CategoryDisplayMode.DYNAMIC_PAGES,
                enabledCategoriesForDynamicPages = cats,
            ),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        val categoryPage = pages.first { it.key.startsWith(CalmTheme.CATEGORY_PAGE_KEY_PREFIX) }
        assertEquals(PageSlot.CATEGORIES, PageArranger.slotOf(categoryPage))
    }

    @Test
    fun dynamicPagesModeWithNoCategoriesAddsNoPages() {
        val pages = planner.buildPages(
            preferences = preferences(
                appGroupingEnabled = true,
                hasAnyCategoryAssignments = true,
                categoryDisplayMode = CategoryDisplayMode.DYNAMIC_PAGES,
                enabledCategoriesForDynamicPages = emptyList(),
            ),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Overview"), pages.map { it.title })
    }

    @Test
    fun cardStackModeStillAddsSingleFolderPage() {
        val pages = planner.buildPages(
            preferences = preferences(
                appGroupingEnabled = true,
                hasAnyCategoryAssignments = true,
                categoryDisplayMode = CategoryDisplayMode.CARD_STACK,
            ),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser", "Browser")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Categories", "Overview"), pages.map { it.title })
        assertEquals(CalmTheme.CATEGORY_FOLDER_KEY, pages[1].key)
    }

    private fun preferences(
        splitAppsByProfile: Boolean = false,
        placeWorkNotificationChaptersBeforeApps: Boolean = false,
        sortOrder: PageSortOrder = PageSortOrder.DEFAULT,
        pinnedPageEnabled: Boolean = false,
        agendaPageEnabled: Boolean = false,
        alarmsPageEnabled: Boolean = false,
        appGroupingEnabled: Boolean = false,
        hasAnyCategoryAssignments: Boolean = false,
        categoryDisplayMode: CategoryDisplayMode = CategoryDisplayMode.CARD_STACK,
        enabledCategoriesForDynamicPages: List<AppCategory> = emptyList(),
    ): LauncherUiPreferences {
        return LauncherUiPreferences(
            useTintedNotificationCards = true,
            useCardIconBackgrounds = true,
            cardCornerRadiusDp = 22,
            cardIconBlur = 0,
            focusBlurRadius = 0,
            splitAppsByProfile = splitAppsByProfile,
            placeWorkNotificationChaptersBeforeApps = placeWorkNotificationChaptersBeforeApps,
            cardHapticsEnabled = false,
            cardHapticStrength = 1,
            cardStackTuning = defaultCardStackTuning(),
            showAdvancedStackControls = false,
            cardVibrancy = 50,
            pageSortOrder = sortOrder,
            pinnedPageEnabled = pinnedPageEnabled,
            agendaPageEnabled = agendaPageEnabled,
            alarmsPageEnabled = alarmsPageEnabled,
            appGroupingEnabled = appGroupingEnabled,
            hasAnyCategoryAssignments = hasAnyCategoryAssignments,
            categoryDisplayMode = categoryDisplayMode,
            enabledCategoriesForDynamicPages = enabledCategoriesForDynamicPages,
        )
    }

    private fun defaultCardStackTuning(): CardStackTuning {
        return CardStackTuning(
            curve = 50,
            horizontalCurve = 0,
            arcWidth = 50,
            aboveFocusCards = 2,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = 3,
        )
    }

    private fun chapter(
        packageName: String,
        label: String,
        isWorkProfile: Boolean = false,
        postTime: Long? = null,
    ): AppChapter {
        val notifications = if (postTime != null) listOf(
            CalmNotificationListenerService.CalmNotification(
                key = "key_$packageName",
                packageName = packageName,
                title = "Test",
                text = "",
                subText = "",
                conversationTitle = "",
                postTime = postTime,
                contentIntent = null,
                backgroundImage = null,
                actions = emptyList(),
            )
        ) else emptyList()
        return AppChapter(
            packageName = packageName,
            label = label,
            notifications = notifications,
            launchable = true,
            hueColor = 0xff123456.toInt(),
            isWorkProfile = isWorkProfile,
        )
    }

    private fun app(packageName: String, label: String, work: Boolean = false): AppEntry {
        return AppEntry(packageName = packageName, label = label, hueColor = 0xff123456.toInt(), isWorkProfile = work)
    }

    // --- Pinned chapters (#62) ---

    @Test
    fun pinnedChapterAppearsWhenNoActiveNotifications() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = emptyList(),
            appEntries = listOf(app("com.whatsapp", "WhatsApp")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.whatsapp"),
        )

        assertEquals(listOf("Apps", "Overview", "WhatsApp"), pages.map { it.title })
    }

    @Test
    fun pinnedChapterWithActiveNotificationsUsesLiveChapter() {
        // Live chapter has a distinct hueColor so we can verify it's used rather than a stub
        val liveChapter = AppChapter(
            packageName = "com.whatsapp",
            label = "WhatsApp",
            notifications = emptyList(),
            launchable = true,
            hueColor = 0xffABCDEF.toInt(),
        )
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = listOf(liveChapter),
            appEntries = listOf(app("com.whatsapp", "WhatsApp")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.whatsapp"),
        )

        assertEquals(listOf("Apps", "Overview", "WhatsApp"), pages.map { it.title })
        assertEquals(1, pages.count { it.chapter != null })
        assertEquals(liveChapter, pages.first { it.chapter != null }.chapter)
    }

    @Test
    fun pinnedChapterNotInAppEntriesIsIgnored() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = emptyList(),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.notinstalled"),
        )

        assertEquals(listOf("Apps", "Overview"), pages.map { it.title })
    }

    @Test
    fun multiplePinnedChaptersAllIncluded() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = emptyList(),
            appEntries = listOf(app("com.a", "AppA"), app("com.b", "AppB")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.a", "com.b"),
        )

        val titles = pages.map { it.title }
        assert("AppA" in titles) { "Expected AppA in $titles" }
        assert("AppB" in titles) { "Expected AppB in $titles" }
    }

    @Test
    fun pinnedStubsAppearsBeforeUnpinnedNotificationChapters() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = listOf(chapter("com.email", "Email")),
            appEntries = listOf(app("com.whatsapp", "WhatsApp")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.whatsapp"),
        )

        val titles = pages.map { it.title }
        assertEquals(listOf("Apps", "Overview", "WhatsApp", "Email"), titles)
    }

    @Test
    fun pinnedChaptersHaveSequentialMarkersAfterOverview() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = emptyList(),
            appEntries = listOf(app("com.a", "AppA")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.a"),
        )

        assertEquals(listOf("I", "II", "III"), pages.map { it.marker })
    }

    @Test
    fun pinnedWorkChapterPlacedBeforeAppsWhenPreferenceSet() {
        val pages = planner.buildPages(
            preferences = preferences(placeWorkNotificationChaptersBeforeApps = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("com.workmail", "Work Mail", work = true)),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("com.workmail"),
        )

        assertEquals(listOf("Work Mail", "Apps", "Overview"), pages.map { it.title })
    }

    @Test
    fun noPinnedChaptersLeavesExistingBehaviourUnchanged() {
        val pages = planner.buildPages(
            preferences = preferences(),
            notificationChapters = listOf(chapter("com.a", "AppA")),
            appEntries = listOf(app("com.a", "AppA")),
            pinnedApps = emptyList(),
        )

        assertEquals(listOf("Apps", "Overview", "AppA"), pages.map { it.title })
    }

    // --- Page sorting (#126) ---

    @Test
    fun sortByAppNameAscOrders() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.APP_NAME_ASC),
            notificationChapters = listOf(
                chapter("c", "Zeta"),
                chapter("b", "Alpha"),
                chapter("a", "Mango"),
            ),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
        )

        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("Alpha", "Mango", "Zeta"), chapterTitles)
    }

    @Test
    fun sortByAppNameDescOrders() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.APP_NAME_DESC),
            notificationChapters = listOf(
                chapter("c", "Zeta"),
                chapter("b", "Alpha"),
                chapter("a", "Mango"),
            ),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
        )

        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("Zeta", "Mango", "Alpha"), chapterTitles)
    }

    @Test
    fun sortByNotificationAgeNewestFirst() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.NOTIFICATION_AGE_NEWEST),
            notificationChapters = listOf(
                chapter("a", "OldApp", postTime = 1000L),
                chapter("b", "NewApp", postTime = 5000L),
                chapter("c", "MidApp", postTime = 3000L),
            ),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
        )

        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("NewApp", "MidApp", "OldApp"), chapterTitles)
    }

    @Test
    fun sortByNotificationAgeOldestFirst() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.NOTIFICATION_AGE_OLDEST),
            notificationChapters = listOf(
                chapter("a", "OldApp", postTime = 1000L),
                chapter("b", "NewApp", postTime = 5000L),
                chapter("c", "MidApp", postTime = 3000L),
            ),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
        )

        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("OldApp", "MidApp", "NewApp"), chapterTitles)
    }

    @Test
    fun sortDoesNotReorderPinnedChapters() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.APP_NAME_ASC),
            notificationChapters = emptyList(),
            appEntries = listOf(app("z.pkg", "Zebra"), app("a.pkg", "Ant")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("z.pkg", "a.pkg"),
        )

        // Pinned insertion order: z.pkg first, a.pkg second — if sort applied it would be [Ant, Zebra]
        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("Zebra", "Ant"), chapterTitles)
    }

    @Test
    fun unpinnedChaptersSortedIndependentlyOfPinned() {
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.APP_NAME_ASC),
            notificationChapters = listOf(
                chapter("c.pkg", "Zeta"),
                chapter("b.pkg", "Beta"),
            ),
            appEntries = listOf(app("a.pkg", "Alpha")),
            pinnedApps = emptyList(),
            pinnedChapterPackages = setOf("a.pkg"),
        )

        // Alpha is pinned (comes first among chapters), then Beta < Zeta alphabetically
        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("Alpha", "Beta", "Zeta"), chapterTitles)
    }

    @Test
    fun chaptersWithNoNotificationsSortLastForNewest() {
        // A chapter with no notifications has postTime fallback 0, so sorts after real notifications
        val pages = planner.buildPages(
            preferences = preferences(sortOrder = PageSortOrder.NOTIFICATION_AGE_NEWEST),
            notificationChapters = listOf(
                chapter("a", "NoNotifApp"),
                chapter("b", "RealApp", postTime = 1000L),
            ),
            appEntries = emptyList(),
            pinnedApps = emptyList(),
        )

        val chapterTitles = pages.filter { it.chapter != null }.map { it.title }
        assertEquals(listOf("RealApp", "NoNotifApp"), chapterTitles)
    }
}
