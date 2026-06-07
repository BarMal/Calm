package dev.barna.calm

import android.content.ComponentName
import java.util.Base64
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class AppLibrarySnapshotCache(
    private val store: AppLibrarySnapshotStore,
    private val loader: () -> List<AppEntry>,
) {
    private var cachedApps: List<AppEntry>? = null
    private var cachedFingerprint: String? = null
    private var loadedFromSnapshot = false
    private val refreshPending = AtomicBoolean(false)

    @Synchronized
    fun load(): List<AppEntry> {
        cachedApps?.let { return it }
        store.load()?.let { snapshot ->
            cachedApps = snapshot.apps
            cachedFingerprint = snapshot.fingerprint
            loadedFromSnapshot = true
            return snapshot.apps
        }
        return refresh().apps
    }

    @Synchronized
    fun loadCachedOrEmpty(): List<AppEntry> {
        cachedApps?.let { return it }
        store.load()?.let { snapshot ->
            cachedApps = snapshot.apps
            cachedFingerprint = snapshot.fingerprint
            loadedFromSnapshot = true
            return snapshot.apps
        }
        return emptyList()
    }

    @Synchronized
    fun shouldRefreshPersistedSnapshot(): Boolean {
        return loadedFromSnapshot
    }

    @Synchronized
    fun shouldRefreshInBackground(): Boolean {
        return loadedFromSnapshot || cachedApps == null
    }

    @Synchronized
    fun refresh(): AppLibraryRefreshResult {
        val previousFingerprint = cachedFingerprint
        val apps = loader()
        val snapshot = AppLibrarySnapshot.from(apps)
        cachedApps = apps
        cachedFingerprint = snapshot.fingerprint
        loadedFromSnapshot = false
        store.save(snapshot)
        return AppLibraryRefreshResult(
            apps = apps,
            changed = previousFingerprint != null && previousFingerprint != snapshot.fingerprint,
        )
    }

    fun refreshAsync(executor: Executor, onRefreshed: (AppLibraryRefreshResult) -> Unit) {
        if (!refreshPending.compareAndSet(false, true)) return
        executor.execute {
            val result = try {
                refresh()
            } finally {
                refreshPending.set(false)
            }
            onRefreshed(result)
        }
    }

    @Synchronized
    fun invalidate() {
        cachedApps = null
        cachedFingerprint = null
        loadedFromSnapshot = false
        store.clear()
    }
}

data class AppLibraryRefreshResult(
    val apps: List<AppEntry>,
    val changed: Boolean,
)

interface AppLibrarySnapshotStore {
    fun load(): AppLibrarySnapshot?
    fun save(snapshot: AppLibrarySnapshot)
    fun clear()
}

data class AppLibrarySnapshot(
    val apps: List<AppEntry>,
    val fingerprint: String,
) {
    companion object {
        fun from(apps: List<AppEntry>): AppLibrarySnapshot {
            return AppLibrarySnapshot(apps, AppLibrarySnapshotCodec.fingerprint(apps))
        }
    }
}

object AppLibrarySnapshotCodec {
    fun encode(snapshot: AppLibrarySnapshot): String {
        return snapshot.apps.joinToString("\n") { app ->
            listOf(
                app.packageName,
                app.label,
                app.hueColor.toString(),
                app.identityKey,
                app.notificationSourceKey,
                app.componentName?.packageName.orEmpty(),
                app.componentName?.className.orEmpty(),
                app.profileLabel,
                app.isWorkProfile.toString(),
            ).joinToString("\t", transform = ::encodeField)
        }
    }

    fun decode(encoded: String): AppLibrarySnapshot? {
        if (encoded.isBlank()) return AppLibrarySnapshot(emptyList(), fingerprint(emptyList()))
        val apps = encoded.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull(::decodeApp)
            .toList()
        if (apps.isEmpty()) return null
        return AppLibrarySnapshot.from(apps)
    }

    fun fingerprint(apps: List<AppEntry>): String {
        return apps.joinToString("\n") { app ->
            listOf(
                app.identityKey,
                app.packageName,
                app.label,
                app.hueColor.toString(),
                app.notificationSourceKey,
                app.componentName?.let { component -> "${component.packageName}/${component.className}" }.orEmpty(),
                app.profileLabel,
                app.isWorkProfile.toString(),
            ).joinToString("|")
        }
    }

    private fun decodeApp(line: String): AppEntry? {
        val fields = line.split('\t').mapNotNull(::decodeField)
        if (fields.size != 9) return null
        val componentPackage = fields[5]
        val componentClass = fields[6]
        val component = if (componentPackage.isNotBlank() && componentClass.isNotBlank()) {
            ComponentName(componentPackage, componentClass)
        } else {
            null
        }
        return AppEntry(
            packageName = fields[0],
            label = fields[1],
            hueColor = fields[2].toIntOrNull() ?: 0,
            identityKey = fields[3],
            notificationSourceKey = fields[4],
            componentName = component,
            profileLabel = fields[7],
            isWorkProfile = fields[8].toBoolean(),
        )
    }

    private fun encodeField(value: String): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun decodeField(value: String): String? {
        return runCatching {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrNull()
    }
}
