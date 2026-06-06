package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppCardTextFormatterTest {
    private val formatter = AppCardTextFormatter()

    @Test
    fun personalAppKeepsOriginalName() {
        val app = AppEntry(packageName = "pkg", label = "Camera", hueColor = 0xff123456.toInt())

        assertEquals("Camera", formatter.cardText(app))
    }

    @Test
    fun workAppKeepsNameAndProfileLine() {
        val app = AppEntry(packageName = "pkg", label = "Calendar", hueColor = 0xff123456.toInt(), isWorkProfile = true)

        assertEquals("Calendar\nWork", formatter.cardText(app))
    }

    @Test
    fun pinnedStateIsNotAddedToVisibleName() {
        val app = AppEntry(packageName = "pkg", label = "Maps", hueColor = 0xff123456.toInt())

        assertFalse(formatter.cardText(app).contains("Pinned"))
    }
}
