package dev.barna.calm

import android.view.View
import android.widget.Toast

class LauncherSettingsToggleHandler(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
    private val currentPageKey: () -> String,
    private val performCardScrollHaptic: (View) -> Unit,
) {
    fun toggleNotificationSurface() {
        val nextValue = settings.toggleNotificationSurface()
        toast(if (nextValue) R.string.toast_notification_cards_tinted else R.string.toast_chapter_panels_tinted)
        render()
    }

    fun toggleSplitAppsByProfile() {
        val nextValue = settings.toggleSplitAppsByProfile()
        val currentKey = currentPageKey()
        if (currentKey == CalmTheme.APP_LIBRARY_KEY ||
            currentKey == CalmTheme.PERSONAL_APP_LIBRARY_KEY ||
            currentKey == CalmTheme.WORK_APP_LIBRARY_KEY
        ) {
            selectPage(if (nextValue) CalmTheme.PERSONAL_APP_LIBRARY_KEY else CalmTheme.APP_LIBRARY_KEY)
        }
        toast(if (nextValue) R.string.toast_apps_split_by_profile else R.string.toast_apps_combined)
        render()
    }

    fun toggleWorkNotificationChapterPlacement() {
        val nextValue = settings.toggleWorkNotificationChaptersBeforeApps()
        toast(if (nextValue) R.string.toast_work_notifications_left else R.string.toast_work_notifications_right)
        render()
    }

    fun toggleNotificationGrouping(chapter: AppChapter) {
        val nextGrouped = settings.toggleNotificationGrouping(chapter.identityKey)
        toast(if (nextGrouped) R.string.toast_notifications_grouped else R.string.toast_notifications_split)
        render()
    }

    fun toggleCardHaptics() {
        val nextValue = settings.toggleCardHaptics()
        if (nextValue) performCardScrollHaptic(activity.window.decorView)
        toast(if (nextValue) R.string.toast_card_haptics_on else R.string.toast_card_haptics_off)
        render()
    }

    fun applyTimescapeStackPreset() {
        settings.applyTimescapeStackPreset()
        toast(R.string.toast_timescape_preset_applied)
        render()
    }

    fun toggleAdvancedStackControls() {
        val nextValue = settings.toggleAdvancedStackControls()
        toast(if (nextValue) R.string.toast_advanced_stack_controls_shown else R.string.toast_advanced_stack_controls_hidden)
        render()
    }

    private fun toast(resId: Int) {
        Toast.makeText(activity, activity.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
