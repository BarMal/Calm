package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPageStateTest {
    private val factory = LauncherPageStateFactory()

    @Test
    fun resolvesPinnedAppsAndBuildsPages() {
        val browser = app("browser.pkg", "Browser")
        val maps = app("maps.pkg", "Maps")
        val preferences = preferences(splitAppsByProfile = false)

        val state = factory.create(
            preferences = preferences,
            notificationChapters = listOf(chapter("chat.pkg", "Chat")),
            appEntries = listOf(browser, maps),
            pinnedKeys = setOf("maps.pkg"),
        )

        assertEquals(listOf(maps), state.pinnedApps)
        assertEquals(listOf("Apps", "Pinned", "Overview", "Chat"), state.pages.map { it.title })
    }

    @Test
    fun omitsPinnedPageWhenNoPinnedAppsResolve() {
        val preferences = preferences(splitAppsByProfile = false)

        val state = factory.create(
            preferences = preferences,
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = setOf("missing.pkg"),
        )

        assertEquals(emptyList<AppEntry>(), state.pinnedApps)
        assertEquals(listOf("Apps", "Overview"), state.pages.map { it.title })
    }

    @Test
    fun preservesWorkBeforeAppsPlanning() {
        val preferences = preferences(placeWorkNotificationChaptersBeforeApps = true)

        val state = factory.create(
            preferences = preferences,
            notificationChapters = listOf(
                chapter("work.pkg", "Work", isWorkProfile = true),
                chapter("personal.pkg", "Personal"),
            ),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = emptySet(),
        )

        assertEquals(listOf("Work", "Apps", "Overview", "Personal"), state.pages.map { it.title })
    }

    @Test
    fun includesEnabledClassicPages() {
        val preferences = preferences()
        val classic = ClassicLauncherPageDefinition(id = "classic-1", title = "Classic")

        val state = factory.create(
            preferences = preferences,
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = emptySet(),
            classicPages = listOf(classic),
        )

        assertEquals(listOf("Apps", "Overview", "Classic"), state.pages.map { it.title })
        assertEquals(classic, state.pages.last().classicPage)
    }

    @Test
    fun includesAgendaPageWhenEnabled() {
        val state = factory.create(
            preferences = preferences(agendaPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = emptySet(),
        )

        assertEquals(listOf("Apps", "Agenda", "Overview"), state.pages.map { it.title })
    }

    @Test
    fun includesAlarmsPageWhenEnabled() {
        val state = factory.create(
            preferences = preferences(alarmsPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = emptySet(),
        )

        assertEquals(listOf("Apps", "Alarms", "Overview"), state.pages.map { it.title })
    }

    @Test
    fun includesPinnedPageWhenEnabledWithoutPinnedApps() {
        val state = factory.create(
            preferences = preferences(pinnedPageEnabled = true),
            notificationChapters = emptyList(),
            appEntries = listOf(app("browser.pkg", "Browser")),
            pinnedKeys = emptySet(),
        )

        assertEquals(emptyList<AppEntry>(), state.pinnedApps)
        assertEquals(listOf("Apps", "Pinned", "Overview"), state.pages.map { it.title })
    }

    private fun preferences(
        splitAppsByProfile: Boolean = false,
        placeWorkNotificationChaptersBeforeApps: Boolean = false,
        pinnedPageEnabled: Boolean = false,
        agendaPageEnabled: Boolean = false,
        alarmsPageEnabled: Boolean = false,
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
            pinnedPageEnabled = pinnedPageEnabled,
            agendaPageEnabled = agendaPageEnabled,
            alarmsPageEnabled = alarmsPageEnabled,
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

    private fun app(packageName: String, label: String): AppEntry {
        return AppEntry(packageName = packageName, label = label, hueColor = 0xff123456.toInt())
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
}
