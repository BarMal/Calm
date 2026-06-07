package dev.barna.calm

object NotificationFilterPattern {
    private val digitRun = Regex("\\d+")
    private val whitespace = Regex("\\s+")

    fun generalizeNumbers(text: String): String? {
        val normalized = text.trim().replace(whitespace, " ")
        if (normalized.isBlank() || !digitRun.containsMatchIn(normalized)) return null
        return digitRun.replace(normalized, "{?}")
    }
}
