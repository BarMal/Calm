package dev.barna.calm

import android.app.Activity
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRepositoryTest {
    @Test
    fun requestCalendarAccessUsesInjectedPermissionRequester() {
        var requested = false
        val repository = CalendarRepository(Activity()) {
            requested = true
        }

        repository.requestCalendarAccess()

        assertTrue(requested)
    }
}
