package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun inactiveLetterGetsInterpolatedCardIndexBetweenNeighbours() {
        // 10 A-apps then 15 Z-apps. B through Y are inactive and should get
        // indices interpolated between A(cardIndex=0) and Z(cardIndex=10).
        val apps = (0 until 10).map { i -> app("Alpha$i") } + (0 until 15).map { i -> app("Zap$i") }
        val model = index.create(apps)

        val targetA = model.targets.first { it.label == "A" }
        val targetM = model.targets.first { it.label == "M" }  // midpoint, inactive
        val targetZ = model.targets.first { it.label == "Z" }

        assertEquals(0, targetA.cardIndex)
        assertEquals(10, targetZ.cardIndex)
        // M is at alphabet position 12 out of 26; interpolated index should be between A and Z.
        assertTrue("M.cardIndex should be > 0", targetM.cardIndex > 0)
        assertTrue("M.cardIndex should be < 10", targetM.cardIndex < 10)
        assertFalse(targetM.active)
    }

    @Test
    fun inactiveLetterBeforeAllActiveLettersSnapsToFirstActive() {
        // All apps start at C and D; letters A and B have no apps.
        val model = index.create(listOf(app("Charlie"), app("Delta")))

        val targetA = model.targets.first { it.label == "A" }
        val targetB = model.targets.first { it.label == "B" }
        val targetC = model.targets.first { it.label == "C" }

        assertEquals(targetC.cardIndex, targetA.cardIndex)
        assertEquals(targetC.cardIndex, targetB.cardIndex)
    }

    @Test
    fun inactiveLetterAfterAllActiveLettersSnapsToLastActive() {
        // All apps start at A and B; letters C onwards have no apps.
        val model = index.create(listOf(app("Alpha"), app("Beta")))

        val targetB = model.targets.first { it.label == "B" }
        val targetZ = model.targets.first { it.label == "Z" }

        assertEquals(targetB.cardIndex, targetZ.cardIndex)
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
    fun targetAtReturnsInactiveTargetDirectlyEnablingProportionalScroll() {
        // With interpolated card indices, inactive targets are returned as-is so
        // slow drag through inactive letter zones actually scrolls the list.
        val model = AppQuickScrollModel(
            listOf(
                AppQuickScrollTarget("S", 10, active = true),
                AppQuickScrollTarget("T", 12, active = false),  // interpolated index
                AppQuickScrollTarget("U", 15, active = true),
            ),
        )
        // y=50 out of 90 → index 1 → returns "T" with its card index (not "S")
        assertEquals(AppQuickScrollTarget("T", 12, active = false), index.targetAt(model, railHeight = 90, y = 50f))
    }

    @Test
    fun slowDragThroughInactiveZoneProducesNonDecreasingCardIndices() {
        // 15 A-apps then 10 Z-apps. Letters B through Y are inactive.
        // Dragging from top to bottom should yield non-decreasing card indices,
        // proving that slow navigation actually scrolls in the correct direction.
        val apps = (0 until 15).map { i -> app("Alpha$i") } + (0 until 10).map { i -> app("Zap$i") }
        val model = index.create(apps)

        val railHeight = 270
        var previousCardIndex = -1
        for (y in 0..railHeight step 10) {
            val target = index.targetAt(model, railHeight = railHeight, y = y.toFloat())
            assertNotNull("Target should not be null at y=$y", target)
            assertTrue(
                "Card index should be non-decreasing during downward drag (y=$y, prev=$previousCardIndex, curr=${target!!.cardIndex})",
                target.cardIndex >= previousCardIndex,
            )
            previousCardIndex = target.cardIndex
        }
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
