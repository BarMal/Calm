package dev.barna.calm

import android.content.ComponentName
import android.os.UserHandle

class AppChapter(
    @JvmField val packageName: String,
    @JvmField val label: String,
    @JvmField val notifications: List<CalmNotificationListenerService.CalmNotification>,
    @JvmField val launchable: Boolean,
    @JvmField val hueColor: Int,
    @JvmField val identityKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
    @JvmField val launcherIdentityKey: String = AppIdentity.packageOnly(packageName).key,
    @JvmField val componentName: ComponentName? = null,
    @JvmField val userHandle: UserHandle? = null,
    @JvmField val profileLabel: String = "",
    @JvmField val isWorkProfile: Boolean = false,
)
