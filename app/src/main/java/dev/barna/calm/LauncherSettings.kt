package dev.barna.calm

import android.content.Context
import android.content.SharedPreferences
import java.text.Collator

class LauncherSettings(private val context: Context) {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun excludedPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_EXCLUDED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun notificationFilters(): List<NotificationFilter> {
        return preferences.getStringSet(PREF_NOTIFICATION_FILTERS, emptySet())
            .orEmpty()
            .mapNotNull(NotificationFilter::decode)
    }

    fun pinnedPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_PINNED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun hiddenAppKeys(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_HIDDEN_APP_KEYS, emptySet()) ?: emptySet())
    }

    fun setHiddenAppKeys(appKeys: Set<String>) {
        preferences.edit().putStringSet(PREF_HIDDEN_APP_KEYS, HashSet(appKeys)).apply()
    }

    fun isAppHidden(app: AppEntry): Boolean {
        return AppVisibility.isHidden(app, hiddenAppKeys())
    }

    fun pinPackage(packageName: String) {
        val pinned = pinnedPackages().toMutableSet()
        pinned.add(packageName)
        preferences.edit().putStringSet(PREF_PINNED_PACKAGES, pinned).apply()
    }

    fun unpinPackage(packageName: String) {
        val pinned = pinnedPackages().toMutableSet()
        pinned.remove(packageName)
        preferences.edit().putStringSet(PREF_PINNED_PACKAGES, pinned).apply()
    }

    fun addNotificationFilter(filter: NotificationFilter) {
        val filters = preferences.getStringSet(PREF_NOTIFICATION_FILTERS, emptySet())
            .orEmpty()
            .toMutableSet()
        filters.add(filter.encode())
        preferences.edit().putStringSet(PREF_NOTIFICATION_FILTERS, filters).apply()
    }

    fun cachedAppHue(packageName: String): Int {
        return preferences.getInt(PREF_APP_HUE_PREFIX + packageName, 0)
    }

    fun cacheAppHue(packageName: String, hueColor: Int) {
        if (hueColor == 0) return
        preferences.edit().putInt(PREF_APP_HUE_PREFIX + packageName, hueColor).apply()
    }

    fun cachedLaunchableAppsSnapshot(): AppLibrarySnapshot? {
        return preferences.getString(PREF_LAUNCHABLE_APPS_SNAPSHOT, null)
            ?.let(AppLibrarySnapshotCodec::decode)
    }

    fun cacheLaunchableAppsSnapshot(snapshot: AppLibrarySnapshot) {
        preferences.edit()
            .putString(PREF_LAUNCHABLE_APPS_SNAPSHOT, AppLibrarySnapshotCodec.encode(snapshot))
            .apply()
    }

    fun clearLaunchableAppsSnapshot() {
        preferences.edit().remove(PREF_LAUNCHABLE_APPS_SNAPSHOT).apply()
    }

    fun excludedSources(labelResolver: (String) -> String): List<ExcludedSource> {
        return excludedPackages()
            .map { sourceKey -> ExcludedSource(sourceKey, labelResolver(sourceKey)) }
            .sortedWith { left, right -> Collator.getInstance().compare(left.label, right.label) }
    }

    fun excludedLabel(packageName: String, fallback: () -> String): String {
        val savedLabel = preferences.getString(PREF_EXCLUDED_LABEL_PREFIX + packageName, null)
        if (!savedLabel.isNullOrBlank()) {
            return savedLabel
        }
        return fallback()
    }

    fun exclude(chapter: AppChapter) {
        val packages = excludedPackages().toMutableSet()
        packages.add(chapter.identityKey)
        preferences.edit()
            .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
            .putString(PREF_EXCLUDED_LABEL_PREFIX + chapter.identityKey, chapter.label)
            .apply()
    }

    fun restore(packageName: String) {
        val packages = excludedPackages().toMutableSet()
        packages.remove(packageName)
        preferences.edit()
            .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
            .remove(PREF_EXCLUDED_LABEL_PREFIX + packageName)
            .apply()
    }

    fun useTintedNotificationCards(): Boolean {
        return preferences.getBoolean(PREF_TINT_NOTIFICATION_CARDS, false)
    }

    fun uiPreferences(): LauncherUiPreferences {
        return LauncherUiPreferences(
            useTintedNotificationCards = useTintedNotificationCards(),
            useCardIconBackgrounds = useCardIconBackgrounds(),
            cardCornerRadiusDp = cardCornerRadiusDp(),
            cardIconBlur = cardIconBlur(),
            focusBlurRadius = focusBlurRadius(),
            splitAppsByProfile = splitAppsByProfile(),
            placeWorkNotificationChaptersBeforeApps = placeWorkNotificationChaptersBeforeApps(),
            cardHapticsEnabled = cardHapticsEnabled(),
            cardHapticStrength = cardHapticStrength(),
            cardStackTuning = cardStackTuning(),
            showAdvancedStackControls = showAdvancedStackControls(),
            cardVibrancy = cardVibrancy(),
        )
    }

    fun launcherChangeToken(): Int {
        return listOf(
            uiPreferences(),
            excludedPackages(),
            notificationFilters().toSet(),
            pinnedPackages(),
            hiddenAppKeys(),
            splitNotificationPackages(),
        ).hashCode()
    }

    fun useCardIconBackgrounds(): Boolean {
        return preferences.getBoolean(PREF_CARD_ICON_BACKGROUNDS, true)
    }

    fun toggleCardIconBackgrounds(): Boolean {
        val nextValue = !useCardIconBackgrounds()
        preferences.edit().putBoolean(PREF_CARD_ICON_BACKGROUNDS, nextValue).apply()
        return nextValue
    }

    fun cardCornerRadiusDp(): Int {
        return preferences.getInt(PREF_CARD_CORNER_RADIUS, 18).coerceIn(0, 36)
    }

    fun setCardCornerRadiusDp(radius: Int) {
        preferences.edit().putInt(PREF_CARD_CORNER_RADIUS, radius.coerceIn(0, 36)).apply()
    }

    fun cardIconBlur(): Int {
        return preferences.getInt(PREF_CARD_ICON_BLUR, 0).coerceIn(0, 100)
    }

    fun setCardIconBlur(blur: Int) {
        preferences.edit().putInt(PREF_CARD_ICON_BLUR, blur.coerceIn(0, 100)).apply()
    }

    fun focusBlurRadius(): Int {
        return preferences.getInt(PREF_FOCUS_BLUR_RADIUS, 7).coerceIn(0, 24)
    }

    fun setFocusBlurRadius(radius: Int) {
        preferences.edit().putInt(PREF_FOCUS_BLUR_RADIUS, radius.coerceIn(0, 24)).apply()
    }

    fun splitAppsByProfile(): Boolean {
        return preferences.getBoolean(PREF_SPLIT_APPS_BY_PROFILE, false)
    }

    fun toggleSplitAppsByProfile(): Boolean {
        val nextValue = !splitAppsByProfile()
        preferences.edit().putBoolean(PREF_SPLIT_APPS_BY_PROFILE, nextValue).apply()
        return nextValue
    }

    fun placeWorkNotificationChaptersBeforeApps(): Boolean {
        return preferences.getBoolean(PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS, false)
    }

    fun toggleWorkNotificationChaptersBeforeApps(): Boolean {
        val nextValue = !placeWorkNotificationChaptersBeforeApps()
        preferences.edit().putBoolean(PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS, nextValue).apply()
        return nextValue
    }

    fun toggleNotificationSurface(): Boolean {
        val nextValue = !useTintedNotificationCards()
        preferences.edit().putBoolean(PREF_TINT_NOTIFICATION_CARDS, nextValue).apply()
        return nextValue
    }

    fun cardHapticsEnabled(): Boolean {
        return preferences.getBoolean(PREF_CARD_HAPTICS_ENABLED, true)
    }

    fun cardHapticStrength(): Int {
        return preferences.getInt(PREF_CARD_HAPTICS_STRENGTH, 2).coerceIn(1, 5)
    }

    fun setCardHapticStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_HAPTICS_STRENGTH, strength.coerceIn(1, 5)).apply()
    }

    fun toggleCardHaptics(): Boolean {
        val nextValue = !cardHapticsEnabled()
        preferences.edit().putBoolean(PREF_CARD_HAPTICS_ENABLED, nextValue).apply()
        return nextValue
    }

    fun cardStackTuning(): CardStackTuning {
        return CardStackTuning(
            curve = preferences.getInt(PREF_CARD_STACK_CURVE, 50).coerceIn(0, 100),
            horizontalCurve = preferences.getInt(PREF_CARD_STACK_HORIZONTAL_CURVE, 0).coerceIn(-100, 100),
            arcWidth = preferences.getInt(PREF_CARD_STACK_ARC_WIDTH, 50).coerceIn(0, 100),
            aboveFocusCards = preferences.getInt(PREF_CARD_STACK_ABOVE_FOCUS, 2).coerceIn(0, 4),
            rotation = preferences.getInt(PREF_CARD_STACK_ROTATION, 0).coerceIn(0, 100),
            verticalSpacing = preferences.getInt(PREF_CARD_STACK_SPACING, 50).coerceIn(0, 100),
            visibleCards = preferences.getInt(PREF_CARD_STACK_VISIBLE, 3).coerceIn(1, 5),
            focusedCardGap = preferences.getInt(PREF_CARD_STACK_FOCUSED_GAP, 36).coerceIn(0, 100),
            focusedCardScale = preferences.getInt(PREF_CARD_STACK_FOCUSED_SCALE, 32).coerceIn(0, 100),
            magnetStrength = preferences.getInt(PREF_CARD_STACK_MAGNET_STRENGTH, 70).coerceIn(0, 100),
            stackPeakPosition = preferences.getInt(PREF_CARD_STACK_PEAK_POSITION, 20).coerceIn(0, 100),
        )
    }

    fun setCardStackCurve(curve: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_CURVE, curve.coerceIn(0, 100)).apply()
    }

    fun setCardStackHorizontalCurve(curve: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_HORIZONTAL_CURVE, curve.coerceIn(-100, 100)).apply()
    }

    fun setCardStackArcWidth(width: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ARC_WIDTH, width.coerceIn(0, 100)).apply()
    }

    fun setAboveFocusCardCount(count: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ABOVE_FOCUS, count.coerceIn(0, 4)).apply()
    }

    fun setCardStackRotation(rotation: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ROTATION, rotation.coerceIn(0, 100)).apply()
    }

    fun setCardStackSpacing(spacing: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_SPACING, spacing.coerceIn(0, 100)).apply()
    }

    fun setVisibleCardCount(count: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_VISIBLE, count.coerceIn(1, 5)).apply()
    }

    fun setFocusedCardGap(gap: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_FOCUSED_GAP, gap.coerceIn(0, 100)).apply()
    }

    fun setFocusedCardScale(scale: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_FOCUSED_SCALE, scale.coerceIn(0, 100)).apply()
    }

    fun setMagnetStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_MAGNET_STRENGTH, strength.coerceIn(0, 100)).apply()
    }

    fun setStackPeakPosition(position: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_PEAK_POSITION, position.coerceIn(0, 100)).apply()
    }

    fun cardVibrancy(): Int {
        return preferences.getInt(PREF_CARD_VIBRANCY, 50).coerceIn(0, 100)
    }

    fun setCardVibrancy(vibrancy: Int) {
        preferences.edit().putInt(PREF_CARD_VIBRANCY, vibrancy.coerceIn(0, 100)).apply()
    }

    fun showAdvancedStackControls(): Boolean {
        return preferences.getBoolean(PREF_CARD_STACK_ADVANCED, false)
    }

    fun toggleAdvancedStackControls(): Boolean {
        val nextValue = !showAdvancedStackControls()
        preferences.edit().putBoolean(PREF_CARD_STACK_ADVANCED, nextValue).apply()
        return nextValue
    }

    fun applyTimescapeStackPreset() {
        preferences.edit()
            .putInt(PREF_CARD_STACK_HORIZONTAL_CURVE, 86)
            .putInt(PREF_CARD_STACK_ARC_WIDTH, 78)
            .putInt(PREF_CARD_STACK_ABOVE_FOCUS, 3)
            .putInt(PREF_CARD_STACK_ROTATION, 24)
            .putInt(PREF_CARD_STACK_CURVE, 76)
            .putInt(PREF_CARD_STACK_SPACING, 42)
            .putInt(PREF_CARD_STACK_VISIBLE, 4)
            .putInt(PREF_CARD_STACK_FOCUSED_GAP, 54)
            .putInt(PREF_CARD_STACK_FOCUSED_SCALE, 42)
            .putInt(PREF_CARD_STACK_MAGNET_STRENGTH, 82)
            .apply()
    }

    fun groupNotifications(packageName: String): Boolean {
        return packageName !in splitNotificationPackages()
    }

    fun toggleNotificationGrouping(packageName: String): Boolean {
        val splitPackages = splitNotificationPackages().toMutableSet()
        val nextGrouped = if (packageName in splitPackages) {
            splitPackages.remove(packageName)
            true
        } else {
            splitPackages.add(packageName)
            false
        }
        preferences.edit().putStringSet(PREF_SPLIT_NOTIFICATION_PACKAGES, splitPackages).apply()
        return nextGrouped
    }

    private fun splitNotificationPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_SPLIT_NOTIFICATION_PACKAGES, emptySet()) ?: emptySet())
    }

    companion object {
        private const val PREFS_NAME = "calm_preferences"
        private const val PREF_EXCLUDED_PACKAGES = "excluded_notification_packages"
        private const val PREF_NOTIFICATION_FILTERS = "notification_filters"
        private const val PREF_PINNED_PACKAGES = "pinned_packages"
        private const val PREF_HIDDEN_APP_KEYS = "hidden_app_keys"
        private const val PREF_APP_HUE_PREFIX = "app_hue_"
        private const val PREF_LAUNCHABLE_APPS_SNAPSHOT = "launchable_apps_snapshot"
        private const val PREF_EXCLUDED_LABEL_PREFIX = "excluded_label_"
        private const val PREF_TINT_NOTIFICATION_CARDS = "tint_notification_cards"
        private const val PREF_CARD_ICON_BACKGROUNDS = "card_icon_backgrounds"
        private const val PREF_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val PREF_CARD_ICON_BLUR = "card_icon_blur"
        private const val PREF_FOCUS_BLUR_RADIUS = "focus_blur_radius"
        private const val PREF_SPLIT_APPS_BY_PROFILE = "split_apps_by_profile"
        private const val PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS = "work_notification_chapters_before_apps"
        private const val PREF_CARD_HAPTICS_ENABLED = "card_haptics_enabled"
        private const val PREF_CARD_HAPTICS_STRENGTH = "card_haptics_strength"
        private const val PREF_SPLIT_NOTIFICATION_PACKAGES = "split_notification_packages"
        private const val PREF_CARD_STACK_CURVE = "card_stack_curve"
        private const val PREF_CARD_STACK_HORIZONTAL_CURVE = "card_stack_horizontal_curve"
        private const val PREF_CARD_STACK_ARC_WIDTH = "card_stack_arc_width"
        private const val PREF_CARD_STACK_ABOVE_FOCUS = "card_stack_above_focus"
        private const val PREF_CARD_STACK_ROTATION = "card_stack_rotation"
        private const val PREF_CARD_STACK_SPACING = "card_stack_spacing"
        private const val PREF_CARD_STACK_VISIBLE = "card_stack_visible"
        private const val PREF_CARD_STACK_FOCUSED_GAP = "card_stack_focused_gap"
        private const val PREF_CARD_STACK_FOCUSED_SCALE = "card_stack_focused_scale"
        private const val PREF_CARD_STACK_MAGNET_STRENGTH = "card_stack_magnet_strength"
        private const val PREF_CARD_STACK_ADVANCED = "card_stack_advanced"
        private const val PREF_CARD_STACK_PEAK_POSITION = "card_stack_peak_position"
        private const val PREF_CARD_VIBRANCY = "card_vibrancy"
    }
}
