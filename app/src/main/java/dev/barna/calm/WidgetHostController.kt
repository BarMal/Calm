package dev.barna.calm

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent

/**
 * Wraps an [AppWidgetHost] so the launcher can host third-party home-screen widgets. Allocation +
 * binding go through the system widget picker (ACTION_APPWIDGET_PICK), which handles the bind
 * permission for us, so no BIND_APPWIDGET manifest permission is required.
 */
class WidgetHostController(private val activity: MainActivity) {
    private val host = AppWidgetHost(activity, HOST_ID)
    private val widgetManager = AppWidgetManager.getInstance(activity)
    private var listening = false

    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }
        listening = true
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun allocateWidgetId(): Int = host.allocateAppWidgetId()

    fun deleteWidgetId(widgetId: Int) {
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    fun info(widgetId: Int): AppWidgetProviderInfo? = runCatching { widgetManager.getAppWidgetInfo(widgetId) }.getOrNull()

    /** Builds an Intent for the system widget picker, pre-seeded with [widgetId]. */
    fun pickIntent(widgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
    }

    /** Hosts the bound widget [widgetId] in a view, or null if its provider can't be resolved. */
    fun createView(widgetId: Int): AppWidgetHostView? {
        val info = info(widgetId) ?: return null
        return runCatching { host.createView(activity, widgetId, info) }.getOrNull()
    }

    companion object {
        // Stable host id for this launcher.
        const val HOST_ID = 0x43414C4D
    }
}
