package dev.barna.calm

data class PageLayoutPreviewSegment(
    val slot: PageSlot,
    val label: String,
    val shortLabel: String,
    val enabled: Boolean,
    val home: Boolean,
)

object PageLayoutPreviewModel {
    fun segments(layout: LauncherPageLayout): List<PageLayoutPreviewSegment> {
        return layout.order.map { slot ->
            PageLayoutPreviewSegment(
                slot = slot,
                label = label(slot),
                shortLabel = shortLabel(slot),
                enabled = true,
                home = slot == layout.defaultHome,
            )
        }
    }

    private fun label(slot: PageSlot): String = when (slot) {
        PageSlot.APPS -> "Apps"
        PageSlot.PINNED -> "Pinned"
        PageSlot.CONTACTS -> "People"
        PageSlot.AGENDA -> "Agenda"
        PageSlot.ALARMS -> "Alarms"
        PageSlot.RSS -> "RSS"
        PageSlot.OVERVIEW -> "Overview"
        PageSlot.WORK_OVERVIEW -> "Work"
        PageSlot.CLASSIC_PAGES -> "Classic"
        PageSlot.NOTIFICATIONS -> "Notifications"
    }

    private fun shortLabel(slot: PageSlot): String = when (slot) {
        PageSlot.APPS -> "Apps"
        PageSlot.PINNED -> "Pin"
        PageSlot.CONTACTS -> "Ppl"
        PageSlot.AGENDA -> "Cal"
        PageSlot.ALARMS -> "Alm"
        PageSlot.RSS -> "RSS"
        PageSlot.OVERVIEW -> "Home"
        PageSlot.WORK_OVERVIEW -> "Work"
        PageSlot.CLASSIC_PAGES -> "Grid"
        PageSlot.NOTIFICATIONS -> "Noti"
    }
}
