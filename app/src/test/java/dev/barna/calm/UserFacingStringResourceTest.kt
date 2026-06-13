package dev.barna.calm

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingStringResourceTest {
    private val projectRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun representativeUserFacingStringsAreResourceBacked() {
        val source = File(projectRoot, "app/src/main/java/dev/barna/calm")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        val forbiddenLiterals = listOf(
            "\"Shortcut unavailable\"",
            "\"Already there\"",
            "\"Position unavailable\"",
            "\"Set as home\"",
            "\"Customise\"",
            "\"Move left\"",
            "\"Move right\"",
            "ContextAction(\"Delete\"",
            "ContextAction(\"Remove\"",
            "\"1 active note\"",
            "\"$" + "count active notes\"",
            "\"Added People page\"",
            "\"Added Agenda page\"",
            "\"Added Alarms page\"",
            "\"Added RSS page\"",
            "\"Added Pinned page\"",
            "\"Work overview appears when work notifications arrive\"",
            "\"Notification pages appear when notifications arrive\"",
        )

        forbiddenLiterals.forEach { literal ->
            assertFalse("Found hardcoded user-facing string: $literal", source.contains(literal))
        }
    }
}
