package dev.barna.calm

import android.os.UserHandle

data class AppShortcutEntry(
    val label: String,
    val id: String,
    val packageName: String,
    val userHandle: UserHandle,
)
