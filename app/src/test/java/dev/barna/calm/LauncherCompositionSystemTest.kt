package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherCompositionSystemTest {
    private val factory = LauncherRenderModelFactory()

    @Test
    fun renderModelComposesClassicDynamicPagesAndDock() {
        val alpha = app("alpha", "Alpha")
        val beta = app("beta", "Beta")
        val classic = ClassicLauncherPageDefinition(
            id = "classic-1",
            title = "Home grid",
            items = listOf(ClassicGridItem.app(alpha.identityKey, x = 0, y = 0)),
        )
        val notificationChapter = AppChapter(
            packageName = "chat.pkg",
            label = "Chat",
            notifications = listOf(notification("chat-1", "Chat")),
            launchable = true,
            hueColor = 0xff123456.toInt(),
            identityKey = "chat.pkg",
            launcherIdentityKey = "chat.pkg",
        )
        val preferences = preferences(
            pageLayout = LauncherPageLayout(
                order = listOf(
                    PageSlot.CLASSIC_PAGES,
                    PageSlot.APPS,
                    PageSlot.PINNED,
                    PageSlot.OVERVIEW,
                    PageSlot.NOTIFICATIONS,
                ),
                disabled = emptySet(),
                defaultHome = PageSlot.CLASSIC_PAGES,
            ),
        )

        val model = factory.create(
            preferences = preferences,
            notificationChapters = listOf(notificationChapter),
            appEntries = listOf(alpha, beta),
            pinnedKeys = setOf(beta.identityKey),
            classicPages = listOf(classic),
            dockConfig = DockConfig(enabled = true, style = DockStyle.HYBRID, itemSpan = 2),
            dockKeys = listOf(alpha.identityKey),
            hasCalendarPermission = false,
            calendarEvents = emptyList(),
        )

        assertEquals(
            listOf(PageSlot.CLASSIC_PAGES, PageSlot.APPS, PageSlot.PINNED, PageSlot.OVERVIEW, PageSlot.NOTIFICATIONS),
            model.pages.map(PageArranger::slotOf),
        )
        assertEquals(classic, model.pages.first().classicPage)
        assertEquals(listOf(beta), model.pinnedApps)
        assertEquals(listOf(alpha), model.dockApps)
        assertEquals(DockStyle.HYBRID, model.dockConfig.style)
    }

    private fun preferences(pageLayout: LauncherPageLayout): LauncherUiPreferences {
        return LauncherUiPreferences(
            useTintedNotificationCards = true,
            useCardIconBackgrounds = true,
            cardCornerRadiusDp = 22,
            cardIconBlur = 0,
            focusBlurRadius = 0,
            splitAppsByProfile = false,
            placeWorkNotificationChaptersBeforeApps = false,
            cardHapticsEnabled = false,
            cardHapticStrength = 1,
            cardStackTuning = CardStackTuning(
                curve = 50,
                horizontalCurve = 0,
                arcWidth = 50,
                aboveFocusCards = 2,
                rotation = 0,
                verticalSpacing = 50,
                visibleCards = 3,
            ),
            showAdvancedStackControls = false,
            cardVibrancy = 50,
            pinnedPageEnabled = true,
            pageLayout = pageLayout,
        )
    }

    private fun app(identityKey: String, label: String): AppEntry {
        return AppEntry(
            packageName = "$identityKey.pkg",
            label = label,
            hueColor = 0xff123456.toInt(),
            identityKey = identityKey,
        )
    }

    private fun notification(key: String, title: String): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = key,
            packageName = "chat.pkg",
            title = title,
            text = "Body",
            subText = "",
            conversationTitle = "",
            postTime = 20,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}
