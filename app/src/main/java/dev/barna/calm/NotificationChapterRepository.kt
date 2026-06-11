package dev.barna.calm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Collator
import java.util.LinkedHashMap
import java.util.concurrent.Executor

class NotificationChapterRepository(
    private val activity: Context,
    private val settings: LauncherSettings,
) : NotificationCardAssetResolver {
    private val launcherApps: LauncherApps? = activity.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = activity.getSystemService(UserManager::class.java)
    private val appIconRepository = AppIconRepository(
        cacheDir = activity.cacheDir,
        launcherApps = launcherApps,
        packageManager = activity.packageManager,
        settings = settings,
    )
    private val launchableAppsCache = AppLibrarySnapshotCache(
        store = SettingsAppLibrarySnapshotStore(settings),
        loader = ::loadFreshLaunchableApps,
    )

    fun setOnHueResolved(listener: Runnable) {
        appIconRepository.setOnHueResolved(listener)
    }

    fun buildNotificationChapters(launchableApps: List<AppEntry> = loadLaunchableApps()): List<AppChapter> {
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
                    hueColor = appIconRepository.resolveAppHue(launchable?.identityKey ?: sourceKey, first.packageName, launchable?.userHandle),
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

    fun loadLaunchableApps(): List<AppEntry> {
        return launchableAppsCache.load().map(::withRestoredUserHandle)
    }

    fun loadCachedLaunchableApps(): List<AppEntry> {
        return launchableAppsCache.loadCachedOrEmpty().map(::withRestoredUserHandle)
    }

    // AppLibrarySnapshotCodec does not serialise UserHandle (not a serialisable Android type).
    // Reconstruct it from the user serial embedded in the identityKey so that work-profile apps
    // can be launched via LauncherApps.startMainActivity even when loaded from the cache.
    private fun withRestoredUserHandle(app: AppEntry): AppEntry {
        if (app.userHandle != null) return app
        val userSerial = AppIdentity.decode(app.identityKey).userSerial
        if (userSerial == AppIdentity.LEGACY_USER_SERIAL) return app
        val user = runCatching { userManager?.getUserForSerialNumber(userSerial) }.getOrNull() ?: return app
        return app.copy(userHandle = user)
    }

    fun refreshLaunchableApps(
        executor: Executor,
        onRefreshed: (AppLibraryRefreshResult) -> Unit,
    ): Boolean {
        if (!launchableAppsCache.shouldRefreshInBackground()) return false
        launchableAppsCache.refreshAsync(executor, onRefreshed)
        return true
    }

    private fun loadFreshLaunchableApps(): List<AppEntry> {
        return loadProfileLaunchableApps().ifEmpty { loadPackageManagerLaunchableApps() }
            .sortedWith { left, right ->
                val labelCompare = Collator.getInstance().compare(left.label, right.label)
                if (labelCompare != 0) {
                    labelCompare
                } else {
                    Collator.getInstance().compare(left.profileLabel, right.profileLabel)
                }
            }
    }

    fun loadAppEntries(): List<AppEntry> {
        return loadLaunchableApps()
    }

    fun loadCachedAppEntries(): List<AppEntry> {
        return loadCachedLaunchableApps()
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
        return appIconRepository.resolveAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
    }

    fun resolveAppLabel(packageName: String): String {
        return loadLaunchableApps().firstOrNull { it.packageName == packageName }?.label
            ?: resolvePackageManagerLabel(packageName)
    }

    fun resolveAppHue(packageName: String): Int {
        return appIconRepository.resolveAppHue(AppIdentity.packageOnly(packageName).key, packageName, null)
    }

    fun resolveAppIcon(app: AppEntry, sizePx: Int): BitmapDrawable? {
        return resolveAppIcon(app.identityKey, app.packageName, app.userHandle, sizePx)
    }

    fun resolveAppIconBitmap(app: AppEntry): Bitmap? {
        return appIconRepository.resolveAppBitmap(app.identityKey, app.packageName, app.userHandle)
    }

    override fun resolveAppIconBitmap(chapter: AppChapter): Bitmap? {
        return appIconRepository.resolveAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
    }

    fun resolveAppIcon(chapter: AppChapter, sizePx: Int): BitmapDrawable? {
        return resolveAppIcon(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle, sizePx)
    }

    override fun resolveMaskedAppIconBitmap(chapter: AppChapter): Bitmap? {
        return appIconRepository.resolveMaskedAppBitmap(chapter.launcherIdentityKey, chapter.packageName, chapter.userHandle)
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
        val users = runCatching { userManager?.userProfiles.orEmpty() }.getOrNull() ?: emptyList()
        return users.flatMap { user ->
            runCatching { launcher.getActivityList(null, user) }
                .getOrNull().orEmpty()
                .map { info -> appEntry(info, user) }
        }
    }

    private fun appEntry(
        info: LauncherActivityInfo,
        user: UserHandle,
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
        val isWorkProfile = user != Process.myUserHandle()
        val profileLabel = if (isWorkProfile) "Work" else "Personal"
        return AppEntry(
            packageName = componentName.packageName,
            label = label,
            hueColor = appIconRepository.resolveAppHue(identity.key, componentName.packageName, user),
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
                hueColor = appIconRepository.resolveAppHue(identity.key, packageName, null),
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
        val bitmap = appIconRepository.resolveAppBitmap(identityKey, packageName, userHandle) ?: return null
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
        val bitmap = appIconRepository.resolveMaskedAppBitmap(identityKey, packageName, userHandle) ?: return null
        return BitmapDrawable(activity.resources, bitmap).apply {
            setBounds(0, 0, sizePx, sizePx)
        }
    }

    private fun resolvePackageManagerLabel(packageName: String): String {
        return try {
            val info = activity.packageManager.getApplicationInfo(packageName, 0)
            activity.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            friendlyPackageName(packageName)
        }
    }

    fun invalidateLaunchableApps() {
        launchableAppsCache.invalidate()
    }

    fun invalidateAppCaches() {
        launchableAppsCache.invalidate()
        appIconRepository.invalidate()
    }

    fun getAppShortcuts(chapter: AppChapter): List<AppShortcutEntry> {
        return getAppShortcuts(chapter.packageName, chapter.userHandle)
    }

    fun getAppShortcuts(app: AppEntry): List<AppShortcutEntry> {
        return getAppShortcuts(app.packageName, app.userHandle)
    }

    private fun getAppShortcuts(packageName: String, userHandle: UserHandle?): List<AppShortcutEntry> {
        val launcher = launcherApps ?: return emptyList()
        val resolvedUserHandle = userHandle ?: return emptyList()
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
            setPackage(packageName)
        }
        return runCatching { launcher.getShortcuts(query, resolvedUserHandle).orEmpty() }
            .getOrNull().orEmpty()
            .mapNotNull { info ->
                val label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                AppShortcutEntry(label, info.id, packageName, resolvedUserHandle)
            }
    }

    fun launchShortcut(shortcut: AppShortcutEntry): Boolean {
        val launcher = launcherApps ?: return false
        val userHandle = shortcut.userHandle ?: return false
        return runCatching {
            launcher.startShortcut(shortcut.packageName, shortcut.id, null, null, userHandle)
        }.isSuccess
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
        }.getOrNull() ?: AppIdentity.LEGACY_USER_SERIAL
    }
}

private class SettingsAppLibrarySnapshotStore(
    private val settings: LauncherSettings,
) : AppLibrarySnapshotStore {
    override fun load(): AppLibrarySnapshot? {
        return settings.cachedLaunchableAppsSnapshot()
    }

    override fun save(snapshot: AppLibrarySnapshot) {
        settings.cacheLaunchableAppsSnapshot(snapshot)
    }

    override fun clear() {
        settings.clearLaunchableAppsSnapshot()
    }
}
