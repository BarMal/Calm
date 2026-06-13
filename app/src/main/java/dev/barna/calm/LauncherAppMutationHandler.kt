package dev.barna.calm

import android.widget.Toast

class LauncherAppMutationHandler(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
    private val loadPinnedApps: () -> List<AppEntry>,
    private val beginClassicItemPlacement: (ClassicLauncherPageDefinition, String) -> Unit = { _, _ -> },
) {
    fun pinApp(app: AppEntry) {
        settings.pinPackage(app.identityKey)
        selectPage(CalmTheme.PINNED_KEY)
        toast(R.string.toast_app_pinned, app.label)
        render()
    }

    fun unpinApp(app: AppEntry) {
        settings.unpinPackage(app.identityKey)
        if (loadPinnedApps().isEmpty() && !settings.pinnedPageEnabled()) {
            selectPage(CalmTheme.APP_LIBRARY_KEY)
        }
        toast(R.string.toast_app_unpinned, app.label)
        render()
    }

    fun hideApp(app: AppEntry) {
        settings.hideApp(app.identityKey, app.label)
        toast(R.string.toast_app_hidden, app.label)
        render()
    }

    fun isDockItem(identityKey: String): Boolean {
        return identityKey in settings.dockKeys()
    }

    fun addDockItem(identityKey: String, label: String) {
        if (isDockItem(identityKey)) {
            toast(R.string.toast_dock_already_contains, label)
            return
        }
        if (settings.addDockKey(identityKey)) {
            settings.setDockEnabled(true)
            toast(R.string.toast_dock_added, label)
            render()
        } else {
            toast(R.string.toast_dock_full)
        }
    }

    fun removeDockItem(identityKey: String, label: String) {
        if (!isDockItem(identityKey)) {
            toast(R.string.toast_dock_missing, label)
            return
        }
        settings.removeDockKey(identityKey)
        toast(R.string.toast_dock_removed, label)
        render()
    }

    fun isClassicPageApp(identityKey: String): Boolean {
        return settings.isClassicPageApp(identityKey)
    }

    fun addAppToClassicPage(app: AppEntry) {
        if (isClassicPageApp(app.identityKey)) {
            toast(R.string.toast_classic_already_contains, app.label)
            return
        }
        if (settings.addAppToClassicPage(app.identityKey)) {
            val pageKey = settings.firstEnabledClassicPage()?.key ?: CalmTheme.OVERVIEW_KEY
            selectPage(pageKey)
            toast(R.string.toast_classic_added, app.label)
            render()
        } else {
            toast(R.string.toast_classic_full)
        }
    }

    fun addAppToClassicPage(page: ClassicLauncherPageDefinition, app: AppEntry) {
        if (isClassicPageApp(app.identityKey)) {
            toast(R.string.toast_classic_already_contains, app.label)
            return
        }
        if (settings.addAppToClassicPage(page.id, app.identityKey)) {
            selectPage(page.key)
            beginClassicItemPlacement(page, ClassicGridItem.app(app.identityKey, x = 0, y = 0).id)
            toast(R.string.toast_classic_added_drag, app.label)
            render()
        } else {
            toast(R.string.toast_page_full, page.title)
        }
    }

    fun showCategoryPicker(app: AppEntry) {
        val categories = settings.categoryList().filter { it.enabled }
        if (categories.isEmpty()) {
            Toast.makeText(activity, "No categories configured.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentIds = settings.appCategoryAssignments()[app.identityKey] ?: emptyList()
        val selected = BooleanArray(categories.size) { i -> categories[i].id in currentIds }
        GoogleInteractionStyle.dialogBuilder(activity)
            .setTitle(app.label)
            .setMultiChoiceItems(categories.map { it.title }.toTypedArray(), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                val chosen = categories.filterIndexed { i, _ -> selected[i] }.map { it.id }
                settings.setAppCategoryIds(app.identityKey, chosen)
                render()
            }
            .show()
    }

    fun showApp(appKey: String) {
        settings.showApp(appKey)
        render()
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(activity, activity.getString(resId, *args), Toast.LENGTH_SHORT).show()
    }
}
