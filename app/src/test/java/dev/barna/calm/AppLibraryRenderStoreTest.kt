package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLibraryRenderStoreTest {
    @Test
    fun loadingEventKeepsExistingAppsRenderable() {
        val mail = app("mail.pkg", "Mail")
        val store = AppLibraryRenderStore(listOf(mail))

        val state = store.dispatch(AppLibraryRenderEvent.LoadingStarted)

        assertTrue(state.loading)
        assertEquals(listOf(mail), state.apps)
        assertEquals(state, store.stateFlow.value)
    }

    @Test
    fun upsertEventsAddAndUpdateAppsWithoutClearingThePage() {
        val mail = app("mail.pkg", "Mail")
        val updatedMail = app("mail.pkg", "Mail+", hueColor = 0xff654321.toInt())
        val camera = app("camera.pkg", "Camera")
        val store = AppLibraryRenderStore(listOf(mail))

        val state = store.dispatch(
            AppLibraryRenderEvent.AppsUpserted(
                apps = listOf(updatedMail, camera),
                orderedIds = listOf(camera.identityKey, updatedMail.identityKey),
            ),
        )

        assertTrue(state.loading)
        assertEquals(listOf(camera, updatedMail), state.apps)
    }

    @Test
    fun removeEventsDropMissingAppsAndKeepOrder() {
        val mail = app("mail.pkg", "Mail")
        val camera = app("camera.pkg", "Camera")
        val store = AppLibraryRenderStore(listOf(mail, camera))

        val state = store.dispatch(
            AppLibraryRenderEvent.AppsRemoved(
                identityKeys = setOf(mail.identityKey),
                orderedIds = listOf(camera.identityKey),
            ),
        )

        assertEquals(listOf(camera), state.apps)
    }

    @Test
    fun loadingFinishedTurnsOffLoadingAndAppliesFinalOrder() {
        val mail = app("mail.pkg", "Mail")
        val camera = app("camera.pkg", "Camera")
        val store = AppLibraryRenderStore(listOf(mail, camera))

        store.dispatch(AppLibraryRenderEvent.LoadingStarted)
        val state = store.dispatch(
            AppLibraryRenderEvent.LoadingFinished(
                orderedIds = listOf(camera.identityKey, mail.identityKey),
            ),
        )

        assertFalse(state.loading)
        assertEquals(listOf(camera, mail), state.apps)
    }

    @Test
    fun plannerBatchesChangedAppsAndEmitsFinalCompletion() {
        val oldMail = app("mail.pkg", "Mail")
        val newMail = app("mail.pkg", "Mail+")
        val camera = app("camera.pkg", "Camera")
        val clock = app("clock.pkg", "Clock")
        val planner = AppLibraryEventPlanner()

        val events = planner.plan(
            currentApps = listOf(oldMail),
            nextApps = listOf(newMail, camera, clock),
            batchSize = 2,
        )

        assertEquals(AppLibraryRenderEvent.LoadingStarted, events.first())
        assertEquals(AppLibraryRenderEvent.LoadingFinished::class, events.last()::class)
        val upserts = events.filterIsInstance<AppLibraryRenderEvent.AppsUpserted>()
        assertEquals(listOf(2, 1), upserts.map { it.apps.size })
        assertEquals(listOf(newMail, camera), upserts[0].apps)
        assertEquals(listOf(clock), upserts[1].apps)
    }

    @Test
    fun plannerEmitsRemovalForAppsMissingFromFreshState() {
        val mail = app("mail.pkg", "Mail")
        val camera = app("camera.pkg", "Camera")
        val planner = AppLibraryEventPlanner()

        val events = planner.plan(
            currentApps = listOf(mail, camera),
            nextApps = listOf(camera),
            batchSize = 24,
        )

        val removal = events.filterIsInstance<AppLibraryRenderEvent.AppsRemoved>().single()
        assertEquals(setOf(mail.identityKey), removal.identityKeys)
        assertEquals(listOf(camera.identityKey), removal.orderedIds)
    }

    private fun app(
        packageName: String,
        label: String,
        hueColor: Int = 0xff123456.toInt(),
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = hueColor,
        )
    }
}
