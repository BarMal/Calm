package dev.barna.calm

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import java.text.Collator
import java.util.LinkedHashMap

class NotificationChapterRepository(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
) {
    fun buildNotificationChapters(): List<AppChapter> {
        val packageManager = activity.packageManager
        val launchableByPackage = loadLaunchableApps()
            .associateByTo(LinkedHashMap()) { it.activityInfo.packageName }
        val notificationsByPackage = LinkedHashMap<String, MutableList<CalmNotificationListenerService.CalmNotification>>()
        val excludedPackages = settings.excludedPackages()

        for (notification in CalmNotificationListenerService.snapshot()) {
            if (notification.packageName in excludedPackages) {
                continue
            }
            notificationsByPackage
                .getOrPut(notification.packageName) { ArrayList() }
                .add(notification)
        }

        return notificationsByPackage.entries
            .filter { it.value.isNotEmpty() }
            .map { (packageName, notifications) ->
                AppChapter(
                    packageName,
                    resolveAppLabel(packageManager, packageName, launchableByPackage[packageName]),
                    notifications,
                    launchableByPackage.containsKey(packageName),
                    resolveAppHue(packageManager, packageName),
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

    fun loadLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = activity.packageManager
        return packageManager.queryIntentActivities(intent, 0)
            .sortedWith { left, right ->
                Collator.getInstance().compare(
                    left.loadLabel(packageManager).toString(),
                    right.loadLabel(packageManager).toString(),
                )
            }
    }

    fun resolveExcludedLabel(packageName: String): String {
        return settings.excludedLabel(packageName) {
            resolveAppLabel(activity.packageManager, packageName, null)
        }
    }

    fun resolveChapterBackground(chapter: AppChapter): Bitmap? {
        for (notification in chapter.notifications) {
            notification.backgroundImage?.let { return it }
        }
        return try {
            activity.packageManager.getApplicationIcon(chapter.packageName).toBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun resolveAppLabel(
        packageManager: PackageManager,
        packageName: String,
        app: ResolveInfo?,
    ): String {
        if (app != null) {
            return app.loadLabel(packageManager).toString()
        }
        return try {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun resolveAppHue(packageManager: PackageManager, packageName: String): Int {
        return try {
            val icon = packageManager.getApplicationIcon(packageName)
            CalmColor.dominant(icon.toBitmap(), CalmTheme.ACCENT)
        } catch (_: PackageManager.NameNotFoundException) {
            0
        }
    }
}
