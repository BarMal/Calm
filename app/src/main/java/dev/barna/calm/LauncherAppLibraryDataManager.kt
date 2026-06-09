package dev.barna.calm

import android.os.Handler
import java.util.concurrent.ExecutorService

class LauncherAppLibraryDataManager(
    private val notificationRepository: NotificationChapterRepository,
    private val settings: LauncherSettings,
    private val appLibraryStore: AppLibraryRenderStore,
    private val appCardDisplayCache: AppCardDisplayCache,
    private val notificationCardDisplayCache: NotificationCardDisplayCache,
    private val appSearchController: AppSearchController,
    private val mainHandler: Handler,
    private val executor: ExecutorService,
) {
    fun refreshInBackground() {
        val scheduled = notificationRepository.refreshLaunchableApps(executor) { result ->
            mainHandler.post {
                if (result.changed) {
                    appCardDisplayCache.clear()
                    notificationCardDisplayCache.clear()
                }
                val state = appLibraryStore.replace(result.apps.filterNot(settings::isAppHidden))
                appSearchController.refreshVisible(state)
            }
        }
        if (scheduled) {
            val state = appLibraryStore.dispatch(AppLibraryRenderEvent.LoadingStarted)
            appSearchController.refreshVisible(state)
        }
    }

    fun loadAppEntries(): List<AppEntry> {
        val apps = notificationRepository.loadAppEntries().filterNot(settings::isAppHidden)
        appLibraryStore.replace(apps)
        return apps
    }

    fun loadCachedAppEntries(): List<AppEntry> {
        val apps = notificationRepository.loadCachedAppEntries().filterNot(settings::isAppHidden)
        appLibraryStore.replace(apps)
        return apps
    }

    fun loadPinnedAppEntries(): List<AppEntry> {
        val pinnedPackages = settings.pinnedPackages()
        if (pinnedPackages.isEmpty()) return emptyList()
        return notificationRepository.loadPinnedAppEntries(pinnedPackages).filterNot(settings::isAppHidden)
    }
}
