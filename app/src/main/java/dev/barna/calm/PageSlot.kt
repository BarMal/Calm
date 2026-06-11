package dev.barna.calm

/**
 * Top-level page groupings the user can reorder and pick a default home from.
 * Notification pages collapse into a single movable NOTIFICATIONS "cluster".
 */
enum class PageSlot {
    APPS,
    PINNED,
    CONTACTS,
    OVERVIEW,
    WORK_OVERVIEW,
    CLASSIC_PAGES,
    NOTIFICATIONS,
}

/**
 * The user's page layout: slot order, legacy disabled slots, and the default home slot. The defaults
 * reproduce the planner's current ordering exactly, so an unconfigured layout changes nothing.
 *
 * Disabled slots are kept only so older preferences can still be decoded. Page visibility now
 * comes from added page instances and live data, not from a secondary enabled/disabled filter.
 */
data class LauncherPageLayout(
    val order: List<PageSlot>,
    val disabled: Set<PageSlot>,
    val defaultHome: PageSlot,
) {
    val isDefaultArrangement: Boolean
        get() = order == DEFAULT_ORDER

    companion object {
        val DEFAULT_ORDER: List<PageSlot> = listOf(
            PageSlot.APPS,
            PageSlot.PINNED,
            PageSlot.CONTACTS,
            PageSlot.OVERVIEW,
            PageSlot.WORK_OVERVIEW,
            PageSlot.CLASSIC_PAGES,
            PageSlot.NOTIFICATIONS,
        )
        val DEFAULT = LauncherPageLayout(DEFAULT_ORDER, emptySet(), PageSlot.OVERVIEW)
    }
}

/** Classifies built pages into slots and rearranges them to honour a [LauncherPageLayout]. */
object PageArranger {
    fun slotOf(page: ChapterPage): PageSlot = when {
        page.appScope != null -> PageSlot.APPS
        page.key == CalmTheme.PINNED_KEY -> PageSlot.PINNED
        page.key == CalmTheme.CONTACTS_KEY -> PageSlot.CONTACTS
        page.key == CalmTheme.WORK_OVERVIEW_KEY -> PageSlot.WORK_OVERVIEW
        page.key == CalmTheme.OVERVIEW_KEY -> PageSlot.OVERVIEW
        page.classicPage != null -> PageSlot.CLASSIC_PAGES
        page.chapter != null -> PageSlot.NOTIFICATIONS
        else -> PageSlot.OVERVIEW
    }

    fun firstPageKeyForSlot(pages: List<ChapterPage>, slot: PageSlot): String? =
        pages.firstOrNull { slotOf(it) == slot }?.key

    /**
     * Reorders [pages] to match [layout]: groups them by slot (preserving each slot's internal
     * order) and emits slots in the configured order. Returns the input unchanged for the default
     * arrangement, and never returns an empty list.
     */
    fun arrange(pages: List<ChapterPage>, layout: LauncherPageLayout): List<ChapterPage> {
        if (layout.isDefaultArrangement) return pages
        val bySlot = LinkedHashMap<PageSlot, MutableList<ChapterPage>>()
        pages.forEach { bySlot.getOrPut(slotOf(it)) { ArrayList() }.add(it) }
        val result = ArrayList<ChapterPage>()
        layout.order.forEach { slot ->
            bySlot.remove(slot)?.let(result::addAll)
        }
        // Any slot not named in the configured order keeps its pages at the end (forward-compatible).
        bySlot.forEach { (_, slotPages) -> result.addAll(slotPages) }
        return result.ifEmpty { pages }
    }
}

object PageLayoutPolicy {
    fun firstEnabledHome(layout: LauncherPageLayout): PageSlot {
        return layout.defaultHome
    }
}
