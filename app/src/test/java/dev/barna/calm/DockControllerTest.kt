package dev.barna.calm

import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
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
}
