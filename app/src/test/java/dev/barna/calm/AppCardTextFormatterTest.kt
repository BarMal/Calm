package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCardTextFormatterTest {
    private val formatter = AppCardTextFormatter()

    @Test
    fun personalAppKeepsOriginalName() {
        val app = AppEntry(packageName = "pkg", label = "Camera", hueColor = 0xff123456.toInt())

        assertEquals("Camera", formatter.cardText(app))
    }

    @Test
    fun workAppHasBriefcasePrefixNotWorkSuffix() {
        // Old behaviour appended "\nWork" on a second line, which wasted vertical space
        // and duplicated what the Work page title already communicates.
        // New behaviour: briefcase emoji prefix on the same line as the label.
        val app = AppEntry(packageName = "pkg", label = "Calendar", hueColor = 0xff123456.toInt(), isWorkProfile = true)
        val text = formatter.cardText(app)

        assertTrue("Expected briefcase prefix 💼", text.startsWith("💼"))
        assertTrue("Expected label in text", text.contains("Calendar"))
        assertFalse("Should not contain literal Work text", text.contains("Work"))
        assertFalse("Should not contain newline", text.contains('\n'))
    }

    @Test
    fun pinnedStateIsNotAddedToVisibleName() {
        val app = AppEntry(packageName = "pkg", label = "Maps", hueColor = 0xff123456.toInt())

        assertFalse(formatter.cardText(app).contains("Pinned"))
    }
}
