package dev.barna.calm

data class MediaNotificationControls(
    val previous: NotificationAction?,
    val playPause: NotificationAction?,
    val next: NotificationAction?,
) {
    val hasAnyAction: Boolean = previous != null || playPause != null || next != null
    val playPauseLabel: String
        get() = playPause?.label?.takeUnless { it.isBlank() } ?: "Play / Pause"

    companion object {
        fun from(notifications: List<CalmNotificationListenerService.CalmNotification>): MediaNotificationControls {
            val actions = notifications.flatMap { it.actions }
            return MediaNotificationControls(
                previous = actions.firstOrNull { it.isPreviousAction() },
                playPause = actions.firstOrNull { it.isPlaybackToggleAction() },
                next = actions.firstOrNull { it.isNextAction() },
            )
        }

        private fun NotificationAction.isPreviousAction(): Boolean {
            val normalized = label.normalizedActionLabel()
            return normalized == "previous" ||
                normalized == "prev" ||
                normalized == "rewind" ||
                normalized.contains("skip back") ||
                normalized.contains("previous track")
        }

        private fun NotificationAction.isPlaybackToggleAction(): Boolean {
            val normalized = label.normalizedActionLabel()
            return normalized == "play" ||
                normalized == "pause" ||
                normalized == "resume" ||
                normalized == "play pause" ||
                normalized == "play/pause" ||
                normalized.contains("play") ||
                normalized.contains("pause")
        }

        private fun NotificationAction.isNextAction(): Boolean {
            val normalized = label.normalizedActionLabel()
            return normalized == "next" ||
                normalized.contains("skip forward") ||
                normalized.contains("next track")
        }

        private fun String.normalizedActionLabel(): String {
            return trim()
                .lowercase()
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("\\s+"), " ")
        }
    }
}
