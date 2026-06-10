package dev.barna.calm

class ResumeRefreshPolicy {
    fun shouldRefreshImmediately(
        hasCurrentScreen: Boolean,
        hasCurrentState: Boolean,
        launcherSettingsChanged: Boolean,
        notificationsChanged: Boolean,
    ): Boolean {
        return launcherSettingsChanged || notificationsChanged || !hasCurrentScreen || !hasCurrentState
    }
}
