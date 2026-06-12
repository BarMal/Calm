package dev.barna.calm

import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppIconRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun shutdownClearsWorkAndStopsHueExecutor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val executor = TrackingExecutorService()
        val repository = AppIconRepository(
            cacheDir = temporaryFolder.newFolder(),
            launcherApps = null,
            packageManager = context.packageManager,
            settings = LauncherSettings(context),
            hueExecutor = executor,
        )

        repository.shutdown()

        assertTrue(executor.shutdownNowCalled)
        assertTrue(executor.isShutdown)
    }

    @Test
    fun rejectedHueWorkDoesNotLeaveKeyPending() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val executor = RejectingExecutorService()
        val repository = AppIconRepository(
            cacheDir = temporaryFolder.newFolder(),
            launcherApps = null,
            packageManager = context.packageManager,
            settings = LauncherSettings(context),
            hueExecutor = executor,
        )

        assertEquals(0, repository.resolveAppHue("identity", "missing.package", null))
        assertEquals(0, repository.resolveAppHue("identity", "missing.package", null))

        assertEquals(2, executor.attempts)
    }

    private class TrackingExecutorService : AbstractExecutorService() {
        var shutdownNowCalled = false
            private set
        private var shutdown = false

        override fun execute(command: Runnable) = Unit
        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdownNowCalled = true
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = shutdown
    }

    private class RejectingExecutorService : AbstractExecutorService() {
        var attempts = 0
            private set
        private var shutdown = false

        override fun execute(command: Runnable) {
            attempts++
            throw RejectedExecutionException("closed")
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = shutdown
    }
}
