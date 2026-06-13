package dev.barna.calm

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherStateManager(
    private val notificationRepository: NotificationChapterRepository,
    private val calendarRepository: CalendarRepository,
    private val rssFeedRepository: RssFeedRepository,
    private val settings: LauncherSettings,
    private val renderModelFactory: LauncherRenderModelFactory,
    private val appCardDisplayCache: AppCardDisplayCache,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val loadAppEntries: () -> List<AppEntry>,
    private val loadCachedAppEntries: () -> List<AppEntry>,
    private val markLoading: () -> Unit,
    private val onStateReady: (LauncherRenderModel) -> Unit,
) {
    private val generation = AtomicInteger(0)

    fun refreshAsync(): Job {
        markLoading()
        val gen = generation.incrementAndGet()
        return scope.launch(workDispatcher) {
            try {
                val notifications = async(ioDispatcher) {
                    notificationRepository.buildNotificationChapters()
                }
                val apps = async(ioDispatcher) { loadAppEntries() }
                val calendar = async(ioDispatcher) {
                    val hasPermission = calendarRepository.hasCalendarPermission()
                    hasPermission to if (hasPermission) calendarRepository.loadUpcomingEvents() else emptyList()
                }
                val rss = async(ioDispatcher) {
                    if (settings.rssPageEnabled()) rssFeedRepository.loadItems(settings.rssFeedUrls()) else emptyList()
                }

                val appEntries = apps.await()
                val pinnedKeys = settings.pinnedPackages()
                val calendarState = calendar.await()
                if (gen != generation.get()) return@launch

                val state = renderModelFactory.create(
                    preferences = settings.uiPreferences(),
                    notificationChapters = notifications.await(),
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
                    rssItems = rss.await(),
                )
                if (gen != generation.get()) return@launch

                appCardDisplayCache.preloadNow(state.appEntries, state.pinnedKeys)
                withContext(mainDispatcher) {
                    if (gen == generation.get()) {
                        onStateReady(state)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                // A later refresh will replace the loading state; avoid crashing the coroutine scope.
                Log.e(TAG, "Refresh gen $gen failed unexpectedly", e)
            }
        }
    }

    private companion object {
        private const val TAG = "LauncherStateManager"
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
