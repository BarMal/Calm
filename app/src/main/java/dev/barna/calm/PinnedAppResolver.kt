package dev.barna.calm

class PinnedAppResolver {
    fun resolve(appEntries: List<AppEntry>, pinnedKeys: Set<String>): List<AppEntry> {
        if (pinnedKeys.isEmpty()) return emptyList()
        return appEntries.filter { app ->
            app.identityKey in pinnedKeys || app.packageName in pinnedKeys
        }
    }

    fun isPinned(app: AppEntry, pinnedKeys: Set<String>): Boolean {
        return app.identityKey in pinnedKeys || app.packageName in pinnedKeys
    }
}
