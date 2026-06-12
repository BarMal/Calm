package dev.barna.calm

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LauncherStateManagerTest {

    private lateinit var context: Context
    private lateinit var settings: LauncherSettings
    private lateinit var mainHandler: Handler
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
        mainHandler = Handler(Looper.getMainLooper())

        activityController = Robolectric.buildActivity(MainActivity::class.java)
        val activity = activityController.get()
        notificationRepository = NotificationChapterRepository(activity, settings)
        calendarRepository = CalendarRepository(activity) {}
        rssRepository = RssFeedRepository()
    }

    private fun manager(
        executor: AbstractExecutorService = SynchronousExecutorService(),
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
        mainHandler = mainHandler,
        executor = executor,
        loadAppEntries = { emptyList() },
        loadCachedAppEntries = { emptyList() },
        markLoading = markLoading,
        onStateReady = onStateReady,
    )

    // ---- happy path ----

    @Test
    fun refreshAsyncDeliversModelOnSuccess() {
        var received: LauncherRenderModel? = null
        manager(onStateReady = { received = it }).refreshAsync()
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(received)
    }

    @Test
    fun markLoadingCalledBeforeOnStateReady() {
        val events = mutableListOf<String>()
        manager(
            markLoading = { events.add("loading") },
            onStateReady = { events.add("ready") },
        ).refreshAsync()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("loading", "ready"), events)
    }

    // ---- generation / cancellation ----

    @Test
    fun secondRefreshSupersedesFirstGenerationResult() {
        // DeferringExecutorService: submit() completes futures immediately (so .get() works),
        // execute() queues the final assembly task. This lets us interleave two refreshAsync()
        // calls before running the assembly tasks, simulating a real concurrent scenario.
        val deferred = DeferringExecutorService()
        var readyCount = 0

        val m = manager(executor = deferred, onStateReady = { readyCount++ })
        m.refreshAsync() // gen=1: assembly queued
        m.refreshAsync() // gen=2: generation incremented; gen=1 assembly will see gen mismatch

        deferred.runAll() // run both assembly tasks
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("only the second generation should deliver", 1, readyCount)
    }

    @Test
    fun staleGenerationDoesNotDeliverAfterNewerRefreshStarts() {
        val deferred = DeferringExecutorService()
        var received: LauncherRenderModel? = null

        val m = manager(executor = deferred, onStateReady = { received = it })
        m.refreshAsync() // gen=1 assembly queued

        // Run gen=1 assembly — builds model and posts to mainHandler.
        deferred.runAll()

        // Before the mainHandler fires, start gen=2 — increments generation.
        m.refreshAsync()
        deferred.runAll()

        // Drain the main looper — gen=1's posted callback sees generation != 1 and skips;
        // gen=2's callback fires normally.
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull("gen=2 must still deliver", received)
    }

    // ---- error handling ----

    @Test
    fun exceptionInFactoryDoesNotCrashExecutorThread() {
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
        val m = manager(factory = throwingFactory)

        m.refreshAsync() // must not propagate exception
        shadowOf(Looper.getMainLooper()).idle()
        // No assertion needed — absence of exception is the pass condition.
    }

    @Test
    fun onStateReadyNotCalledWhenFactoryThrows() {
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
        val m = manager(factory = throwingFactory, onStateReady = { received = it })

        m.refreshAsync()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("factory failure must not deliver a model", received)
    }

    @Test
    fun rejectedExecutionOnShutdownDoesNotCrash() {
        val m = manager(executor = ShutdownExecutorService())
        m.refreshAsync() // must not throw
    }
}

// ---------------------------------------------------------------------------
// Test executor implementations
// ---------------------------------------------------------------------------

/** Runs every submitted task immediately on the calling thread. */
private class SynchronousExecutorService : AbstractExecutorService() {
    private var shutdown = false

    override fun execute(command: Runnable) {
        if (shutdown) throw RejectedExecutionException("shut down")
        command.run()
    }

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        val f = FutureTask(task)
        execute(f)
        return f
    }

    override fun shutdown() { shutdown = true }
    override fun shutdownNow(): List<Runnable> = emptyList<Runnable>().also { shutdown = true }
    override fun isShutdown() = shutdown
    override fun isTerminated() = shutdown
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

/**
 * Runs submit() tasks immediately (so Future.get() resolves), but queues execute() tasks
 * to be run only when [runAll] is called. This lets tests interleave multiple refreshAsync()
 * calls before the final model-assembly step runs.
 */
private class DeferringExecutorService : AbstractExecutorService() {
    private val queue = mutableListOf<Runnable>()
    private var shutdown = false

    override fun execute(command: Runnable) {
        if (shutdown) throw RejectedExecutionException()
        queue.add(command)
    }

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        val f = FutureTask(task)
        f.run() // resolve immediately so .get() works inside the execute lambda
        return f
    }

    fun runAll() {
        val snapshot = queue.toList()
        queue.clear()
        snapshot.forEach { it.run() }
    }

    override fun shutdown() { shutdown = true }
    override fun shutdownNow(): List<Runnable> = emptyList<Runnable>().also { shutdown = true }
    override fun isShutdown() = shutdown
    override fun isTerminated() = shutdown
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

/** Always throws RejectedExecutionException to simulate a shut-down executor. */
private class ShutdownExecutorService : AbstractExecutorService() {
    override fun execute(command: Runnable) = throw RejectedExecutionException("shut down")
    override fun <T : Any?> submit(task: Callable<T>): Future<T> = throw RejectedExecutionException()
    override fun shutdown() = Unit
    override fun shutdownNow(): List<Runnable> = emptyList()
    override fun isShutdown() = true
    override fun isTerminated() = true
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}
