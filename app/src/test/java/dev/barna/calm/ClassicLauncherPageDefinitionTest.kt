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

    @Test
    fun withoutItemRemovesOnlyMatchingItem() {
        val app = ClassicGridItem.app("com.example", x = 0, y = 0)
        val widget = ClassicGridItem.widget(appWidgetId = 42, x = 0, y = 1)
        val page = ClassicLauncherPageDefinition.default().copy(items = listOf(app, widget))

        val updated = page.withoutItem(app.id)

        assertEquals(listOf(widget), updated.items)
    }

    @Test
    fun withItemAtNextFreeAreaPreservesMovedItemIdentityAndSpan() {
        val target = ClassicLauncherPageDefinition.default().withApp("com.a")
        val widget = ClassicGridItem.widget(appWidgetId = 42, x = 3, y = 5, width = 4, height = 2)

        val updated = target?.withItemAtNextFreeArea(widget)
        val moved = updated?.items?.single { it.id == widget.id }

        assertEquals(widget.id, moved?.id)
        assertEquals(widget.type, moved?.type)
        assertEquals(widget.target, moved?.target)
        assertEquals(0, moved?.x)
        assertEquals(1, moved?.y)
        assertEquals(4, moved?.width)
        assertEquals(2, moved?.height)
    }

    @Test
    fun withItemAtNextFreeAreaReturnsNullForDuplicateItem() {
        val app = ClassicGridItem.app("com.example", x = 0, y = 0)
        val page = ClassicLauncherPageDefinition.default().copy(items = listOf(app))

        assertNull(page.withItemAtNextFreeArea(app.copy(x = 1, y = 0)))
    }

    @Test
    fun withResizedItemPreservesPositionWhenAreaIsClear() {
        val app = ClassicGridItem.app("com.example", x = 1, y = 1)
        val page = ClassicLauncherPageDefinition.default().copy(items = listOf(app))

        val resized = page.withResizedItem(app.id, width = 2, height = 2)?.items?.single()

        assertEquals(app.id, resized?.id)
        assertEquals(1, resized?.x)
        assertEquals(1, resized?.y)
        assertEquals(2, resized?.width)
        assertEquals(2, resized?.height)
    }

    @Test
    fun withResizedItemMovesToFirstFreeAreaWhenCurrentPositionCannotFit() {
        val app = ClassicGridItem.app("com.example", x = ClassicGridItem.GRID_COLUMNS - 1, y = 0)
        val page = ClassicLauncherPageDefinition.default().copy(items = listOf(app))

        val resized = page.withResizedItem(app.id, width = 2, height = 1)?.items?.single()

        assertEquals(0, resized?.x)
        assertEquals(0, resized?.y)
        assertEquals(2, resized?.width)
        assertEquals(1, resized?.height)
    }

    @Test
    fun withResizedItemReturnsNullWhenNoAreaFits() {
        val app = ClassicGridItem.app("com.example", x = 0, y = 0)
        val blockers = (0 until ClassicGridItem.DEFAULT_GRID_ROWS).flatMap { row ->
            (0 until ClassicGridItem.GRID_COLUMNS).mapNotNull { column ->
                if (row == 0 && column == 0) null else ClassicGridItem.app("com.$row.$column", column, row)
            }
        }
        val page = ClassicLauncherPageDefinition.default().copy(items = listOf(app) + blockers)

        assertNull(page.withResizedItem(app.id, width = 2, height = 2))
    }
}
