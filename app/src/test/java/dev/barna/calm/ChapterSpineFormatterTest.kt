package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSpineFormatterTest {
    private val page = ChapterPage.notifications(
        AppChapter(
            packageName = "com.example.chat",
            label = "Chat",
            notifications = emptyList(),
            launchable = true,
            hueColor = 0,
        ),
        "IV",
    )

    @Test
    fun combinedModeKeepsExistingMarkerAndTitleText() {
        assertEquals(
            "IV  Chat",
            ChapterSpineFormatter.displayText(page, ChapterSpineStyle(titleMode = ChapterSpineTitleMode.COMBINED)),
        )
    }

    @Test
    fun splitModePlacesMarkerAboveTitle() {
        assertEquals(
            "IV\nChat",
            ChapterSpineFormatter.displayText(page, ChapterSpineStyle(titleMode = ChapterSpineTitleMode.SPLIT)),
        )
    }

    @Test
    fun titleOnlyModeSuppressesMarker() {
        assertEquals(
            "Chat",
            ChapterSpineFormatter.displayText(page, ChapterSpineStyle(titleMode = ChapterSpineTitleMode.TITLE_ONLY)),
        )
    }

    @Test
    fun spineOnlyModeSuppressesTitle() {
        assertEquals(
            "IV",
            ChapterSpineFormatter.displayText(page, ChapterSpineStyle(titleMode = ChapterSpineTitleMode.SPINE_ONLY)),
        )
    }

    @Test
    fun hiddenModeAndHiddenPositionSuppressText() {
        assertNull(ChapterSpineFormatter.displayText(page, ChapterSpineStyle(titleMode = ChapterSpineTitleMode.HIDDEN)))
        assertNull(ChapterSpineFormatter.displayText(page, ChapterSpineStyle(position = ChapterSpinePosition.HIDDEN)))
    }
}
