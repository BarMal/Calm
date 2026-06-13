package dev.barna.calm

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DockControllerTest {
    private lateinit var activity: MainActivity
    private lateinit var controller: DockController

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        controller = DockController(
            activity = activity,
            drawables = CalmDrawables(activity),
            resolveIcon = { null },
            openAppEntry = {},
            openNotification = {},
            openNotificationPage = {},
            showContextMenu = { _, _, _, _ -> },
            describeDock = { app, _ -> app.label },
            tapToOpenText = { "Tap to open" },
        )
    }

    @Test
    fun classicDockAppliesConfiguredSideMarginsAndItemSpacing() {
        val dock = controller.buildDock(
            apps = apps(),
            config = DockConfig(enabled = true, style = DockStyle.CLASSIC, horizontalPaddingDp = 24),
            chapters = emptyList(),
        ) as HorizontalScrollView
        val row = dock.getChildAt(0) as LinearLayout
        val itemLayout = row.getChildAt(0).layoutParams as LinearLayout.LayoutParams

        assertEquals(activity.dp(24), dock.paddingLeft)
        assertEquals(activity.dp(24), dock.paddingRight)
        assertEquals(activity.dp(12), itemLayout.marginStart)
        assertEquals(activity.dp(12), itemLayout.marginEnd)
    }

    @Test
    fun oneCellClassicDockItemsRenderIconOnly() {
        val dock = controller.buildDock(
            apps = apps(),
            config = DockConfig(enabled = true, style = DockStyle.CLASSIC, itemSpan = 1),
            chapters = emptyList(),
        ) as HorizontalScrollView
        val row = dock.getChildAt(0) as LinearLayout

        assertFalse(row.getChildAt(0).containsText("Alpha"))
    }

    @Test
    fun twoCellClassicDockItemsRenderLabelAndNotificationDetail() {
        val dock = controller.buildDock(
            apps = apps(),
            config = DockConfig(enabled = true, style = DockStyle.CLASSIC, itemSpan = 2),
            chapters = listOf(chapter("alpha")),
        ) as HorizontalScrollView
        val row = dock.getChildAt(0) as LinearLayout

        assertTrue(row.getChildAt(0).containsText("Alpha"))
        assertTrue(row.getChildAt(0).containsText("Newest body"))
    }

    @Test
    fun cardDockHorizontalSwipeCyclesCurrentAppsNotifications() {
        val dock = controller.buildDock(
            apps = apps(),
            config = DockConfig(enabled = true, style = DockStyle.CARD),
            chapters = listOf(chapter("alpha")),
        ) as FrameLayout

        assertTrue(dock.containsText("Newest body"))

        dock.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 60f, 80f, 0))
        dock.dispatchTouchEvent(MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, 0f, 80f, 0))
        dock.dispatchTouchEvent(MotionEvent.obtain(0L, 32L, MotionEvent.ACTION_UP, 0f, 80f, 0))

        assertTrue(dock.containsText("Older body"))
    }

    private fun apps(): List<AppEntry> = listOf(
        app("alpha", "Alpha"),
        app("beta", "Beta"),
        app("gamma", "Gamma"),
    )

    private fun app(identityKey: String, label: String): AppEntry {
        return AppEntry(
            packageName = "pkg.$identityKey",
            label = label,
            hueColor = 0,
            identityKey = identityKey,
        )
    }

    private fun chapter(identityKey: String): AppChapter {
        return AppChapter(
            packageName = "pkg.$identityKey",
            label = "Alpha",
            notifications = listOf(
                notification("Older", "Older body", postTime = 10),
                notification("Newest", "Newest body", postTime = 20),
            ),
            launchable = true,
            hueColor = 0,
            identityKey = "pkg.$identityKey",
            launcherIdentityKey = identityKey,
        )
    }

    private fun notification(
        title: String,
        text: String,
        postTime: Long,
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = title,
            packageName = "pkg.alpha",
            title = title,
            text = text,
            subText = "",
            conversationTitle = "",
            postTime = postTime,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }

    private fun View.containsText(value: String): Boolean {
        if (this is TextView && text.toString() == value) return true
        if (this !is ViewGroup) return false
        return (0 until childCount).any { index -> getChildAt(index).containsText(value) }
    }
}
