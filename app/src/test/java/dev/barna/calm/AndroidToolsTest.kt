package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidToolsTest {
    @Test
    fun friendlyPackageNameUsesReadableFinalSegment() {
        assertEquals("Outlook", friendlyPackageName("com.microsoft.office.outlook"))
        assertEquals("Youtube Music", friendlyPackageName("app.revanced.android.apps.youtube_music"))
        assertEquals("My App", friendlyPackageName("dev.barna.myApp"))
    }
}
