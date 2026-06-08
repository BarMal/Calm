package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppQuickScrollIndexTest {
    private val index = AppQuickScrollIndex()

    @Test
    fun createIncludesAllAlphabetLettersPlusHash() {
        val model = index.create(
            listOf(
                app("Alpha"),
                app("Another"),
                app("Beta"),
                app("camera"),
                app("9 Notes"),
            ),
        )

        assertEquals(27, model.targets.size)
        assertEquals('A', model.targets.first().label[0])
        assertEquals('#', model.targets.last().label[0])
    }

    @Test
    fun createMarksOnlyLettersWithAppsAsActive() {
        val model = index.create(
            listOf(
                app("Alpha"),
                app("Another"),
                app("Beta"),
                app("camera"),
                app("9 Notes"),
            ),
        )

        val activeLabels = model.targets.filter { it.active }.map { it.label }
        assertEquals(listOf("A", "B", "C", "#"), activeLabels)
        assertFalse(model.targets.first { it.label == "D" }.active)
        assertFalse(model.targets.first { it.label == "Z" }.active)
    }

    @Test
    fun createAssignsCardIndexOfFirstAppPerLetter() {
        val model = index.create(
            listOf(
                app("Alpha"),
                app("Another"),
                app("Beta"),
                app("camera"),
                app("9 Notes"),
            ),
        )

        assertEquals(0, model.targets.first { it.label == "A" }.cardIndex)
        assertEquals(2, model.targets.first { it.label == "B" }.cardIndex)
        assertEquals(3, model.targets.first { it.label == "C" }.cardIndex)
        assertEquals(4, model.targets.first { it.label == "#" }.cardIndex)
    }

    @Test
    fun targetAtReturnsActiveTargetDirectly() {
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("A", 0, active = true),
                AppQuickScrollTarget("M", 4, active = true),
                AppQuickScrollTarget("Z", 9, active = true),
            ),
        )

        assertEquals(AppQuickScrollTarget("A", 0, active = true), index.targetAt(model, railHeight = 90, y = 0f))
        assertEquals(AppQuickScrollTarget("M", 4, active = true), index.targetAt(model, railHeight = 90, y = 45f))
        assertEquals(AppQuickScrollTarget("Z", 9, active = true), index.targetAt(model, railHeight = 90, y = 120f))
    }

    @Test
    fun inactiveTargetFallsBackToPrecedingActiveTarget() {
        // "T" is inactive between "S" (active) and "U" (active). Should resolve to "S".
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("S", 10, active = true),
                AppQuickScrollTarget("T", 0, active = false),
                AppQuickScrollTarget("U", 15, active = true),
            ),
        )
        // y=50 out of 90 → index 1 → "T" (inactive) → falls back to "S"
        assertEquals(AppQuickScrollTarget("S", 10, active = true), index.targetAt(model, railHeight = 90, y = 50f))
    }

    @Test
    fun inactiveTargetWithNoPrecedingActiveFallsForwardToNearestActive() {
        // "A" and "B" are inactive, "C" is the first active letter.
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("A", 0, active = false),
                AppQuickScrollTarget("B", 0, active = false),
                AppQuickScrollTarget("C", 5, active = true),
            ),
        )
        // y=0 → index 0 → "A" (inactive) → no preceding active → falls forward to "C"
        assertEquals(AppQuickScrollTarget("C", 5, active = true), index.targetAt(model, railHeight = 90, y = 0f))
    }

    @Test
    fun allInactiveTargetsReturnNull() {
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("A", 0, active = false),
                AppQuickScrollTarget("B", 0, active = false),
            ),
        )
        assertNull(index.targetAt(model, railHeight = 90, y = 0f))
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
