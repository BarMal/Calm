package dev.barna.calm

import android.os.Handler
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

class LauncherStateManager(
    private val notificationRepository: NotificationChapterRepository,
    private val calendarRepository: CalendarRepository,
    private val rssFeedRepository: RssFeedRepository,
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
        try {
            val notifications = executor.submit<List<AppChapter>> {
                notificationRepository.buildNotificationChapters()
            }
            val apps = executor.submit<List<AppEntry>> { loadAppEntries() }
            val calendar = executor.submit<Pair<Boolean, List<CalendarEvent>>> {
                val hasPermission = calendarRepository.hasCalendarPermission()
                hasPermission to if (hasPermission) calendarRepository.loadUpcomingEvents() else emptyList()
            }
            val rss = executor.submit<List<RssFeedItem>> {
                if (settings.rssPageEnabled()) rssFeedRepository.loadItems(settings.rssFeedUrls()) else emptyList()
            }
            executor.execute {
                try {
                    val appEntries = apps.get()
                    val pinnedKeys = settings.pinnedPackages()
                    val calendarState = calendar.get()
                    if (gen != generation.get()) return@execute
                    val state = renderModelFactory.create(
                        preferences = settings.uiPreferences(),
                        notificationChapters = notifications.get(),
                        appEntries = appEntries,
                        pinnedKeys = pinnedKeys,
                        pinnedChapterPackages = settings.pinnedChapterPackages(),
                        classicPages = settings.classicPages(),
                        classicGridConfig = settings.classicGridConfig(),
                        dockConfig = settings.dockConfig(),
                        dockKeys = settings.dockKeys(),
                        hasCalendarPermission = calendarState.first,
                        calendarEvents = calendarState.second,
                        rssFeedUrls = settings.rssFeedUrls(),
                        rssItems = rss.get(),
                    )
                    if (gen != generation.get()) return@execute
                    appCardDisplayCache.preloadNow(state.appEntries, state.pinnedKeys)
                    mainHandler.post {
                        if (gen == generation.get()) {
                            onStateReady(state)
                        }
                    }
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (_: Exception) {
                    // A later refresh will replace the loading state; avoid crashing the executor thread.
                }
            }
        } catch (_: RejectedExecutionException) {
            // The runner is shutting down; stale work can be ignored.
        }
    }

    fun buildCachedShell(): LauncherRenderModel {
        val appEntries = loadCachedAppEntries()
        val pinnedKeys = settings.pinnedPackages()
        return renderModelFactory.create(
            preferences = settings.uiPreferences(),
            notificationChapters = emptyList(),
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedChapterPackages = settings.pinnedChapterPackages(),
            classicPages = settings.classicPages(),
            classicGridConfig = settings.classicGridConfig(),
            dockConfig = settings.dockConfig(),
            dockKeys = settings.dockKeys(),
            hasCalendarPermission = calendarRepository.hasCalendarPermission(),
            calendarEvents = emptyList(),
            rssFeedUrls = settings.rssFeedUrls(),
            rssItems = emptyList(),
        )
    }

    fun buildSync(): LauncherRenderModel {
        val appEntries = loadCachedAppEntries()
        val notificationChapters = notificationRepository.buildNotificationChapters(appEntries)
        val pinnedKeys = settings.pinnedPackages()
        val hasCalendarPermission = calendarRepository.hasCalendarPermission()
        val rssFeedUrls = settings.rssFeedUrls()
        return renderModelFactory.create(
            preferences = settings.uiPreferences(),
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            pinnedChapterPackages = settings.pinnedChapterPackages(),
            classicPages = settings.classicPages(),
            classicGridConfig = settings.classicGridConfig(),
            dockConfig = settings.dockConfig(),
            dockKeys = settings.dockKeys(),
            hasCalendarPermission = hasCalendarPermission,
            calendarEvents = if (hasCalendarPermission) calendarRepository.loadUpcomingEvents() else emptyList(),
            rssFeedUrls = rssFeedUrls,
            rssItems = if (settings.rssPageEnabled()) rssFeedRepository.loadItems(rssFeedUrls) else emptyList(),
        )
    }
}
