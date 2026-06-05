package dev.barna.calm

class AppChapter(
    @JvmField val packageName: String,
    @JvmField val label: String,
    @JvmField val notifications: List<CalmNotificationListenerService.CalmNotification>,
    @JvmField val launchable: Boolean,
    @JvmField val hueColor: Int,
)
