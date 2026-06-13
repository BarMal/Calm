package dev.barna.calm

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes

@Suppress("DEPRECATION")
internal object CalmThemeColor {
    fun notificationBadge(context: Context): Int {
        return resolveResource(context, R.attr.calmNotificationBadgeColor, R.color.calm_notification_badge)
    }

    fun onNotificationBadge(context: Context): Int {
        return resolveResource(context, R.attr.calmOnNotificationBadgeColor, R.color.calm_on_notification_badge)
    }

    fun pageOverviewScrim(context: Context): Int {
        return resolveResource(context, R.attr.calmPageOverviewScrimColor, R.color.calm_page_overview_scrim)
    }

    fun pageOverviewBadgeScrim(context: Context): Int {
        return resolveResource(context, R.attr.calmPageOverviewBadgeScrimColor, R.color.calm_page_overview_badge_scrim)
    }

    fun onPageOverviewScrim(context: Context): Int {
        return resolveResource(context, R.attr.calmOnPageOverviewScrimColor, R.color.calm_on_page_overview_scrim)
    }

    internal fun resolve(context: Context, @AttrRes attr: Int, fallback: Int): Int {
        val colors = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            if (colors.hasValue(0)) colors.getColor(0, fallback) else fallback
        } finally {
            colors.recycle()
        }
    }

    private fun resolveResource(context: Context, @AttrRes attr: Int, @ColorRes fallbackRes: Int): Int {
        val colors = context.theme.obtainStyledAttributes(intArrayOf(attr))
        return try {
            if (colors.hasValue(0)) {
                colors.getColor(0, 0)
            } else {
                runCatching { context.resources.getColor(fallbackRes) }.getOrDefault(0)
            }
        } finally {
            colors.recycle()
        }
    }
}
