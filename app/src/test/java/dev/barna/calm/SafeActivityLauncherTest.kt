package dev.barna.calm

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafeActivityLauncherTest {
    @Test
    fun startReturnsFalseWhenActivityLaunchThrows() {
        val context = ThrowingContext(ApplicationProvider.getApplicationContext(), failCount = Int.MAX_VALUE)

        val launched = SafeActivityLauncher.start(context, Intent(Intent.ACTION_VIEW))

        assertFalse(launched)
        assertEquals(1, context.starts)
    }

    @Test
    fun startAnyFallsBackToNextIntent() {
        val context = ThrowingContext(ApplicationProvider.getApplicationContext(), failCount = 1)

        val launched = SafeActivityLauncher.startAnyOrToast(
            context,
            listOf(Intent("first"), Intent("second")),
            "Unavailable",
        )

        assertTrue(launched)
        assertEquals(2, context.starts)
    }

    private class ThrowingContext(base: Context, private var failCount: Int) : ContextWrapper(base) {
        var starts = 0
            private set

        override fun startActivity(intent: Intent?) {
            starts++
            if (failCount > 0) {
                failCount--
                throw SecurityException("blocked")
            }
        }
    }
}
