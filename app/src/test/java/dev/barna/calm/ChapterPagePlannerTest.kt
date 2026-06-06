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

    private fun preferences(
        splitAppsByProfile: Boolean = false,
        placeWorkNotificationChaptersBeforeApps: Boolean = false,
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
    ): AppChapter {
        return AppChapter(
            packageName = packageName,
            label = label,
            notifications = emptyList(),
            launchable = true,
            hueColor = 0xff123456.toInt(),
            isWorkProfile = isWorkProfile,
        )
    }

    private fun app(packageName: String, label: String): AppEntry {
        return AppEntry(packageName = packageName, label = label, hueColor = 0xff123456.toInt())
    }
}
