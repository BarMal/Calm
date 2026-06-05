package dev.barna.calm

class ChapterPage private constructor(
    @JvmField val key: String,
    @JvmField val marker: String,
    @JvmField val title: String,
    @JvmField val chapter: AppChapter?,
) {
    companion object {
        @JvmStatic
        fun overview(overviewKey: String): ChapterPage {
            return ChapterPage(overviewKey, "I", "Overview", null)
        }

        @JvmStatic
        fun notifications(chapter: AppChapter, marker: String): ChapterPage {
            return ChapterPage(chapter.packageName, marker, chapter.label, chapter)
        }

        @JvmStatic
        fun settings(settingsKey: String, marker: String): ChapterPage {
            return ChapterPage(settingsKey, marker, "Settings", null)
        }
    }
}
