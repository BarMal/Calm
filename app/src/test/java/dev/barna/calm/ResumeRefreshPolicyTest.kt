package dev.barna.calm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRefreshPolicyTest {
    private val policy = ResumeRefreshPolicy()

    @Test
    fun refreshesWhenNoUiHasBeenRendered() {
        assertTrue(policy.shouldRefreshImmediately(hasCurrentScreen = false, hasCurrentState = false, launcherSettingsChanged = false))
        assertTrue(policy.shouldRefreshImmediately(hasCurrentScreen = true, hasCurrentState = false, launcherSettingsChanged = false))
        assertTrue(policy.shouldRefreshImmediately(hasCurrentScreen = false, hasCurrentState = true, launcherSettingsChanged = false))
    }

    @Test
    fun preservesExistingUiOnResume() {
        assertFalse(policy.shouldRefreshImmediately(hasCurrentScreen = true, hasCurrentState = true, launcherSettingsChanged = false))
    }

    @Test
    fun refreshesExistingUiWhenLauncherSettingsChanged() {
        assertTrue(policy.shouldRefreshImmediately(hasCurrentScreen = true, hasCurrentState = true, launcherSettingsChanged = true))
    }
}
