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
    fun widgetCreatesStableHostIdTarget() {
        val item = ClassicGridItem.widget(appWidgetId = 42, x = 0, y = 3, width = 4, height = 2)

        assertEquals("widget:42", item.id)
        assertEquals(ClassicGridItemType.WIDGET, item.type)
        assertEquals("42", item.target)
        assertEquals(4, item.width)
        assertEquals(2, item.height)
    }

    @Test
    fun staticCreatesStableTargetItem() {
        val item = ClassicGridItem.static(ClassicStaticItem.CLOCK, x = 0, y = 2, width = 4, height = 1)

        assertEquals("static:clock", item.id)
        assertEquals(ClassicGridItemType.STATIC, item.type)
        assertEquals("CLOCK", item.target)
        assertEquals(4, item.width)
        assertEquals(1, item.height)
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
    fun decodePreservesCoordinatesForConfigurableGrid() {
        val decoded = ClassicGridItem.decode(
            JSONObject()
                .put("id", "widget-1")
                .put("type", "WIDGET")
                .put("target", "clock")
                .put("x", 8)
                .put("y", 20)
                .put("width", 9)
                .put("height", 21),
        )

        assertEquals(8, decoded?.x)
        assertEquals(20, decoded?.y)
        assertEquals(9, decoded?.width)
        assertEquals(21, decoded?.height)
    }

    @Test
    fun decodeRejectsMissingIdentityFields() {
        assertNull(ClassicGridItem.decode(JSONObject().put("target", "com.example")))
        assertNull(ClassicGridItem.decode(JSONObject().put("id", "app-1")))
    }
}
