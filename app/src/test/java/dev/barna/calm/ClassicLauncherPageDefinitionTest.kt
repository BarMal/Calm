package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassicLauncherPageDefinitionTest {
    @Test
    fun encodeDecodePreservesItems() {
        val page = ClassicLauncherPageDefinition(
            id = "classic-1",
            title = "Home",
            enabled = false,
            items = listOf(ClassicGridItem.app("com.example", x = 1, y = 2)),
        )

        val decoded = ClassicLauncherPageDefinition.decode(page.encode())

        assertEquals(page, decoded)
    }

    @Test
    fun withAppPlacesAppsInFirstFreeCells() {
        val page = ClassicLauncherPageDefinition.default()

        val updated = page.withApp("com.a")?.withApp("com.b")

        assertEquals(listOf(0 to 0, 1 to 0), updated?.items?.map { it.x to it.y })
        assertTrue(updated?.containsApp("com.a") == true)
        assertTrue(updated?.containsApp("com.b") == true)
    }

    @Test
    fun withAppSkipsDuplicate() {
        val page = ClassicLauncherPageDefinition.default().withApp("com.a")

        val updated = page?.withApp("com.a")

        assertSame(page, updated)
        assertEquals(1, updated?.items?.size)
    }

    @Test
    fun withAppReturnsNullWhenGridIsFull() {
        val full = (0 until ClassicGridItem.DEFAULT_GRID_ROWS).fold(ClassicLauncherPageDefinition.default()) { page, row ->
            (0 until ClassicGridItem.GRID_COLUMNS).fold(page) { current, column ->
                current.copy(items = current.items + ClassicGridItem.app("com.$row.$column", column, row))
            }
        }

        assertNull(full.withApp("com.extra"))
    }

    @Test
    fun withWidgetPlacesWidgetInFirstFreeArea() {
        val page = ClassicLauncherPageDefinition.default()
            .withApp("com.a")
            ?.withWidget(appWidgetId = 42)

        val widget = page?.items?.single { it.type == ClassicGridItemType.WIDGET }
        assertEquals(0, widget?.x)
        assertEquals(1, widget?.y)
        assertEquals(ClassicGridItem.GRID_COLUMNS, widget?.width)
        assertEquals(2, widget?.height)
        assertTrue(page?.containsWidget(42) == true)
    }

    @Test
    fun withWidgetSkipsDuplicate() {
        val page = ClassicLauncherPageDefinition.default().withWidget(42)

        val updated = page?.withWidget(42)

        assertSame(page, updated)
        assertEquals(1, updated?.items?.size)
    }

    @Test
    fun withWidgetReturnsNullWhenNoSpanFits() {
        val page = ClassicLauncherPageDefinition.default().copy(
            items = (0 until ClassicGridItem.DEFAULT_GRID_ROWS).map { row ->
                ClassicGridItem.app("com.$row", x = 0, y = row)
            },
        )

        assertNull(page.withWidget(42))
    }
}
