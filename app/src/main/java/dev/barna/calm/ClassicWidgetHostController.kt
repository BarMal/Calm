package dev.barna.calm

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.widget.Toast

class ClassicWidgetHostController(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val requestWidgetPick: (Intent) -> Unit,
    private val requestWidgetConfigure: (Intent) -> Unit,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, HOST_ID)
    private var pendingRequest: PendingWidgetRequest? = null

    fun startListening() {
        runCatching { appWidgetHost.startListening() }
    }

    fun stopListening() {
        runCatching { appWidgetHost.stopListening() }
    }

    fun requestAddWidget(page: ClassicLauncherPageDefinition) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingRequest = PendingWidgetRequest(page.id, appWidgetId)
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        launchOrDelete(appWidgetId, "No widget picker available") {
            requestWidgetPick(intent)
        }
    }

    fun onWidgetPickResult(resultCode: Int, data: Intent?) {
        val request = pendingRequest ?: return
        val appWidgetId = data.appWidgetIdOr(request.appWidgetId)
        if (resultCode != Activity.RESULT_OK) {
            deletePending(appWidgetId)
            return
        }
        pendingRequest = request.copy(appWidgetId = appWidgetId)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.configure == null) {
            finishWidget(appWidgetId)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        launchOrDelete(appWidgetId, "Widget setup unavailable") {
            requestWidgetConfigure(intent)
        }
    }

    fun onWidgetConfigureResult(resultCode: Int, data: Intent?) {
        val request = pendingRequest ?: return
        val appWidgetId = data.appWidgetIdOr(request.appWidgetId)
        if (resultCode == Activity.RESULT_OK) {
            finishWidget(appWidgetId)
        } else {
            deletePending(appWidgetId)
        }
    }

    fun createWidgetView(item: ClassicGridItem): View? {
        val appWidgetId = item.target.toIntOrNull() ?: return null
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching {
            appWidgetHost.createView(activity, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
            }
        }.getOrNull()
    }

    private fun finishWidget(appWidgetId: Int) {
        val request = pendingRequest ?: return
        pendingRequest = null
        if (settings.addWidgetToClassicPage(request.pageId, appWidgetId)) {
            settings.classicPages().firstOrNull { it.id == request.pageId }?.let { page -> selectPage(page.key) }
            Toast.makeText(activity, "Added widget", Toast.LENGTH_SHORT).show()
            render()
        } else {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(activity, "Classic page is full", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchOrDelete(appWidgetId: Int, failureMessage: String, launch: () -> Unit) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            deletePending(appWidgetId)
            Toast.makeText(activity, failureMessage, Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            deletePending(appWidgetId)
            Toast.makeText(activity, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deletePending(appWidgetId: Int) {
        pendingRequest = null
        appWidgetHost.deleteAppWidgetId(appWidgetId)
    }

    private fun Intent?.appWidgetIdOr(fallback: Int): Int {
        return this?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, fallback) ?: fallback
    }

    private data class PendingWidgetRequest(
        val pageId: String,
        val appWidgetId: Int,
    )

    private companion object {
        const val HOST_ID = 1017
    }
}
