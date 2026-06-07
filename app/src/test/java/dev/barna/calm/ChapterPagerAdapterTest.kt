package dev.barna.calm

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChapterPagerAdapterTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun preloadReusesPageViewByPageKey() {
        var factoryCalls = 0
        val adapter = ChapterPagerAdapter(listOf(page("one"))) {
            factoryCalls++
            View(context)
        }

        val first = adapter.preload(0)
        val second = adapter.preload(0)

        assertSame(first, second)
        assertEquals(1, factoryCalls)
    }

    @Test
    fun bindMovesCachedViewFromOldHolderToNewHolder() {
        val adapter = ChapterPagerAdapter(listOf(page("one"))) { View(context) }
        val parent = FrameLayout(context)
        val firstHolder = adapter.onCreateViewHolder(parent, 0)
        val secondHolder = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(firstHolder, 0)
        val cachedView = firstHolder.container.getChildAt(0)
        adapter.onBindViewHolder(secondHolder, 0)

        assertEquals(0, firstHolder.container.childCount)
        assertEquals(1, secondHolder.container.childCount)
        assertSame(cachedView, secondHolder.container.getChildAt(0))
    }

    @Test
    fun recycleRemovesAttachedPageView() {
        val adapter = ChapterPagerAdapter(listOf(page("one"))) { View(context) }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0)
        adapter.onViewRecycled(holder)

        assertEquals(0, holder.container.childCount)
    }

    @Test
    fun preloadReturnsNullForInvalidPositions() {
        val adapter = ChapterPagerAdapter(listOf(page("one"))) { View(context) }

        assertNull(adapter.preload(-1))
        assertNull(adapter.preload(1))
    }

    private fun page(key: String): ChapterPage {
        val page = ChapterPage.overview(key)
        assertTrue(page.key == key)
        return page
    }
}
