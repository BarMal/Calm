package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStateViewModelTest {
    @Test
    fun startsWithoutRenderModel() {
        val viewModel = LauncherStateViewModel()

        assertNull(viewModel.uiState.value.renderModel)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun markLoadingKeepsExistingRenderModel() {
        val viewModel = LauncherStateViewModel()
        val model = renderModel()

        viewModel.publish(model)
        viewModel.markLoading()

        assertSame(model, viewModel.uiState.value.renderModel)
        assertTrue(viewModel.uiState.value.loading)
    }

    @Test
    fun publishStoresRenderModelAndClearsLoading() {
        val viewModel = LauncherStateViewModel()
        val model = renderModel()

        viewModel.markLoading()
        viewModel.publish(model)

        assertSame(model, viewModel.uiState.value.renderModel)
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(2, viewModel.uiState.value.generation)
    }

    @Test
    fun selectedPageSurvivesPublishedRenderModels() {
        val viewModel = LauncherStateViewModel()
        val first = renderModel()
        val second = renderModel()

        viewModel.selectPage("notifications:mail")
        viewModel.publish(first)
        viewModel.publish(second)

        assertEquals("notifications:mail", viewModel.uiState.value.selectedPageKey)
        assertSame(second, viewModel.uiState.value.renderModel)
    }

    private fun renderModel(): LauncherRenderModel {
        val preferences = LauncherUiPreferences(
            useTintedNotificationCards = true,
            useCardIconBackgrounds = true,
            cardCornerRadiusDp = 22,
            cardIconBlur = 0,
            focusBlurRadius = 0,
            splitAppsByProfile = false,
            placeWorkNotificationChaptersBeforeApps = false,
            cardHapticsEnabled = false,
            cardHapticStrength = 1,
            cardStackTuning = CardStackTuning(
                curve = 50,
                horizontalCurve = 0,
                arcWidth = 50,
                aboveFocusCards = 2,
                rotation = 0,
                verticalSpacing = 50,
                visibleCards = 3,
            ),
            showAdvancedStackControls = false,
        )
        return LauncherRenderModel(
            preferences = preferences,
            notificationChapters = emptyList(),
            appEntries = emptyList(),
            pinnedKeys = emptySet(),
            pinnedApps = emptyList(),
            hasCalendarPermission = false,
            calendarEvents = emptyList(),
            pages = listOf(ChapterPage.overview(CalmTheme.OVERVIEW_KEY)),
        )
    }
}
