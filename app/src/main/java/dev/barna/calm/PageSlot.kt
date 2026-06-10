package dev.barna.calm

/**
 * Top-level page groupings the user can reorder, enable/disable, and pick a default home from.
 * Notification pages collapse into a single movable NOTIFICATIONS "cluster".
 */
enum class PageSlot {
    APPS,
    PINNED,
    CONTACTS,
    OVERVIEW,
    WORK_OVERVIEW,
    NOTIFICATIONS,
}

fun PageSlot.displayName(): String = when (this) {
    PageSlot.APPS -> "Apps"
    PageSlot.PINNED -> "Pinned"
    PageSlot.CONTACTS -> "People"
    PageSlot.OVERVIEW -> "Overview"
    PageSlot.WORK_OVERVIEW -> "Work overview"
    PageSlot.NOTIFICATIONS -> "Notifications"
}

/**
 * The user's page layout: slot order, disabled slots, and the default home slot. The defaults
 * reproduce the planner's current ordering exactly, so an unconfigured layout changes nothing.
 */
data class LauncherPageLayout(
    val order: List<PageSlot>,
    val disabled: Set<PageSlot>,
    val defaultHome: PageSlot,
) {
    val isDefaultArrangement: Boolean
        get() = order == DEFAULT_ORDER && disabled.isEmpty()

    companion object {
        val DEFAULT_ORDER: List<PageSlot> = listOf(
            PageSlot.APPS,
            PageSlot.PINNED,
            PageSlot.CONTACTS,
            PageSlot.OVERVIEW,
            PageSlot.WORK_OVERVIEW,
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
        page.chapter != null -> PageSlot.NOTIFICATIONS
        else -> PageSlot.OVERVIEW
    }

    fun firstPageKeyForSlot(pages: List<ChapterPage>, slot: PageSlot): String? =
        pages.firstOrNull { slotOf(it) == slot }?.key

    /**
     * Reorders [pages] to match [layout]: groups them by slot (preserving each slot's internal
     * order), emits slots in the configured order, and drops disabled slots. Returns the input
     * unchanged for the default arrangement, and never returns an empty list.
     */
    fun arrange(pages: List<ChapterPage>, layout: LauncherPageLayout): List<ChapterPage> {
        if (layout.isDefaultArrangement) return pages
        val bySlot = LinkedHashMap<PageSlot, MutableList<ChapterPage>>()
        pages.forEach { bySlot.getOrPut(slotOf(it)) { ArrayList() }.add(it) }
        val result = ArrayList<ChapterPage>()
        layout.order.forEach { slot ->
            if (slot in layout.disabled) return@forEach
            bySlot.remove(slot)?.let(result::addAll)
        }
        // Any slot not named in the configured order keeps its pages at the end (forward-compatible).
        bySlot.forEach { (slot, slotPages) -> if (slot !in layout.disabled) result.addAll(slotPages) }
        return result.ifEmpty { pages }
    }
}
