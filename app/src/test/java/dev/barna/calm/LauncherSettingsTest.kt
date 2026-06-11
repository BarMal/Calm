package dev.barna.calm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherSettingsTest {

    private lateinit var settings: LauncherSettings

    @Before
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_launcher_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = LauncherSettings(prefs)
    }

    // ---- defaults ----

    @Test
    fun defaultsForBooleans() {
        assertFalse(settings.useTintedNotificationCards())
        assertTrue(settings.useCardIconBackgrounds())
        assertFalse(settings.splitAppsByProfile())
        assertFalse(settings.placeWorkNotificationChaptersBeforeApps())
        assertTrue(settings.cardHapticsEnabled())
        assertFalse(settings.showAdvancedStackControls())
        assertFalse(settings.dockConfig().enabled)
    }

    @Test
    fun defaultsForInts() {
        assertEquals(18, settings.cardCornerRadiusDp())
        assertEquals(0, settings.cardIconBlur())
        assertEquals(7, settings.focusBlurRadius())
        assertEquals(2, settings.cardHapticStrength())
        assertEquals(50, settings.cardVibrancy())
    }

    @Test
    fun defaultCardStackTuning() {
        val tuning = settings.cardStackTuning()
        assertEquals(50, tuning.curve)
        assertEquals(0, tuning.horizontalCurve)
        assertEquals(50, tuning.arcWidth)
        assertEquals(2, tuning.aboveFocusCards)
        assertEquals(0, tuning.rotation)
        assertEquals(50, tuning.verticalSpacing)
        assertEquals(3, tuning.visibleCards)
        assertEquals(36, tuning.focusedCardGap)
        assertEquals(32, tuning.focusedCardScale)
        assertEquals(70, tuning.magnetStrength)
        assertEquals(20, tuning.stackPeakPosition)
    }

    @Test
    fun defaultDockConfig() {
        val dock = settings.dockConfig()
        assertFalse(dock.enabled)
        assertEquals(DockConfig.DEFAULT_ITEM_COUNT, dock.itemCount)
        assertEquals(DockConfig.DEFAULT_ITEM_SPAN, dock.itemSpan)
        assertEquals(DockConfig.DEFAULT_VERTICAL_PADDING_DP, dock.verticalPaddingDp)
        assertEquals(DockConfig.DEFAULT_HORIZONTAL_PADDING_DP, dock.horizontalPaddingDp)
    }

    @Test
    fun defaultPageSortOrderIsAppNameAsc() {
        assertEquals(PageSortOrder.APP_NAME_ASC, settings.pageSortOrder())
    }

    @Test
    fun defaultCollectionsAreEmpty() {
        assertTrue(settings.excludedPackages().isEmpty())
        assertTrue(settings.notificationFilters().isEmpty())
        assertTrue(settings.pinnedPackages().isEmpty())
        assertTrue(settings.hiddenAppKeys().isEmpty())
        assertTrue(settings.pinnedChapterPackages().isEmpty())
        assertTrue(settings.classicPages().isEmpty())
        assertTrue(settings.dockKeys().isEmpty())
    }

    @Test
    fun uiPreferencesHasCorrectDefaults() {
        val prefs = settings.uiPreferences()
        assertFalse(prefs.useTintedNotificationCards)
        assertTrue(prefs.useCardIconBackgrounds)
        assertEquals(18, prefs.cardCornerRadiusDp)
        assertEquals(0, prefs.cardIconBlur)
        assertEquals(7, prefs.focusBlurRadius)
        assertFalse(prefs.splitAppsByProfile)
        assertFalse(prefs.placeWorkNotificationChaptersBeforeApps)
        assertTrue(prefs.cardHapticsEnabled)
        assertEquals(2, prefs.cardHapticStrength)
        assertFalse(prefs.showAdvancedStackControls)
        assertEquals(50, prefs.cardVibrancy)
        assertEquals(PageSortOrder.APP_NAME_ASC, prefs.pageSortOrder)
    }

    // ---- round-trip correctness ----

    @Test
    fun cardCornerRadiusRoundTrips() {
        settings.setCardCornerRadiusDp(22)
        assertEquals(22, settings.cardCornerRadiusDp())
    }

    @Test
    fun cardIconBlurRoundTrips() {
        settings.setCardIconBlur(75)
        assertEquals(75, settings.cardIconBlur())
    }

    @Test
    fun focusBlurRadiusRoundTrips() {
        settings.setFocusBlurRadius(12)
        assertEquals(12, settings.focusBlurRadius())
    }

    @Test
    fun cardHapticStrengthRoundTrips() {
        settings.setCardHapticStrength(4)
        assertEquals(4, settings.cardHapticStrength())
    }

    @Test
    fun cardVibrancyRoundTrips() {
        settings.setCardVibrancy(30)
        assertEquals(30, settings.cardVibrancy())
    }

    @Test
    fun pageSortOrderRoundTrips() {
        settings.setPageSortOrder(PageSortOrder.NOTIFICATION_AGE_NEWEST)
        assertEquals(PageSortOrder.NOTIFICATION_AGE_NEWEST, settings.pageSortOrder())
    }

    @Test
    fun dockEnabledRoundTrips() {
        settings.setDockEnabled(true)
        assertTrue(settings.dockConfig().enabled)
    }

    @Test
    fun dockItemCountRoundTrips() {
        settings.setDockItemCount(6)
        assertEquals(6, settings.dockConfig().itemCount)
    }

    @Test
    fun dockItemSpanRoundTrips() {
        settings.setDockItemSpan(2)
        assertEquals(2, settings.dockConfig().itemSpan)
    }

    @Test
    fun classicPagesRoundTrip() {
        val page = ClassicLauncherPageDefinition(id = "classic-1", title = "Classic", enabled = true)

        settings.setClassicPages(listOf(page))

        assertEquals(listOf(page), settings.classicPages())
    }

    @Test
    fun enablingClassicPagesCreatesDefaultPage() {
        settings.setClassicPagesEnabled(true)

        val pages = settings.classicPages()
        assertEquals(1, pages.size)
        assertEquals("classic:classic-1", pages.single().key)
        assertEquals("Classic", pages.single().title)
        assertTrue(pages.single().enabled)
    }

    @Test
    fun disablingClassicPagesKeepsDefinitionButDisablesIt() {
        settings.setClassicPagesEnabled(true)

        settings.setClassicPagesEnabled(false)

        val pages = settings.classicPages()
        assertEquals(1, pages.size)
        assertFalse(pages.single().enabled)
        assertFalse(settings.classicPagesEnabled())
    }

    @Test
    fun cardStackCurveRoundTrips() {
        settings.setCardStackCurve(75)
        assertEquals(75, settings.cardStackTuning().curve)
    }

    @Test
    fun cardStackHorizontalCurveRoundTripsNegative() {
        settings.setCardStackHorizontalCurve(-50)
        assertEquals(-50, settings.cardStackTuning().horizontalCurve)
    }

    // ---- boundary / coerceIn clamping ----

    @Test
    fun cardCornerRadiusClampedToMax() {
        settings.setCardCornerRadiusDp(999)
        assertEquals(36, settings.cardCornerRadiusDp())
    }

    @Test
    fun cardCornerRadiusClampedToMin() {
        settings.setCardCornerRadiusDp(-1)
        assertEquals(0, settings.cardCornerRadiusDp())
    }

    @Test
    fun focusBlurRadiusClampedToMax() {
        settings.setFocusBlurRadius(100)
        assertEquals(24, settings.focusBlurRadius())
    }

    @Test
    fun cardHapticStrengthClampedToMin() {
        settings.setCardHapticStrength(0)
        assertEquals(1, settings.cardHapticStrength())
    }

    @Test
    fun cardHapticStrengthClampedToMax() {
        settings.setCardHapticStrength(99)
        assertEquals(5, settings.cardHapticStrength())
    }

    @Test
    fun dockItemCountClampedToMin() {
        settings.setDockItemCount(0)
        assertEquals(DockConfig.MIN_ITEM_COUNT, settings.dockConfig().itemCount)
    }

    @Test
    fun dockItemCountClampedToMax() {
        settings.setDockItemCount(100)
        assertEquals(DockConfig.MAX_ITEM_COUNT, settings.dockConfig().itemCount)
    }

    @Test
    fun dockItemSpanClampedToMin() {
        settings.setDockItemSpan(0)
        assertEquals(DockConfig.MIN_ITEM_SPAN, settings.dockConfig().itemSpan)
    }

    @Test
    fun dockItemSpanClampedToMax() {
        settings.setDockItemSpan(100)
        assertEquals(DockConfig.MAX_ITEM_SPAN, settings.dockConfig().itemSpan)
    }

    // ---- toggle semantics ----

    @Test
    fun toggleCardHapticsReturnsNewValue() {
        val first = settings.toggleCardHaptics()
        assertFalse(first)
        val second = settings.toggleCardHaptics()
        assertTrue(second)
    }

    @Test
    fun doubleToggleCardHapticsRestoresOriginal() {
        val original = settings.cardHapticsEnabled()
        settings.toggleCardHaptics()
        settings.toggleCardHaptics()
        assertEquals(original, settings.cardHapticsEnabled())
    }

    @Test
    fun toggleSplitAppsByProfileReturnsNewValue() {
        val toggled = settings.toggleSplitAppsByProfile()
        assertTrue(toggled)
        assertEquals(toggled, settings.splitAppsByProfile())
    }

    @Test
    fun toggleNotificationSurfaceReturnsNewValue() {
        val toggled = settings.toggleNotificationSurface()
        assertTrue(toggled)
        assertEquals(toggled, settings.useTintedNotificationCards())
    }

    @Test
    fun toggleAdvancedStackControlsDoubleToggleRestoresOriginal() {
        val original = settings.showAdvancedStackControls()
        settings.toggleAdvancedStackControls()
        settings.toggleAdvancedStackControls()
        assertEquals(original, settings.showAdvancedStackControls())
    }

    @Test
    fun toggleCardIconBackgroundsReturnsNewValue() {
        val toggled = settings.toggleCardIconBackgrounds()
        assertFalse(toggled)
        assertEquals(toggled, settings.useCardIconBackgrounds())
    }

    @Test
    fun expandedCardsEnabledByDefault() {
        assertTrue(settings.expandedCardsEnabled())
    }

    @Test
    fun toggleExpandedCardsReturnsNewValue() {
        val toggled = settings.toggleExpandedCards()
        assertFalse(toggled)
        assertEquals(toggled, settings.expandedCardsEnabled())
    }

    // ---- pinned / hidden apps ----

    @Test
    fun pinPackageAppearsInPinnedPackages() {
        settings.pinPackage("com.a")
        assertTrue("com.a" in settings.pinnedPackages())
    }

    @Test
    fun unpinPackageRemovedFromPinnedPackages() {
        settings.pinPackage("com.a")
        settings.unpinPackage("com.a")
        assertFalse("com.a" in settings.pinnedPackages())
    }

    @Test
    fun hideAppAppearsInHiddenKeys() {
        settings.hideApp("key.a", "App A")
        assertTrue("key.a" in settings.hiddenAppKeys())
    }

    @Test
    fun showAppRemovedFromHiddenKeys() {
        settings.hideApp("key.a", "App A")
        settings.showApp("key.a")
        assertFalse("key.a" in settings.hiddenAppKeys())
    }

    // ---- dock key list operations ----

    @Test
    fun addDockKeyAppendsToEnd() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        assertEquals(listOf("com.a", "com.b"), settings.dockKeys())
    }

    @Test
    fun addDockKeyReturnsFalseWhenFull() {
        settings.setDockItemCount(DockConfig.MIN_ITEM_COUNT)
        repeat(DockConfig.MIN_ITEM_COUNT) { settings.addDockKey("com.$it") }
        assertFalse(settings.addDockKey("com.extra"))
    }

    @Test
    fun addDockKeyReturnsFalseForDuplicate() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        assertFalse(settings.addDockKey("com.a"))
        assertEquals(1, settings.dockKeys().size)
    }

    @Test
    fun removeDockKeyRemovesExactlyOneOccurrence() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        settings.removeDockKey("com.a")
        assertEquals(listOf("com.b"), settings.dockKeys())
    }

    @Test
    fun moveDockKeyToNewPosition() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        settings.addDockKey("com.c")
        settings.moveDockKey("com.a", 2)
        assertEquals(listOf("com.b", "com.c", "com.a"), settings.dockKeys())
    }

    @Test
    fun moveDockKeyToSamePositionIsNoOp() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        settings.moveDockKey("com.b", 1)
        assertEquals(listOf("com.a", "com.b"), settings.dockKeys())
    }

    @Test
    fun moveDockKeyToFirstPosition() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        settings.addDockKey("com.c")
        settings.moveDockKey("com.c", 0)
        assertEquals(listOf("com.c", "com.a", "com.b"), settings.dockKeys())
    }

    @Test
    fun moveDockKeyOutOfBoundsIsClampedToLast() {
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.addDockKey("com.a")
        settings.addDockKey("com.b")
        settings.moveDockKey("com.a", 999)
        assertEquals(listOf("com.b", "com.a"), settings.dockKeys())
    }

    // ---- notification filter integration ----

    @Test
    fun addNotificationFilterSurvivesRoundTrip() {
        val filter = NotificationFilter(
            kind = NotificationFilter.Kind.TITLE,
            packageName = "com.example",
            value = "promo",
        )
        settings.addNotificationFilter(filter)

        val retrieved = settings.notificationFilters()
        assertEquals(1, retrieved.size)
        assertEquals(filter, retrieved.single())
    }

    @Test
    fun multipleNotificationFiltersSurviveMultipleRoundTrips() {
        val f1 = NotificationFilter(NotificationFilter.Kind.APP, "com.a", "")
        val f2 = NotificationFilter(NotificationFilter.Kind.TITLE, "com.b", "promo")
        settings.addNotificationFilter(f1)
        settings.addNotificationFilter(f2)

        val retrieved = settings.notificationFilters().toSet()
        assertEquals(setOf(f1, f2), retrieved)
    }

    // ---- launcherChangeToken ----

    @Test
    fun changeTokenAltersAfterMutation() {
        val before = settings.launcherChangeToken()
        settings.toggleNotificationSurface()
        val after = settings.launcherChangeToken()
        assertNotEquals(before, after)
    }

    @Test
    fun changeTokenDoesNotAlterAfterReadOnlyCall() {
        val before = settings.launcherChangeToken()
        settings.uiPreferences()
        settings.excludedPackages()
        settings.pinnedPackages()
        val after = settings.launcherChangeToken()
        assertEquals(before, after)
    }

    @Test
    fun changeTokenAltersAfterDockConfigMutation() {
        val before = settings.launcherChangeToken()
        settings.setDockEnabled(true)
        settings.setDockItemCount(DockConfig.MAX_ITEM_COUNT)
        settings.setDockItemSpan(DockConfig.MAX_ITEM_SPAN)
        settings.setDockVerticalPadding(DockConfig.MAX_VERTICAL_PADDING_DP)
        settings.setDockHorizontalPadding(DockConfig.MAX_HORIZONTAL_PADDING_DP)
        val after = settings.launcherChangeToken()
        assertNotEquals(before, after)
    }

    @Test
    fun changeTokenAltersAfterClassicPageMutation() {
        val before = settings.launcherChangeToken()
        settings.setClassicPagesEnabled(true)
        val after = settings.launcherChangeToken()
        assertNotEquals(before, after)
    }

    @Test
    fun changeTokenAltersAfterDockKeysMutation() {
        val before = settings.launcherChangeToken()
        settings.setDockKeys(listOf("com.example.one", "com.example.two"))
        val after = settings.launcherChangeToken()
        assertNotEquals(before, after)
    }

    // ---- app hue caching ----

    @Test
    fun cachedHueDefaultIsZero() {
        assertEquals(0, settings.cachedAppHue("com.example"))
    }

    @Test
    fun cacheAppHueRoundTrips() {
        settings.cacheAppHue("com.example", 0xFF3344)
        assertEquals(0xFF3344, settings.cachedAppHue("com.example"))
    }

    @Test
    fun cacheAppHueIgnoresZero() {
        settings.cacheAppHue("com.example", 0xFF3344)
        settings.cacheAppHue("com.example", 0)
        assertEquals(0xFF3344, settings.cachedAppHue("com.example"))
    }

    // ---- notification grouping ----

    @Test
    fun groupNotificationsDefaultsToTrue() {
        assertTrue(settings.groupNotifications("com.example"))
    }

    @Test
    fun toggleNotificationGroupingUngroupsThenGroups() {
        val grouped = settings.toggleNotificationGrouping("com.example")
        assertFalse(grouped)
        assertFalse(settings.groupNotifications("com.example"))

        val regrouped = settings.toggleNotificationGrouping("com.example")
        assertTrue(regrouped)
        assertTrue(settings.groupNotifications("com.example"))
    }

    // ---- app library snapshot ----

    @Test
    fun cachedSnapshotDefaultIsNull() {
        assertNull(settings.cachedLaunchableAppsSnapshot())
    }

    @Test
    fun snapshotRoundTripsViaSettings() {
        val apps = listOf(
            AppEntry("com.a", "A", hueColor = 0),
            AppEntry("com.b", "B", hueColor = 0),
        )
        val snapshot = AppLibrarySnapshot.from(apps)
        settings.cacheLaunchableAppsSnapshot(snapshot)
        val loaded = settings.cachedLaunchableAppsSnapshot()

        assertEquals(snapshot.fingerprint, loaded?.fingerprint)
        assertEquals(snapshot.apps, loaded?.apps)
    }

    @Test
    fun clearLaunchableAppsSnapshotRemovesIt() {
        settings.cacheLaunchableAppsSnapshot(AppLibrarySnapshot.from(emptyList()))
        settings.clearLaunchableAppsSnapshot()
        assertNull(settings.cachedLaunchableAppsSnapshot())
    }

    // ---- timescape preset ----

    @Test
    fun timescapePresetAppliesExpectedValues() {
        settings.applyTimescapeStackPreset()
        val tuning = settings.cardStackTuning()
        assertEquals(86, tuning.horizontalCurve)
        assertEquals(78, tuning.arcWidth)
        assertEquals(3, tuning.aboveFocusCards)
        assertEquals(24, tuning.rotation)
        assertEquals(76, tuning.curve)
        assertEquals(42, tuning.verticalSpacing)
        assertEquals(4, tuning.visibleCards)
        assertEquals(54, tuning.focusedCardGap)
        assertEquals(42, tuning.focusedCardScale)
        assertEquals(82, tuning.magnetStrength)
    }

    // ---- page layout ----

    @Test
    fun disablingDefaultHomeMovesHomeToFirstEnabledSlot() {
        settings.setPageLayoutOrder(listOf(PageSlot.APPS, PageSlot.OVERVIEW, PageSlot.NOTIFICATIONS))
        settings.setDefaultHomeSlot(PageSlot.OVERVIEW)

        settings.setPageSlotEnabled(PageSlot.OVERVIEW, false)

        val layout = settings.pageLayout()
        assertEquals(PageSlot.APPS, layout.defaultHome)
        assertTrue(PageSlot.OVERVIEW in layout.disabled)
    }

    @Test
    fun settingDisabledSlotAsHomeReEnablesIt() {
        settings.setPageSlotEnabled(PageSlot.CONTACTS, false)

        settings.setDefaultHomeSlot(PageSlot.CONTACTS)

        val layout = settings.pageLayout()
        assertEquals(PageSlot.CONTACTS, layout.defaultHome)
        assertFalse(PageSlot.CONTACTS in layout.disabled)
    }

    @Test
    fun storedDisabledHomeIsNormalizedOnRead() {
        settings.setPageLayoutOrder(listOf(PageSlot.APPS, PageSlot.OVERVIEW, PageSlot.NOTIFICATIONS))
        settings.setDefaultHomeSlot(PageSlot.OVERVIEW)
        settings.setPageSlotEnabled(PageSlot.OVERVIEW, false)

        assertEquals(PageSlot.APPS, settings.pageLayout().defaultHome)
    }
}
