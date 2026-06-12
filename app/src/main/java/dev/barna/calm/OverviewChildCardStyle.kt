package dev.barna.calm

import android.graphics.Color

object OverviewChildCardStyle {
    fun hueColor(chapterHue: Int, item: NotificationCardItem): Int {
        val base = Color.HSVToColor(floatArrayOf(stableHue(item), 0.42f, 0.72f))
        if (chapterHue == 0) return base
        return CalmColor.blend(chapterHue, base, 0.58f)
    }

    private fun stableHue(item: NotificationCardItem): Float {
        val raw = item.notifications.joinToString("|") { notification ->
            listOf(
                notification.key,
                notification.conversationTitle,
                notification.title,
                notification.bodyText(),
            ).joinToString(":")
        }.hashCode()
        return ((raw and Int.MAX_VALUE) % 360).toFloat()
    }
}
