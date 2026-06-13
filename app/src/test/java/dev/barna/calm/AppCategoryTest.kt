package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryTest {

    @Test
    fun defaultsIncludeRequestedCategorySeeds() {
        val ids = AppCategory.DEFAULTS.map { it.id }

        assertTrue("communications" in ids)
        assertTrue("finance" in ids)
        assertTrue("shopping" in ids)
        assertTrue("system" in ids)
        assertTrue("customisation" in ids)
    }

    @Test
    fun customCategoryNormalizesTitleIntoStableId() {
        val category = AppCategory.custom(" Finance & Budgeting ")

        assertEquals("finance-and-budgeting", category.id)
        assertEquals("Finance & Budgeting", category.title)
        assertTrue(category.enabled)
    }

    @Test
    fun categoryRoundTripsThroughEncoding() {
        val category = AppCategory("finance", "Money | Budgeting", enabled = false)

        assertEquals(category, AppCategory.decode(category.encode()))
    }

    @Test
    fun categoryListRoundTripsDistinctById() {
        val categories = listOf(
            AppCategory("finance", "Finance"),
            AppCategory("finance", "Money"),
            AppCategory("shopping", "Shopping", enabled = false),
        )

        val decoded = AppCategory.decodeList(AppCategory.encodeList(categories))

        assertEquals(
            listOf(
                AppCategory("finance", "Finance"),
                AppCategory("shopping", "Shopping", enabled = false),
            ),
            decoded,
        )
    }

    @Test
    fun blankStoredListFallsBackToDefaults() {
        assertEquals(AppCategory.DEFAULTS, AppCategory.decodeList(null))
        assertEquals(AppCategory.DEFAULTS, AppCategory.decodeList(""))
    }

    @Test
    fun malformedCategoryLineIsIgnored() {
        val encoded = listOf(
            "bad",
            AppCategory("media", "Media").encode(),
            "Invalid Id|Title|true",
        ).joinToString("\n")

        assertEquals(listOf(AppCategory("media", "Media")), AppCategory.decodeList(encoded))
        assertNull(AppCategory.decode("finance|Finance|not-bool"))
    }

    @Test
    fun constructorRejectsBlankAndUnnormalisedFields() {
        assertFails { AppCategory("", "Finance") }
        assertFails { AppCategory("finance", "") }
        assertFails { AppCategory("Finance", "Finance") }
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
