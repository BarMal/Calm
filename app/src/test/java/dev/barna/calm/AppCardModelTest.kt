package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCardModelTest {
    private val factory = AppCardModelFactory()

    @Test
    fun appCardTextDoesNotIncludePinnedState() {
        val app = app("maps.pkg", "Maps")

        val model = factory.create(app, setOf("maps.pkg"))

        assertEquals("Maps", model.text)
        assertTrue(model.isPinned)
    }

    @Test
    fun workAppTextHasBriefcasePrefix() {
        val app = app("calendar.pkg", "Calendar", isWorkProfile = true)

        val model = factory.create(app, emptySet())

        assertEquals("💼 Calendar", model.text)
        assertFalse(model.isPinned)
    }

    @Test
    fun appCardUsesHueColour() {
        val app = app("camera.pkg", "Camera", hueColor = -5517841)

        val model = factory.create(app, emptySet())

        assertEquals(-5517841, model.hueColor)
    }

    private fun app(
        packageName: String,
        label: String,
        hueColor: Int = -15584170,
        isWorkProfile: Boolean = false,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = hueColor,
            isWorkProfile = isWorkProfile,
        )
    }
}
