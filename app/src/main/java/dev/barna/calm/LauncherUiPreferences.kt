package dev.barna.calm

data class LauncherUiPreferences(
    val useTintedNotificationCards: Boolean,
    val useCardIconBackgrounds: Boolean,
    val cardCornerRadiusDp: Int,
    val cardIconBlur: Int,
    val focusBlurRadius: Int,
    val splitAppsByProfile: Boolean,
    val placeWorkNotificationChaptersBeforeApps: Boolean,
    val cardHapticsEnabled: Boolean,
    val cardHapticStrength: Int,
    val cardStackTuning: CardStackTuning,
    val showAdvancedStackControls: Boolean,
    val cardVibrancy: Int,
    val pageSortOrder: PageSortOrder = PageSortOrder.DEFAULT,
    val expandedCardsEnabled: Boolean = true,
    val contactsPageEnabled: Boolean = false,
    val agendaPageEnabled: Boolean = false,
    val cardAppearance: CardAppearance = CardAppearance.DEFAULT,
    val pageLayout: LauncherPageLayout = LauncherPageLayout.DEFAULT,
)
