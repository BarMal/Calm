package dev.barna.calm

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.Collator
import java.util.concurrent.Executor

class ClassicWidgetHostController(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val requestWidgetBind: (Intent) -> Unit,
    private val requestWidgetConfigure: (Intent) -> Unit,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
    private val beginClassicItemPlacement: (ClassicLauncherPageDefinition, String) -> Unit,
    private val widgetIpcRunner: WidgetHostIpcRunner = WidgetHostIpcRunner(
        callbackExecutor = Executor { command -> activity.runOnUiThread(command) },
        logFailure = { message, throwable -> Log.w(TAG, message, throwable) },
    ),
) {
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val appWidgetHost = AppWidgetHost(activity, HOST_ID)
    private var pendingRequest: PendingWidgetRequest? = null

    fun startListening() {
        widgetIpcRunner.run("Widget host startListening failed") {
            appWidgetHost.startListening()
        }
    }

    fun stopListening() {
        widgetIpcRunner.run("Widget host stopListening failed") {
            appWidgetHost.stopListening()
        }
    }

    fun shutdown() {
        widgetIpcRunner.shutdown()
    }

    fun requestAddWidget(page: ClassicLauncherPageDefinition) {
        val providers = appWidgetManager.installedProviders
            .sortedWith { left, right -> Collator.getInstance().compare(widgetLabel(left), widgetLabel(right)) }
        if (providers.isEmpty()) {
            Toast.makeText(activity, "No widgets available", Toast.LENGTH_SHORT).show()
            return
        }
        showWidgetPicker(page, providers)
    }

    fun onWidgetBindResult(resultCode: Int, data: Intent?) {
        val request = pendingRequest ?: return
        val appWidgetId = data.appWidgetIdOr(request.appWidgetId)
        if (resultCode == Activity.RESULT_OK) {
            continueWidgetSetup(appWidgetId)
        } else {
            deletePending(appWidgetId)
        }
    }

    private fun showWidgetPicker(
        page: ClassicLauncherPageDefinition,
        providers: List<AppWidgetProviderInfo>,
    ) {
        val grid = GridLayout(activity).apply {
            columnCount = WIDGET_PICKER_COLUMNS
            useDefaultMargins = false
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
        }
        var dialog: AlertDialog? = null
        providers.forEachIndexed { index, provider ->
            grid.addView(
                widgetProviderCard(provider) {
                    dialog?.dismiss()
                    beginAddWidget(page, provider)
                },
                GridLayout.LayoutParams(
                    GridLayout.spec(index / WIDGET_PICKER_COLUMNS),
                    GridLayout.spec(index % WIDGET_PICKER_COLUMNS, GridLayout.FILL),
                ).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(index % WIDGET_PICKER_COLUMNS, 1f)
                    setMargins(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
                },
            )
        }
        val scroll = ScrollView(activity).apply {
            addView(grid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog = GoogleInteractionStyle.dialogBuilder(activity)
            .setTitle("Add widget")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun widgetProviderCard(provider: AppWidgetProviderInfo, onClick: () -> Unit): View {
        val label = widgetLabel(provider)
        val span = ClassicWidgetSpanCalculator.spanFor(provider.minWidth, provider.minHeight, settings.classicGridConfig())
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GoogleInteractionStyle.rowBackground(activity, 18)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            addView(
                FrameLayout(activity).apply {
                    foregroundGravity = Gravity.CENTER
                    val image = ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setImageDrawable(widgetPreview(provider))
                    }
                    addView(
                        image,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(96)),
            )
            addView(
                TextView(activity).apply {
                    text = label
                    setTextColor(GoogleInteractionStyle.onSurface(activity))
                    textSize = 13f
                    typeface = Typeface.DEFAULT
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    includeFontPadding = false
                    setPadding(0, activity.dp(8), 0, 0)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                TextView(activity).apply {
                    text = "${span.first}x${span.second}"
                    setTextColor(GoogleInteractionStyle.onSurfaceVariant(activity))
                    textSize = 11f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(0, activity.dp(5), 0, 0)
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            minimumHeight = activity.dp(168)
            setOnClickListener { onClick() }
        }
    }

    private fun beginAddWidget(page: ClassicLauncherPageDefinition, provider: AppWidgetProviderInfo) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingRequest = PendingWidgetRequest(page.id, appWidgetId)
        widgetIpcRunner.call(
            message = "bindAppWidgetIdIfAllowed failed for ${provider.provider}",
            defaultValue = false,
            action = { appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider) },
        ) { bound ->
            if (pendingRequest?.appWidgetId != appWidgetId) return@call
            if (bound) {
                continueWidgetSetup(appWidgetId)
                return@call
            }
            launchOrDelete(appWidgetId, "Widget permission unavailable") {
                requestWidgetBind(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    },
                )
            }
        }
    }

    private fun continueWidgetSetup(appWidgetId: Int) {
        pendingRequest = pendingRequest?.copy(appWidgetId = appWidgetId)
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

    fun onWidgetConfigureResult(resultCode: Int, data: Intent?): Boolean {
        val request = pendingRequest ?: return false
        val appWidgetId = data.appWidgetIdOr(request.appWidgetId)
        if (resultCode == Activity.RESULT_OK) {
            finishWidget(appWidgetId)
        } else {
            deletePending(appWidgetId)
        }
        return true
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

    fun canConfigureWidget(item: ClassicGridItem): Boolean {
        val appWidgetId = item.target.toIntOrNull() ?: return false
        return appWidgetManager.getAppWidgetInfo(appWidgetId)?.configure != null
    }

    fun defaultWidgetSpan(appWidgetId: Int): Pair<Int, Int> {
        return appWidgetManager.getAppWidgetInfo(appWidgetId)
            ?.let { info -> ClassicWidgetSpanCalculator.spanFor(info.minWidth, info.minHeight, settings.classicGridConfig()) }
            ?: (settings.classicGridConfig().columns to 2)
    }

    fun requestConfigureWidget(item: ClassicGridItem) {
        val appWidgetId = item.target.toIntOrNull() ?: return
        val configure = appWidgetManager.getAppWidgetInfo(appWidgetId)?.configure
        if (configure == null) {
            Toast.makeText(activity, "Widget has no settings", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        launchOrToast("Widget settings unavailable") {
            requestWidgetConfigure(intent)
        }
    }

    fun deleteWidget(item: ClassicGridItem) {
        val appWidgetId = item.target.toIntOrNull() ?: return
        appWidgetHost.deleteAppWidgetId(appWidgetId)
    }

    private fun finishWidget(appWidgetId: Int) {
        val request = pendingRequest ?: return
        pendingRequest = null
        val span = appWidgetManager.getAppWidgetInfo(appWidgetId)
            ?.let { info -> ClassicWidgetSpanCalculator.spanFor(info.minWidth, info.minHeight, settings.classicGridConfig()) }
            ?: (settings.classicGridConfig().columns to 2)
        if (settings.addWidgetToClassicPage(request.pageId, appWidgetId, span.first, span.second)) {
            settings.classicPages().firstOrNull { it.id == request.pageId }?.let { page ->
                selectPage(page.key)
                beginClassicItemPlacement(page, "widget:$appWidgetId")
            }
            Toast.makeText(activity, "Added widget; drag to place", Toast.LENGTH_SHORT).show()
            render()
        } else {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            Toast.makeText(activity, "Classic page is full", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchOrToast(failureMessage: String, launch: () -> Unit) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, failureMessage, Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            Toast.makeText(activity, failureMessage, Toast.LENGTH_SHORT).show()
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

    private fun widgetLabel(provider: AppWidgetProviderInfo): String {
        return provider.loadLabel(activity.packageManager).takeIf { it.isNotBlank() }
            ?: provider.provider.shortName()
    }

    private fun widgetPreview(provider: AppWidgetProviderInfo): android.graphics.drawable.Drawable? {
        val density = activity.resources.displayMetrics.densityDpi
        return provider.loadPreviewImage(activity, density)
            ?: provider.loadIcon(activity, density)
    }

    private fun ComponentName.shortName(): String {
        return className.substringAfterLast('.').ifBlank { packageName }
    }

    private data class PendingWidgetRequest(
        val pageId: String,
        val appWidgetId: Int,
    )

    companion object {
        const val HOST_ID = 1017
        private const val WIDGET_PICKER_COLUMNS = 2
        private const val TAG = "ClassicWidgetHost"
    }
}
