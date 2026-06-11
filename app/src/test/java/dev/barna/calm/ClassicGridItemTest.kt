package dev.barna.calm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClassicGridItemTest {
    @Test
    fun appCreatesStablePrivateId() {
        val first = ClassicGridItem.app("com.example/.Main", x = 1, y = 2)
        val second = ClassicGridItem.app("com.example/.Main", x = 3, y = 4)
        val other = ClassicGridItem.app("com.other/.Main", x = 1, y = 2)

        assertEquals(first.id, second.id)
        assertNotEquals(first.id, other.id)
        assertEquals(ClassicGridItemType.APP, first.type)
        assertEquals("com.example/.Main", first.target)
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val item = ClassicGridItem(
            id = "widget-1",
            type = ClassicGridItemType.WIDGET,
            target = "clock",
            x = 2,
            y = 3,
            width = 2,
            height = 1,
        )

        val decoded = ClassicGridItem.decode(item.encode())

        assertEquals(item, decoded)
    }

    @Test
    fun decodeClampsCoordinatesAndSizeMinimums() {
        val decoded = ClassicGridItem.decode(
            JSONObject()
                .put("id", "app-1")
                .put("target", "com.example")
                .put("x", -4)
                .put("y", -3)
                .put("width", 0)
                .put("height", 0),
        )

        assertEquals(0, decoded?.x)
        assertEquals(0, decoded?.y)
        assertEquals(1, decoded?.width)
        assertEquals(1, decoded?.height)
    }

    @Test
    fun decodeClampsItemsIntoGridBounds() {
        val decoded = ClassicGridItem.decode(
            JSONObject()
                .put("id", "widget-1")
                .put("type", "WIDGET")
                .put("target", "clock")
                .put("x", 99)
                .put("y", 99)
                .put("width", 99)
                .put("height", 99),
        )

        assertEquals(ClassicGridItem.GRID_COLUMNS - 1, decoded?.x)
        assertEquals(ClassicGridItem.DEFAULT_GRID_ROWS - 1, decoded?.y)
        assertEquals(1, decoded?.width)
        assertEquals(1, decoded?.height)
    }

    @Test
    fun decodeRejectsMissingIdentityFields() {
        assertNull(ClassicGridItem.decode(JSONObject().put("target", "com.example")))
        assertNull(ClassicGridItem.decode(JSONObject().put("id", "app-1")))
    }
}
