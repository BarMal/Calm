package dev.barna.calm

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
class AppLibrarySnapshotCacheTest {
    @Test
    fun loadReturnsPersistedSnapshotBeforeCallingFreshLoader() {
        val store = FakeAppLibrarySnapshotStore(AppLibrarySnapshot.from(listOf(app("cached.pkg", "Cached"))))
        var loadCalls = 0
        val cache = AppLibrarySnapshotCache(store) {
            loadCalls++
            listOf(app("fresh.pkg", "Fresh"))
        }

        val apps = cache.load()

        assertEquals(listOf("cached.pkg"), apps.map { it.packageName })
        assertEquals(0, loadCalls)
        assertTrue(cache.shouldRefreshPersistedSnapshot())
    }

    @Test
    fun loadFallsBackToFreshAppsAndPersistsSnapshot() {
        val store = FakeAppLibrarySnapshotStore()
        var loadCalls = 0
        val cache = AppLibrarySnapshotCache(store) {
            loadCalls++
            listOf(app("fresh.pkg", "Fresh"))
        }

        val apps = cache.load()
        val second = cache.load()

        assertEquals(listOf("fresh.pkg"), apps.map { it.packageName })
        assertEquals(apps, second)
        assertEquals(1, loadCalls)
        assertNotNull(store.snapshot)
        assertFalse(cache.shouldRefreshPersistedSnapshot())
    }

    @Test
    fun refreshReportsChangeAfterPersistedSnapshotWasLoaded() {
        val store = FakeAppLibrarySnapshotStore(AppLibrarySnapshot.from(listOf(app("cached.pkg", "Cached"))))
        val cache = AppLibrarySnapshotCache(store) {
            listOf(app("fresh.pkg", "Fresh"))
        }

        cache.load()
        val result = cache.refresh()

        assertTrue(result.changed)
        assertEquals(listOf("fresh.pkg"), result.apps.map { it.packageName })
        assertEquals(listOf("fresh.pkg"), store.snapshot?.apps?.map { it.packageName })
        assertFalse(cache.shouldRefreshPersistedSnapshot())
    }

    @Test
    fun refreshDoesNotReportChangeWhenFreshSnapshotMatches() {
        val app = app("same.pkg", "Same")
        val store = FakeAppLibrarySnapshotStore(AppLibrarySnapshot.from(listOf(app)))
        val cache = AppLibrarySnapshotCache(store) { listOf(app) }

        cache.load()
        val result = cache.refresh()

        assertFalse(result.changed)
    }

    @Test
    fun invalidateClearsMemoryAndPersistedSnapshot() {
        val store = FakeAppLibrarySnapshotStore(AppLibrarySnapshot.from(listOf(app("cached.pkg", "Cached"))))
        var freshPackage = "first.pkg"
        val cache = AppLibrarySnapshotCache(store) {
            listOf(app(freshPackage, "Fresh"))
        }

        cache.load()
        cache.invalidate()
        freshPackage = "second.pkg"
        val apps = cache.load()

        assertEquals(listOf("second.pkg"), apps.map { it.packageName })
        assertEquals(listOf("second.pkg"), store.snapshot?.apps?.map { it.packageName })
    }

    @Test
    fun refreshAsyncCoalescesDuplicatePendingRefreshes() {
        val executor = AppCacheQueuedExecutor()
        val cache = AppLibrarySnapshotCache(FakeAppLibrarySnapshotStore()) {
            listOf(app("fresh.pkg", "Fresh"))
        }
        var changedCallbacks = 0

        cache.refreshAsync(executor) { changedCallbacks++ }
        cache.refreshAsync(executor) { changedCallbacks++ }

        assertEquals(1, executor.tasks.size)
        executor.runNext()
        assertEquals(0, changedCallbacks)
    }

    @Test
    fun codecRoundTripsComponentAndProfileMetadata() {
        val source = AppLibrarySnapshot.from(
            listOf(
                app(
                    packageName = "work.pkg",
                    label = "Work App",
                    identityKey = AppIdentity.launcherKey("work.pkg", "MainActivity", 10),
                    notificationSourceKey = AppIdentity.notificationKey("work.pkg", 10),
                    componentName = ComponentName("work.pkg", "MainActivity"),
                    profileLabel = "Work",
                    isWorkProfile = true,
                ),
            ),
        )

        val decoded = AppLibrarySnapshotCodec.decode(AppLibrarySnapshotCodec.encode(source))

        assertEquals(source.fingerprint, decoded?.fingerprint)
        assertEquals(source.apps, decoded?.apps)
    }

    private fun app(
        packageName: String,
        label: String,
        identityKey: String = AppIdentity.packageOnly(packageName).key,
        notificationSourceKey: String = AppIdentity.packageOnly(packageName).notificationSourceKey,
        componentName: ComponentName? = ComponentName(packageName, "MainActivity"),
        profileLabel: String = "Personal",
        isWorkProfile: Boolean = false,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = 0xff123456.toInt(),
            identityKey = identityKey,
            notificationSourceKey = notificationSourceKey,
            componentName = componentName,
            profileLabel = profileLabel,
            isWorkProfile = isWorkProfile,
        )
    }
}

private class FakeAppLibrarySnapshotStore(
    var snapshot: AppLibrarySnapshot? = null,
) : AppLibrarySnapshotStore {
    override fun load(): AppLibrarySnapshot? {
        return snapshot
    }

    override fun save(snapshot: AppLibrarySnapshot) {
        this.snapshot = snapshot
    }

    override fun clear() {
        snapshot = null
    }
}

private class AppCacheQueuedExecutor : Executor {
    val tasks = ArrayList<Runnable>()

    override fun execute(command: Runnable) {
        tasks.add(command)
    }

    fun runNext() {
        tasks.removeAt(0).run()
    }
}
