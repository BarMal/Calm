package dev.barna.calm

import android.widget.Toast

class LauncherAppMutationHandler(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
    private val loadPinnedApps: () -> List<AppEntry>,
) {
    fun pinApp(app: AppEntry) {
        settings.pinPackage(app.identityKey)
        selectPage(CalmTheme.PINNED_KEY)
        Toast.makeText(activity, "Pinned ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    fun unpinApp(app: AppEntry) {
        settings.unpinPackage(app.identityKey)
        settings.unpinPackage(app.packageName)
        if (loadPinnedApps().isEmpty()) {
            selectPage(CalmTheme.APP_LIBRARY_KEY)
        }
        Toast.makeText(activity, "Unpinned ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    fun hideApp(app: AppEntry) {
        settings.hideApp(app.identityKey, app.label)
        Toast.makeText(activity, "Hidden ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    fun showApp(appKey: String) {
        settings.showApp(appKey)
        render()
    }
}
