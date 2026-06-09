package dev.barna.calm

import android.os.Handler
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

class LauncherStateManager(
    private val notificationRepository: NotificationChapterRepository,
    private val calendarRepository: CalendarRepository,
    private val settings: LauncherSettings,
    private val renderModelFactory: LauncherRenderModelFactory,
    private val appCardDisplayCache: AppCardDisplayCache,
    private val mainHandler: Handler,
    private val executor: ExecutorService,
    private val loadAppEntries: () -> List<AppEntry>,
    private val loadCachedAppEntries: () -> List<AppEntry>,
    private val markLoading: () -> Unit,
    private val onStateReady: (LauncherRenderModel) -> Unit,
) {
    private val generation = AtomicInteger(0)

    fun refreshAsync() {
        markLoading()
        val gen = generation.incrementAndGet()
        val notifications = executor.submit<List<AppChapter>> {
            notificationRepository.buildNotificationChapters()
        }
        val apps = executor.submit<List<AppEntry>> { loadAppEntries() }
        val calendar = executor.submit<Pair<Boolean, List<CalendarEvent>>> {
            val hasPermission = calendarRepository.hasCalendarPermission()
            hasPermission to if (hasPermission) calendarRepository.loadUpcomingEvents() else emptyList()
        }
        executor.execute {
            val appEntries = apps.get()
            val pinnedKeys = settings.pinnedPackages()
            val calendarState = calendar.get()
            val state = renderModelFactory.create(
                preferences = settings.uiPreferences(),
                notificationChapters = notifications.get(),
                appEntries = appEntries,
                pinnedKeys = pinnedKeys,
                pinnedChapterPackages = settings.pinnedChapterPackages(),
                dockConfig = settings.dockConfig(),
                dockKeys = settings.dockKeys(),
                hasCalendarPermission = calendarState.first,
                calendarEvents = calendarState.second,
            )
            appCardDisplayCache.preloadNow(state.appEntries, state.pinnedKeys)
            mainHandler.post {
                if (gen == generation.get()) {
                    onStateReady(state)
                }
            }
        }
    }

    fun buildSync(): LauncherRenderModel {
        val appEntries = loadCachedAppEntries()
        val notificationChapters = notificationRepository.buildNotificationChapters(appEntries)
        val pinnedKeys = settings.pinnedPackages()
        val hasCalendarPermission = calendarRepository.hasCalendarPermission()
        return renderModelFactory.create(
            preferences = settings.uiPreferences(),
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedChapterPackages = settings.pinnedChapterPackages(),
            dockConfig = settings.dockConfig(),
            dockKeys = settings.dockKeys(),
            hasCalendarPermission = hasCalendarPermission,
            calendarEvents = if (hasCalendarPermission) calendarRepository.loadUpcomingEvents() else emptyList(),
        )
    }
}
