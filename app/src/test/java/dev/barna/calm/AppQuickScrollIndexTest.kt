package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppQuickScrollIndexTest {
    private val index = AppQuickScrollIndex()

    @Test
    fun createsOneTargetPerLeadingLetter() {
        val model = index.create(
            listOf(
                app("Alpha"),
                app("Another"),
                app("Beta"),
                app("camera"),
                app("9 Notes"),
            ),
        )

        assertEquals(
            listOf(
                AppQuickScrollTarget("A", 0),
                AppQuickScrollTarget("B", 2),
                AppQuickScrollTarget("C", 3),
                AppQuickScrollTarget("#", 4),
            ),
            model.targets,
        )
    }

    @Test
    fun mapsTouchPositionToNearestTargetBand() {
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("A", 0),
                AppQuickScrollTarget("M", 4),
                AppQuickScrollTarget("Z", 9),
            ),
        )

        assertEquals(AppQuickScrollTarget("A", 0), index.targetAt(model, railHeight = 90, y = 0f))
        assertEquals(AppQuickScrollTarget("M", 4), index.targetAt(model, railHeight = 90, y = 45f))
        assertEquals(AppQuickScrollTarget("Z", 9), index.targetAt(model, railHeight = 90, y = 120f))
    }

    @Test
    fun emptyRailHasNoTarget() {
        assertNull(index.targetAt(AppQuickScrollModel(emptyList()), railHeight = 100, y = 10f))
        assertNull(index.targetAt(AppQuickScrollModel(listOf(AppQuickScrollTarget("A", 0))), railHeight = 0, y = 10f))
    }

    private fun app(label: String): AppEntry {
        return AppEntry(packageName = label.lowercase(), label = label, hueColor = 0xff123456.toInt())
    }
}
