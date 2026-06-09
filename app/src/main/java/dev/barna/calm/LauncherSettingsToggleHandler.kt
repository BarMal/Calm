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
        Toast.makeText(activity, if (nextValue) "Notification cards are tinted" else "Chapter panels are tinted", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(activity, if (nextValue) "Apps split by profile" else "Apps combined", Toast.LENGTH_SHORT).show()
        render()
    }

    fun toggleWorkNotificationChapterPlacement() {
        val nextValue = settings.toggleWorkNotificationChaptersBeforeApps()
        Toast.makeText(activity, if (nextValue) "Work notification chapters moved left" else "Work notification chapters moved right", Toast.LENGTH_SHORT).show()
        render()
    }

    fun toggleNotificationGrouping(chapter: AppChapter) {
        val nextGrouped = settings.toggleNotificationGrouping(chapter.identityKey)
        Toast.makeText(activity, if (nextGrouped) "Notifications grouped" else "Notifications split", Toast.LENGTH_SHORT).show()
        render()
    }

    fun toggleCardHaptics() {
        val nextValue = settings.toggleCardHaptics()
        if (nextValue) performCardScrollHaptic(activity.window.decorView)
        Toast.makeText(activity, if (nextValue) "Card haptics on" else "Card haptics off", Toast.LENGTH_SHORT).show()
        render()
    }

    fun applyTimescapeStackPreset() {
        settings.applyTimescapeStackPreset()
        Toast.makeText(activity, "Timescape stack preset applied", Toast.LENGTH_SHORT).show()
        render()
    }

    fun toggleAdvancedStackControls() {
        val nextValue = settings.toggleAdvancedStackControls()
        Toast.makeText(activity, if (nextValue) "Advanced stack controls shown" else "Advanced stack controls hidden", Toast.LENGTH_SHORT).show()
        render()
    }
}
