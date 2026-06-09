package dev.barna.calm

class DockResolver {
    fun resolve(appEntries: List<AppEntry>, dockKeys: List<String>): List<AppEntry> {
        if (dockKeys.isEmpty() || appEntries.isEmpty()) return emptyList()
        val byIdentityKey = appEntries.associateBy { it.identityKey }
        val byPackageName = appEntries.associateBy { it.packageName }
        return dockKeys.mapNotNull { key ->
            byIdentityKey[key] ?: byPackageName[key]
        }
    }
}
