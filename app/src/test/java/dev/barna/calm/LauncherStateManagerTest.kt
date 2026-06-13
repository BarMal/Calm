package dev.barna.calm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherStateManagerTest {

    private lateinit var context: Context
    private lateinit var settings: LauncherSettings
    private lateinit var activityController: ActivityController<MainActivity>
    private lateinit var notificationRepository: NotificationChapterRepository
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var rssRepository: RssFeedRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_state_manager", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = LauncherSettings(prefs)

        activityController = Robolectric.buildActivity(MainActivity::class.java)
        val activity = activityController.get()
        notificationRepository = NotificationChapterRepository(activity, settings)
        calendarRepository = CalendarRepository(activity) {}
        rssRepository = RssFeedRepository()
    }

    private fun manager(
        scope: CoroutineScope,
        workDispatcher: CoroutineDispatcher,
        ioDispatcher: CoroutineDispatcher = workDispatcher,
        mainDispatcher: CoroutineDispatcher = workDispatcher,
        markLoading: () -> Unit = {},
        onStateReady: (LauncherRenderModel) -> Unit = {},
        factory: LauncherRenderModelFactory = LauncherRenderModelFactory(),
    ) = LauncherStateManager(
        notificationRepository = notificationRepository,
        calendarRepository = calendarRepository,
        rssFeedRepository = rssRepository,
        settings = settings,
        renderModelFactory = factory,
        appCardDisplayCache = AppCardDisplayCache(notificationRepository, AppCardModelFactory()),
        scope = scope,
        workDispatcher = workDispatcher,
        ioDispatcher = ioDispatcher,
        mainDispatcher = mainDispatcher,
        loadAppEntries = { emptyList() },
        loadCachedAppEntries = { emptyList() },
        markLoading = markLoading,
        onStateReady = onStateReady,
    )

    @Test
    fun refreshAsyncDeliversModelOnSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var received: LauncherRenderModel? = null
        manager(scope = this, workDispatcher = dispatcher, onStateReady = { received = it }).refreshAsync()

        advanceUntilIdle()

        assertNotNull(received)
    }

    @Test
    fun markLoadingCalledBeforeOnStateReady() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = mutableListOf<String>()
        manager(
            scope = this,
            workDispatcher = dispatcher,
            markLoading = { events.add("loading") },
            onStateReady = { events.add("ready") },
        ).refreshAsync()

        advanceUntilIdle()

        assertEquals(listOf("loading", "ready"), events)
    }

    @Test
    fun secondRefreshSupersedesFirstGenerationResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var readyCount = 0

        val m = manager(scope = this, workDispatcher = dispatcher, onStateReady = { readyCount++ })
        m.refreshAsync()
        m.refreshAsync()

        advanceUntilIdle()

        assertEquals("only the second generation should deliver", 1, readyCount)
    }

    @Test
    fun staleGenerationDoesNotDeliverAfterNewerRefreshStarts() = runTest {
        val workDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mainDispatcher = QueuedCoroutineDispatcher()
        var received: LauncherRenderModel? = null
        var readyCount = 0

        val m = manager(
            scope = this,
            workDispatcher = workDispatcher,
            mainDispatcher = mainDispatcher,
            onStateReady = {
                readyCount++
                received = it
            },
        )
        m.refreshAsync()
        assertEquals(1, mainDispatcher.queuedCount)

        m.refreshAsync()
        assertEquals(2, mainDispatcher.queuedCount)

        mainDispatcher.runAll()

        assertNotNull("gen=2 must still deliver", received)
        assertEquals("gen=1 posted callback must be skipped", 1, readyCount)
    }

    @Test
    fun exceptionInFactoryDoesNotCrashCoroutineScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val throwingFactory = object : LauncherRenderModelFactory() {
            override fun create(
                preferences: LauncherUiPreferences,
                notificationChapters: List<AppChapter>,
                appEntries: List<AppEntry>,
                pinnedKeys: Set<String>,
                pinnedChapterPackages: Set<String>,
                classicPages: List<ClassicLauncherPageDefinition>,
                classicGridConfig: ClassicGridConfig,
                dockConfig: DockConfig,
                dockKeys: List<String>,
                hasCalendarPermission: Boolean,
                calendarEvents: List<CalendarEvent>,
                rssFeedUrls: List<String>,
                rssItems: List<RssFeedItem>,
            ) = throw RuntimeException("deliberate failure")
        }
        val m = manager(scope = this, workDispatcher = dispatcher, factory = throwingFactory)

        m.refreshAsync()
        advanceUntilIdle()
    }

    @Test
    fun onStateReadyNotCalledWhenFactoryThrows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var received: LauncherRenderModel? = null
        val throwingFactory = object : LauncherRenderModelFactory() {
            override fun create(
                preferences: LauncherUiPreferences,
                notificationChapters: List<AppChapter>,
                appEntries: List<AppEntry>,
                pinnedKeys: Set<String>,
                pinnedChapterPackages: Set<String>,
                classicPages: List<ClassicLauncherPageDefinition>,
                classicGridConfig: ClassicGridConfig,
                dockConfig: DockConfig,
                dockKeys: List<String>,
                hasCalendarPermission: Boolean,
                calendarEvents: List<CalendarEvent>,
                rssFeedUrls: List<String>,
                rssItems: List<RssFeedItem>,
            ) = throw RuntimeException("deliberate failure")
        }
        val m = manager(
            scope = this,
            workDispatcher = dispatcher,
            factory = throwingFactory,
            onStateReady = { received = it },
        )

        m.refreshAsync()
        advanceUntilIdle()

        assertNull("factory failure must not deliver a model", received)
    }

    @Test
    fun cancelledScopeDoesNotDeliverState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val parentJob = SupervisorJob()
        val cancelledScope = TestScope(parentJob + dispatcher)
        var received: LauncherRenderModel? = null
        val m = manager(
            scope = cancelledScope,
            workDispatcher = dispatcher,
            onStateReady = { received = it },
        )

        parentJob.cancel()
        m.refreshAsync()
        advanceUntilIdle()

        assertNull("cancelled scope must not deliver a model", received)
    }
}

private class QueuedCoroutineDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    val queuedCount: Int get() = queue.size

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    fun runAll() {
        while (queue.isNotEmpty()) {
            queue.removeFirst().run()
        }
    }
}
