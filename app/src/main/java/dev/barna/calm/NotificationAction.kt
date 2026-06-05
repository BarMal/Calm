package dev.barna.calm

import android.app.PendingIntent
import android.app.RemoteInput

data class NotificationAction(
    val label: String,
    val intent: PendingIntent?,
    val remoteInputs: List<RemoteInput>,
) {
    val requiresInput: Boolean = remoteInputs.isNotEmpty()
}
