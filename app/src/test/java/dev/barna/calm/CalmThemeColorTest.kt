package dev.barna.calm

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmThemeColorTest {
    private val projectRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun calmThemeExposesLauncherColorAttributesBackedByResources() {
        val attrs = File(projectRoot, "app/src/main/res/values/attrs.xml").readText()
        val colors = File(projectRoot, "app/src/main/res/values/colors.xml").readText()
        val styles = File(projectRoot, "app/src/main/res/values/styles.xml").readText()

        assertColorAttribute(attrs, colors, styles, "calmNotificationBadgeColor", "calm_notification_badge")
        assertColorAttribute(attrs, colors, styles, "calmOnNotificationBadgeColor", "calm_on_notification_badge")
        assertColorAttribute(attrs, colors, styles, "calmPageOverviewScrimColor", "calm_page_overview_scrim")
        assertColorAttribute(attrs, colors, styles, "calmPageOverviewBadgeScrimColor", "calm_page_overview_badge_scrim")
        assertColorAttribute(attrs, colors, styles, "calmOnPageOverviewScrimColor", "calm_on_page_overview_scrim")
    }

    private fun assertColorAttribute(attrs: String, colors: String, styles: String, attrName: String, colorName: String) {
        assertTrue(attrs.contains("""<attr name="$attrName" format="color" />"""))
        assertTrue(colors.contains("""<color name="$colorName">#"""))
        assertTrue(styles.contains("""<item name="$attrName">@color/$colorName</item>"""))
    }
}
