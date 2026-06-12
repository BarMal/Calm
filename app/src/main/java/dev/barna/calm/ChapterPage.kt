package dev.barna.calm

class ChapterPage private constructor(
    @JvmField val key: String,
    @JvmField val marker: String,
    @JvmField val title: String,
    @JvmField val chapter: AppChapter?,
    @JvmField val appScope: AppLibraryScope?,
    @JvmField val classicPage: ClassicLauncherPageDefinition?,
) {
    fun withMarker(marker: String): ChapterPage {
        return ChapterPage(key, marker, title, chapter, appScope, classicPage)
    }

    companion object {
        @JvmStatic
        fun overview(overviewKey: String): ChapterPage {
            return ChapterPage(overviewKey, "I", "Overview", null, null, null)
        }

        @JvmStatic
        fun appLibrary(appLibraryKey: String): ChapterPage {
            return ChapterPage(appLibraryKey, "I", "Apps", null, AppLibraryScope.ALL, null)
        }

        @JvmStatic
        fun personalApps(appLibraryKey: String, marker: String): ChapterPage {
            return ChapterPage(appLibraryKey, marker, "Personal apps", null, AppLibraryScope.PERSONAL, null)
        }

        @JvmStatic
        fun workApps(appLibraryKey: String, marker: String): ChapterPage {
            return ChapterPage(appLibraryKey, marker, "Work apps", null, AppLibraryScope.WORK, null)
        }

        @JvmStatic
        fun pinned(pinnedKey: String, marker: String): ChapterPage {
            return ChapterPage(pinnedKey, marker, "Pinned", null, null, null)
        }

        @JvmStatic
        fun contacts(contactsKey: String, marker: String): ChapterPage {
            return ChapterPage(contactsKey, marker, "People", null, null, null)
        }

        @JvmStatic
        fun agenda(agendaKey: String, marker: String): ChapterPage {
            return ChapterPage(agendaKey, marker, "Agenda", null, null, null)
        }

        @JvmStatic
        fun workOverview(workOverviewKey: String, marker: String): ChapterPage {
            return ChapterPage(workOverviewKey, marker, "Work", null, null, null)
        }

        @JvmStatic
        fun notifications(chapter: AppChapter, marker: String): ChapterPage {
            return ChapterPage(chapter.identityKey, marker, chapter.label, chapter, null, null)
        }

        @JvmStatic
        fun classic(page: ClassicLauncherPageDefinition, marker: String): ChapterPage {
            return ChapterPage(page.key, marker, page.title, null, null, page)
        }
    }
}
