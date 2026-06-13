package dev.barna.calm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ClassicPageCoordinatorTest {
    private lateinit var context: Context
    private lateinit var settings: LauncherSettings
    private lateinit var deletedWidgets: MutableList<ClassicGridItem>
    private lateinit var selectedPages: MutableList<String>
    private lateinit var toasts: MutableList<String>
    private var renderCount = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_classic_page_coordinator", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = LauncherSettings(prefs)
        settings.setClassicPagesEnabled(true)
        deletedWidgets = mutableListOf()
        selectedPages = mutableListOf()
        toasts = mutableListOf()
        renderCount = 0
    }

    @Test
    fun addClassicPageSelectsPageAndEntersEditMode() {
        val coordinator = coordinator()

        coordinator.addClassicPage()

        val page = settings.classicPages().last()
        assertEquals(listOf(page.key), selectedPages)
        assertTrue(coordinator.isClassicPageEditing(page))
        assertEquals(listOf("Added ${page.title}"), toasts)
        assertEquals(1, renderCount)
    }

    @Test
    fun disablingEditModeClearsPendingPlacement() {
        val coordinator = coordinator()
        val page = settings.classicPages().first()

        coordinator.beginItemPlacement(page, "app:one")
        coordinator.setClassicPageEditing(page, false)

        assertFalse(coordinator.isClassicPageEditing(page))
        assertNull(coordinator.pendingPlacementItemId)
        assertEquals(1, renderCount)
    }

    @Test
    fun finishItemPlacementOnlyClearsMatchingItem() {
        val coordinator = coordinator()
        val page = settings.classicPages().first()

        coordinator.beginItemPlacement(page, "app:one")
        coordinator.finishItemPlacement("app:two")
        assertEquals("app:one", coordinator.pendingPlacementItemId)

        coordinator.finishItemPlacement("app:one")
        assertNull(coordinator.pendingPlacementItemId)
    }

    @Test
    fun removeClassicPageDeletesWidgetsAndClearsPlacementState() {
        val coordinator = coordinator()
        val page = settings.classicPages().first()
        settings.addWidgetToClassicPage(page.id, appWidgetId = 42, width = 1, height = 1)
        val pageWithWidget = settings.classicPages().first { it.id == page.id }

        coordinator.beginItemPlacement(pageWithWidget, "widget:42")
        coordinator.removeClassicPage(pageWithWidget)

        assertEquals(listOf(ClassicGridItem.widget(42, x = 0, y = 0, width = 1, height = 1)), deletedWidgets)
        assertFalse(coordinator.isClassicPageEditing(pageWithWidget))
        assertNull(coordinator.pendingPlacementItemId)
        assertEquals(listOf("Removed ${pageWithWidget.title}"), toasts)
        assertEquals(1, renderCount)
    }

    @Test
    fun resetWidgetSizeUsesWidgetDefaultSpan() {
        val coordinator = coordinator(defaultWidgetSpan = { 2 to 2 })
        val page = settings.classicPages().first()
        settings.addWidgetToClassicPage(page.id, appWidgetId = 42, width = 1, height = 1)
        val widget = settings.classicPages().first().items.single()

        coordinator.resetClassicGridItemSize(page, widget)

        val resized = settings.classicPages().first().items.single()
        assertEquals(2, resized.width)
        assertEquals(2, resized.height)
        assertEquals(listOf("Resized"), toasts)
        assertEquals(1, renderCount)
    }

    private fun coordinator(
        defaultWidgetSpan: (Int) -> Pair<Int, Int>? = { null },
    ) = ClassicPageCoordinator(
        settings = settings,
        selectPage = selectedPages::add,
        render = { renderCount++ },
        stringResource = ::stringResource,
        showToast = toasts::add,
    ).also { coordinator ->
        coordinator.setWidgetCallbacks(
            deleteWidget = deletedWidgets::add,
            defaultWidgetSpan = defaultWidgetSpan,
        )
    }

    private fun stringResource(resId: Int, args: Array<out Any>): String {
        return when (resId) {
            R.string.toast_classic_page_added -> "Added ${args[0]}"
            R.string.toast_page_removed -> "Removed ${args[0]}"
            R.string.toast_resized -> "Resized"
            R.string.static_item_clock -> "Clock"
            R.string.static_item_search -> "Search"
            else -> error("Unexpected string resource $resId")
        }
    }
}
