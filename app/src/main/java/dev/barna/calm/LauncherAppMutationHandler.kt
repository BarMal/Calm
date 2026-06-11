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

    fun isDockItem(identityKey: String): Boolean {
        return identityKey in settings.dockKeys()
    }

    fun addDockItem(identityKey: String, label: String) {
        if (isDockItem(identityKey)) {
            Toast.makeText(activity, "$label is already in the dock", Toast.LENGTH_SHORT).show()
            return
        }
        if (settings.addDockKey(identityKey)) {
            settings.setDockEnabled(true)
            Toast.makeText(activity, "Added $label to dock", Toast.LENGTH_SHORT).show()
            render()
        } else {
            Toast.makeText(activity, "Dock is full", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeDockItem(identityKey: String, label: String) {
        if (!isDockItem(identityKey)) {
            Toast.makeText(activity, "$label is not in the dock", Toast.LENGTH_SHORT).show()
            return
        }
        settings.removeDockKey(identityKey)
        Toast.makeText(activity, "Removed $label from dock", Toast.LENGTH_SHORT).show()
        render()
    }

    fun showApp(appKey: String) {
        settings.showApp(appKey)
        render()
    }
}
