package dev.barna.calm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NotificationChapterRepositoryTest {
    @Test
    fun openAppReturnsFalseWhenFallbackLaunchThrows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageName = "com.example.unavailable"
        registerLauncherActivity(context, packageName)
        val settings = LauncherSettings(context.getSharedPreferences("notification_repository_test", Context.MODE_PRIVATE))
        val repository = NotificationChapterRepository(ThrowingStartActivityContext(context), settings)

        val opened = repository.openApp(
            AppEntry(
                packageName = packageName,
                label = "Unavailable",
                hueColor = 0,
                identityKey = AppIdentity.packageOnly(packageName).key,
                notificationSourceKey = AppIdentity.packageOnly(packageName).notificationSourceKey,
                componentName = null,
            ),
        )

        assertFalse(opened)
    }

    private fun registerLauncherActivity(context: Context, packageName: String) {
        val component = ComponentName(packageName, "$packageName.MainActivity")
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = component.className
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    nonLocalizedLabel = "Unavailable"
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName),
            resolveInfo,
        )
    }

    private class ThrowingStartActivityContext(base: Context) : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            throw SecurityException("blocked")
        }
    }
}
