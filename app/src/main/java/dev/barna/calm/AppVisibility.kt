package dev.barna.calm

object AppVisibility {
    fun isHidden(app: AppEntry, hiddenKeys: Set<String>): Boolean {
        return app.identityKey in hiddenKeys || app.packageName in hiddenKeys
    }

    fun visibleApps(apps: List<AppEntry>, hiddenKeys: Set<String>): List<AppEntry> {
        return apps.filterNot { app -> isHidden(app, hiddenKeys) }
    }
}
