package dev.barna.calm

import android.content.ComponentName
import android.os.UserHandle

data class AppEntry(
    val packageName: String,
    val label: String,
    val hueColor: Int,
    val identityKey: String = AppIdentity.packageOnly(packageName).key,
    val notificationSourceKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
    val componentName: ComponentName? = null,
    val userHandle: UserHandle? = null,
    val profileLabel: String = "",
    val isWorkProfile: Boolean = false,
)
