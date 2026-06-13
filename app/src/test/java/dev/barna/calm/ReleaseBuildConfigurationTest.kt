package dev.barna.calm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseBuildConfigurationTest {
    private val projectRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun releaseBuildUsesR8AndResourceShrinking() {
        val buildFile = File(projectRoot, "app/build.gradle.kts").readText()

        assertTrue(buildFile.contains("isMinifyEnabled = true"))
        assertTrue(buildFile.contains("isShrinkResources = true"))
        assertTrue(buildFile.contains("getDefaultProguardFile(\"proguard-android-optimize.txt\")"))
        assertTrue(buildFile.contains("\"proguard-rules.pro\""))
    }

    @Test
    fun proguardRulesKeepAndroidEntryPoints() {
        val rules = File(projectRoot, "app/proguard-rules.pro").readText()

        assertTrue(rules.contains("dev.barna.calm.MainActivity"))
        assertTrue(rules.contains("dev.barna.calm.CalmSettingsActivity"))
        assertTrue(rules.contains("extends android.service.notification.NotificationListenerService"))
        assertTrue(rules.contains("extends android.appwidget.AppWidgetProvider"))
    }
}
