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
)
