package dev.barna.calm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.io.File
import java.security.MessageDigest
import java.text.Collator
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NotificationChapterRepository(
    private val activity: Context,
    private val settings: LauncherSettings,
) {
    private val launcherApps: LauncherApps? = activity.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = activity.getSystemService(UserManager::class.java)
    private val hueCache = ConcurrentHashMap<String, Int>()
    private val iconCache = ConcurrentHashMap<String, Bitmap>()
    private val maskedIconCache = ConcurrentHashMap<String, Bitmap>()
    private val iconDiskCacheDir = File(activity.cacheDir, "calm_icons").apply { mkdirs() }
    private val pendingHueKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val hueExecutor = Executors.newSingleThreadExecutor()
    private var launchableAppsCache: List<AppEntry>? = null
    private var onHueResolved: Runnable? = null

    fun setOnHueResolved(listener: Runnable) {
        onHueResolved = listener
    }

    fun buildNotificationChapters(): List<AppChapter> {
        val launchableApps = loadLaunchableApps()
        val launchableBySource = launchableApps
            .groupBy { it.notificationSourceKey }
            .mapValues { it.value.first() }
        val launchableByPackage = launchableApps
            .groupBy { it.packageName }
            .mapValues { it.value.first() }
        val notificationsBySource = LinkedHashMap<String, MutableList<CalmNotificationListenerService.CalmNotification>>()
        val excludedSources = settings.excludedPackages()
        val filters = settings.notificationFilters()

        for (notification in CalmNotificationListenerService.snapshot()) {
            if (notification.sourceKey in excludedSources || notification.packageName in excludedSources) {
                continue
            }
            if (filters.any { it.matches(notification) }) {
                continue
            }
            notificationsBySource
                .getOrPut(notification.sourceKey) { ArrayList() }
                .add(notification)
        }

        return notificationsBySource.entries
            .filter { it.value.isNotEmpty() }
            .map { (sourceKey, notifications) ->
                val first = notifications.first()
                val launchable = launchableBySource[sourceKey] ?: launchableByPackage[first.packageName]
                AppChapter(
                    packageName = first.packageName,
                    label = launchable?.label ?: resolveAppLabel(first.packageName),
                    notifications = notifications,
                    launchable = launchable != null,
                    hueColor = resolveAppHue(launchable?.identityKey ?: sourceKey, first.packageName, launchable?.userHandle),
                    identityKey = sourceKey,
                    launcherIdentityKey = launchable?.identityKey ?: sourceKey,
                    componentName = launchable?.componentName,
                    userHandle = launchable?.userHandle,
                    profileLabel = launchable?.profileLabel.orEmpty(),
                    isWorkProfile = launchable?.isWorkProfile ?: false,
                )
            }
            .sortedWith { left, right ->
                val notificationCompare = right.notifications.size.compareTo(left.notifications.size)
                if (notificationCompare != 0) {
                    notificationCompare
                } else {
                    Collator.getInstance().compare(left.label, right.label)
                }
            }
    }

    @Synchronized
    fun loadLaunchableApps(): List<AppEntry> {
        launchableAppsCache?.let { return it }
        val apps = loadProfileLaunchableApps().ifEmpty { loadPackageManagerLaunchableApps() }
            .sortedWith { left, right ->
                val labelCompare = Collator.getInstance().compare(left.label, right.label)
                if (labelCompare != 0) {
                    labelCompare
                } else {
                    Collator.getInstance().compare(left.profileLabel, right.profileLabel)
                }
            }
        launchableAppsCache = apps
        return apps
    }

    fun loadAppEntries(): List<AppEntry> {
        return loadLaunchableApps()
    }

    fun loadPinnedAppEntries(pinnedKeys: Set<String>): List<AppEntry> {
        return loadLaunchableApps()
            .filter { app -> app.identityKey in pinnedKeys || app.packageName in pinnedKeys }
            .sortedWith { left, right -> Collator.getInstance().compare(left.label, right.label) }
    }

    fun resolveExcludedLabel(sourceKey: String): String {
        return settings.excludedLabel(sourceKey) {
            val identity = AppIdentity.decode(sourceKey)
            resolveAppLabel(identity.packageName)
        }
    }

    fun resolveChapterBackground(chapter: AppChapter): Bitmap? {
        for (notification in chapter.notifications) {
            notification.backgroundImage?.let { return it }
        }
        return resolveAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
    }

    fun resolveAppLabel(packageName: String): String {
        return loadLaunchableApps().firstOrNull { it.packageName == packageName }?.label
            ?: resolvePackageManagerLabel(packageName)
    }

    fun resolveAppHue(packageName: String): Int {
        return resolveAppHue(AppIdentity.packageOnly(packageName).key, packageName, null)
    }

    fun resolveAppIcon(app: AppEntry, sizePx: Int): BitmapDrawable? {
        return resolveAppIcon(app.identityKey, app.packageName, app.userHandle, sizePx)
    }

    fun resolveAppIconBitmap(app: AppEntry): Bitmap? {
        return resolveAppBitmap(app.identityKey, app.packageName, app.userHandle)
    }

    fun resolveAppIconBitmap(chapter: AppChapter): Bitmap? {
        return resolveAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
    }

    fun resolveAppIcon(chapter: AppChapter, sizePx: Int): BitmapDrawable? {
        return resolveAppIcon(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle, sizePx)
    }

    fun resolveMaskedAppIconBitmap(chapter: AppChapter): Bitmap? {
        return resolveMaskedAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
    }

    fun resolveMaskedAppIcon(chapter: AppChapter, sizePx: Int): BitmapDrawable? {
        return resolveMaskedAppIcon(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle, sizePx)
    }

    fun resolveAppIcon(packageName: String, sizePx: Int): BitmapDrawable? {
        val app = loadLaunchableApps().firstOrNull { it.packageName == packageName }
        return if (app != null) {
            resolveAppIcon(app, sizePx)
        } else {
            resolveAppIcon(AppIdentity.packageOnly(packageName).key, packageName, null, sizePx)
        }
    }

    fun openApp(app: AppEntry): Boolean {
        val componentName = app.componentName ?: return openPackageFallback(app.packageName)
        val userHandle = app.userHandle ?: return openPackageFallback(app.packageName)
        val launcher = launcherApps ?: return openPackageFallback(app.packageName)
        return runCatching {
            launcher.startMainActivity(componentName, userHandle, null, null)
        }.isSuccess
    }

    fun openChapter(chapter: AppChapter): Boolean {
        val componentName = chapter.componentName ?: return openPackageFallback(chapter.packageName)
        val userHandle = chapter.userHandle ?: return openPackageFallback(chapter.packageName)
        val launcher = launcherApps ?: return openPackageFallback(chapter.packageName)
        return runCatching {
            launcher.startMainActivity(componentName, userHandle, null, null)
        }.isSuccess
    }

    private fun loadProfileLaunchableApps(): List<AppEntry> {
        val launcher = launcherApps ?: return emptyList()
        val users = runCatching { userManager?.userProfiles.orEmpty() }.getOrDefault(emptyList())
        val currentUserSerial = profileSerial(Process.myUserHandle())
        return users.flatMap { user ->
            runCatching { launcher.getActivityList(null, user) }
                .getOrDefault(emptyList())
                .map { info -> appEntry(info, user, currentUserSerial) }
        }
    }

    private fun appEntry(
        info: LauncherActivityInfo,
        user: UserHandle,
        currentUserSerial: Long,
    ): AppEntry {
        val userSerial = profileSerial(user)
        val componentName = info.componentName
        val identity = AppIdentity(
            packageName = componentName.packageName,
            className = componentName.className,
            userSerial = userSerial,
        )
        val label = info.label?.toString()?.takeIf { it.isNotBlank() }
            ?: friendlyPackageName(componentName.packageName)
        val isWorkProfile = currentUserSerial != AppIdentity.LEGACY_USER_SERIAL && userSerial != currentUserSerial
        val profileLabel = if (isWorkProfile) "Work" else "Personal"
        return AppEntry(
            packageName = componentName.packageName,
            label = label,
            hueColor = resolveAppHue(identity.key, componentName.packageName, user),
            identityKey = identity.key,
            notificationSourceKey = identity.notificationSourceKey,
            componentName = componentName,
            userHandle = user,
            profileLabel = profileLabel,
            isWorkProfile = isWorkProfile,
        )
    }

    private fun loadPackageManagerLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = activity.packageManager
        return packageManager.queryIntentActivities(intent, 0).map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val className = resolveInfo.activityInfo.name
            val identity = AppIdentity.packageOnly(packageName)
            AppEntry(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                hueColor = resolveAppHue(identity.key, packageName, null),
                identityKey = identity.key,
                notificationSourceKey = identity.notificationSourceKey,
                componentName = ComponentName(packageName, className),
            )
        }
    }

    private fun resolveAppIcon(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
        sizePx: Int,
    ): BitmapDrawable? {
        val bitmap = resolveAppBitmap(identityKey, packageName, userHandle) ?: return null
        return BitmapDrawable(activity.resources, bitmap).apply {
            setBounds(0, 0, sizePx, sizePx)
        }
    }

    private fun resolveMaskedAppIcon(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
        sizePx: Int,
    ): BitmapDrawable? {
        val bitmap = resolveMaskedAppBitmap(identityKey, packageName, userHandle) ?: return null
        return BitmapDrawable(activity.resources, bitmap).apply {
            setBounds(0, 0, sizePx, sizePx)
        }
    }

    private fun resolveAppBitmap(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
    ): Bitmap? {
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
                activity.packageManager.getApplicationIcon(packageName).toUnmaskedIconBitmap()
            }.getOrNull() ?: return null
            cacheIcon("unmasked", identityKey, generated)
            generated
        }
    }

    private fun resolveMaskedAppBitmap(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
    ): Bitmap? {
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
                activity.packageManager.getApplicationIcon(packageName).toBitmap()
            }.getOrNull() ?: return null
            cacheIcon("masked", identityKey, generated)
            generated
        }
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

    private fun resolvePackageManagerLabel(packageName: String): String {
        return try {
            val info = activity.packageManager.getApplicationInfo(packageName, 0)
            activity.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            friendlyPackageName(packageName)
        }
    }

    @Synchronized
    fun invalidateLaunchableApps() {
        launchableAppsCache = null
    }

    @Synchronized
    fun invalidateAppCaches() {
        launchableAppsCache = null
        hueCache.clear()
        iconCache.clear()
        maskedIconCache.clear()
        pendingHueKeys.clear()
    }

    private fun resolveAppHue(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
    ): Int {
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

    private fun scheduleHueResolution(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
    ) {
        if (!pendingHueKeys.add(identityKey)) return
        hueExecutor.execute {
            val hue = computeAppHue(identityKey, packageName, userHandle)
            if (hue != 0) {
                hueCache[identityKey] = hue
                settings.cacheAppHue(identityKey, hue)
            }
            pendingHueKeys.remove(identityKey)
            if (hue != 0) {
                onHueResolved?.run()
            }
        }
    }

    private fun computeAppHue(
        identityKey: String,
        packageName: String,
        userHandle: UserHandle?,
    ): Int {
        val icon = resolveAppBitmap(identityKey, packageName, userHandle) ?: return 0
        return CalmColor.dominant(icon, CalmTheme.ACCENT)
    }

    private fun openPackageFallback(packageName: String): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        return true
    }

    private fun profileSerial(user: UserHandle): Long {
        return runCatching {
            userManager?.getSerialNumberForUser(user) ?: AppIdentity.LEGACY_USER_SERIAL
        }.getOrDefault(AppIdentity.LEGACY_USER_SERIAL)
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
