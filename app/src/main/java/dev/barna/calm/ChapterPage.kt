package dev.barna.calm

class ChapterPage private constructor(
    @JvmField val key: String,
    @JvmField val marker: String,
    @JvmField val title: String,
    @JvmField val chapter: AppChapter?,
    @JvmField val appScope: AppLibraryScope?,
) {
    fun withMarker(marker: String): ChapterPage {
        return ChapterPage(key, marker, title, chapter, appScope)
    }

    companion object {
        @JvmStatic
        fun overview(overviewKey: String): ChapterPage {
            return ChapterPage(overviewKey, "I", "Overview", null, null)
        }

        @JvmStatic
        fun appLibrary(appLibraryKey: String): ChapterPage {
            return ChapterPage(appLibraryKey, "I", "Apps", null, AppLibraryScope.ALL)
        }

        @JvmStatic
        fun personalApps(appLibraryKey: String, marker: String): ChapterPage {
            return ChapterPage(appLibraryKey, marker, "Personal apps", null, AppLibraryScope.PERSONAL)
        }

        @JvmStatic
        fun workApps(appLibraryKey: String, marker: String): ChapterPage {
            return ChapterPage(appLibraryKey, marker, "Work apps", null, AppLibraryScope.WORK)
        }

        @JvmStatic
        fun pinned(pinnedKey: String, marker: String): ChapterPage {
            return ChapterPage(pinnedKey, marker, "Pinned", null, null)
        }

        @JvmStatic
        fun contacts(contactsKey: String, marker: String): ChapterPage {
            return ChapterPage(contactsKey, marker, "People", null, null)
        }

        @JvmStatic
        fun widgets(widgetsKey: String, marker: String): ChapterPage {
            return ChapterPage(widgetsKey, marker, "Widgets", null, null)
        }

        @JvmStatic
        fun customHome(customHomeKey: String, marker: String): ChapterPage {
            return ChapterPage(customHomeKey, marker, "Home", null, null)
        }

        @JvmStatic
        fun workOverview(workOverviewKey: String, marker: String): ChapterPage {
            return ChapterPage(workOverviewKey, marker, "Work", null, null)
        }

        @JvmStatic
        fun notifications(chapter: AppChapter, marker: String): ChapterPage {
            return ChapterPage(chapter.identityKey, marker, chapter.label, chapter, null)
        }

    }
}
