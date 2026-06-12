package dev.barna.calm

import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserHandle
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class AppIconRepository(
    cacheDir: File,
    private val launcherApps: LauncherApps?,
    private val packageManager: PackageManager,
    private val settings: LauncherSettings,
    private val maxMemoryCacheEntries: Int = DEFAULT_MEMORY_CACHE_ENTRIES,
    private val hueExecutor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val iconDiskCacheDir = File(cacheDir, "calm_icons").apply { mkdirs() }
    private val hueCache = BoundedMemoryCache<String, Int>(maxMemoryCacheEntries)
    private val iconCache = BoundedMemoryCache<String, Bitmap>(maxMemoryCacheEntries)
    private val maskedIconCache = BoundedMemoryCache<String, Bitmap>(maxMemoryCacheEntries)
    private val pendingHueKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    @Volatile
    private var shutdown = false
    @Volatile
    private var onHueResolved: Runnable? = null

    fun setOnHueResolved(listener: Runnable) {
        onHueResolved = listener
    }

    fun resolveAppBitmap(identityKey: String, packageName: String, userHandle: UserHandle?): Bitmap? {
        return iconCache.getOrPut(identityKey) {
            loadCachedIcon("unmasked", identityKey)?.let { return@getOrPut it }
            val profileIcon = userHandle?.let { user ->
                runCatching {
                    launcherApps?.getActivityList(packageName, user)
                        ?.firstOrNull()
                        ?.getIcon(0)
                        ?.toUnmaskedIconBitmap()
                }.getOrNull()
            }
            val generated = profileIcon ?: runCatching {
                packageManager.getApplicationIcon(packageName).toUnmaskedIconBitmap()
            }.getOrNull() ?: return@getOrPut null
            cacheIcon("unmasked", identityKey, generated)
            generated
        }
    }

    fun resolveMaskedAppBitmap(identityKey: String, packageName: String, userHandle: UserHandle?): Bitmap? {
        return maskedIconCache.getOrPut(identityKey) {
            loadCachedIcon("masked", identityKey)?.let { return@getOrPut it }
            val profileIcon = userHandle?.let { user ->
                runCatching {
                    launcherApps?.getActivityList(packageName, user)
                        ?.firstOrNull()
                        ?.getBadgedIcon(0)
                        ?.toBitmap()
                }.getOrNull()
            }
            val generated = profileIcon ?: runCatching {
                packageManager.getApplicationIcon(packageName).toBitmap()
            }.getOrNull() ?: return@getOrPut null
            cacheIcon("masked", identityKey, generated)
            generated
        }
    }

    fun resolveAppHue(identityKey: String, packageName: String, userHandle: UserHandle?): Int {
        hueCache[identityKey]?.let { return it }
        val persistedHue = settings.cachedAppHue(identityKey)
        if (persistedHue != 0) {
            hueCache[identityKey] = persistedHue
            return persistedHue
        }
        val legacyHue = settings.cachedAppHue(packageName)
        if (legacyHue != 0) {
            hueCache[identityKey] = legacyHue
            return legacyHue
        }
        scheduleHueResolution(identityKey, packageName, userHandle)
        return 0
    }

    fun invalidate() {
        hueCache.clear()
        iconCache.clear()
        maskedIconCache.clear()
        pendingHueKeys.clear()
    }

    fun shutdown() {
        shutdown = true
        onHueResolved = null
        invalidate()
        hueExecutor.shutdownNow()
    }

    private fun scheduleHueResolution(identityKey: String, packageName: String, userHandle: UserHandle?) {
        if (shutdown) return
        if (!pendingHueKeys.add(identityKey)) return
        try {
            hueExecutor.execute {
                val hue = computeAppHue(identityKey, packageName, userHandle)
                if (!shutdown && hue != 0) {
                    hueCache[identityKey] = hue
                    settings.cacheAppHue(identityKey, hue)
                }
                pendingHueKeys.remove(identityKey)
                if (!shutdown && hue != 0) {
                    onHueResolved?.run()
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingHueKeys.remove(identityKey)
        }
    }

    private fun computeAppHue(identityKey: String, packageName: String, userHandle: UserHandle?): Int {
        val icon = resolveAppBitmap(identityKey, packageName, userHandle) ?: return 0
        return CalmColor.dominant(icon, CalmTheme.ACCENT)
    }

    private fun loadCachedIcon(kind: String, identityKey: String): Bitmap? {
        val file = iconCacheFile(kind, identityKey)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun cacheIcon(kind: String, identityKey: String, bitmap: Bitmap) {
        runCatching {
            iconCacheFile(kind, identityKey).outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    private fun iconCacheFile(kind: String, identityKey: String): File {
        return File(iconDiskCacheDir, "$kind-${identityKey.sha256()}.png")
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DEFAULT_MEMORY_CACHE_ENTRIES = 96
    }
}
