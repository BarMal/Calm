package dev.barna.calm

class ClassicPageCoordinator(
    private val settings: LauncherSettings,
    private val selectPage: (String) -> Unit,
    private val render: () -> Unit,
    private val stringResource: (Int, Array<out Any>) -> String,
    private val showToast: (String) -> Unit,
) {
    private var editingClassicPageId: String? = null
    private var deleteWidget: (ClassicGridItem) -> Unit = {}
    private var defaultWidgetSpan: (Int) -> Pair<Int, Int>? = { null }
    var pendingPlacementItemId: String? = null
        private set

    fun setWidgetCallbacks(
        deleteWidget: (ClassicGridItem) -> Unit,
        defaultWidgetSpan: (Int) -> Pair<Int, Int>?,
    ) {
        this.deleteWidget = deleteWidget
        this.defaultWidgetSpan = defaultWidgetSpan
    }

    fun removeClassicGridItem(page: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val removed = settings.removeClassicGridItem(page.id, item.id) ?: return
        if (removed.type == ClassicGridItemType.WIDGET) {
            deleteWidget(removed)
        }
        toast(R.string.toast_removed_from_page, page.title)
        render()
    }

    fun moveClassicGridItem(
        sourcePage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        targetPage: ClassicLauncherPageDefinition,
    ) {
        val moved = settings.moveClassicGridItem(sourcePage.id, item.id, targetPage.id)
        if (!moved) {
            toast(R.string.toast_page_full, targetPage.title)
            return
        }
        selectPage(targetPage.key)
        toast(R.string.toast_page_item_moved_to, targetPage.title)
        render()
    }

    fun moveClassicGridItemWithinPage(
        page: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        x: Int,
        y: Int,
    ) {
        if (item.x == x && item.y == y) {
            toast(R.string.toast_already_there)
            return
        }
        if (!settings.moveClassicGridItemWithinPage(page.id, item.id, x, y)) {
            toast(R.string.toast_position_unavailable)
            return
        }
        toast(R.string.toast_moved)
        render()
    }

    fun resizeClassicGridItem(
        page: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        width: Int,
        height: Int,
    ) {
        if (item.width == width && item.height == height) {
            toast(R.string.toast_already_that_size)
            return
        }
        val resized = settings.resizeClassicGridItem(page.id, item.id, width, height)
        if (!resized) {
            toast(R.string.toast_no_room_for_size)
            return
        }
        toast(R.string.toast_resized)
        render()
    }

    fun resetClassicGridItemSize(page: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val gridConfig = settings.classicGridConfig()
        val defaultSize = when (item.type) {
            ClassicGridItemType.APP -> 1 to 1
            ClassicGridItemType.STATIC -> gridConfig.columns to 1
            ClassicGridItemType.WIDGET -> item.target.toIntOrNull()
                ?.let(defaultWidgetSpan)
                ?: (gridConfig.columns to 2)
        }
        resizeClassicGridItem(page, item, defaultSize.first, defaultSize.second)
    }

    fun beginItemPlacement(page: ClassicLauncherPageDefinition, itemId: String) {
        beginEditing(page)
        pendingPlacementItemId = itemId
    }

    fun finishItemPlacement(itemId: String) {
        if (pendingPlacementItemId == itemId) {
            pendingPlacementItemId = null
        }
    }

    fun addClassicPage() {
        val page = settings.addClassicPage()
        beginEditing(page)
        selectPage(page.key)
        toast(R.string.toast_classic_page_added, page.title)
        render()
    }

    fun addStaticItemToClassicPage(page: ClassicLauncherPageDefinition, staticItem: ClassicStaticItem) {
        if (settings.addStaticItemToClassicPage(page.id, staticItem)) {
            beginItemPlacement(page, ClassicGridItem.static(staticItem, x = 0, y = 0).id)
            toast(R.string.toast_static_item_added, staticItem.displayLabel, page.title)
            render()
        } else {
            toast(R.string.toast_no_room_for_item, staticItem.displayLabel)
        }
    }

    fun moveClassicPage(page: ClassicLauncherPageDefinition, targetIndex: Int) {
        if (!settings.moveClassicPage(page.id, targetIndex)) return
        selectPage(page.key)
        toast(R.string.toast_classic_page_moved, page.title)
        render()
    }

    fun isClassicPageEditing(page: ClassicLauncherPageDefinition): Boolean {
        return editingClassicPageId == page.id
    }

    fun beginEditing(page: ClassicLauncherPageDefinition) {
        editingClassicPageId = page.id
    }

    fun setClassicPageEditing(page: ClassicLauncherPageDefinition, editing: Boolean) {
        editingClassicPageId = if (editing) page.id else null
        if (!editing) pendingPlacementItemId = null
        render()
    }

    fun renameClassicPage(page: ClassicLauncherPageDefinition, title: String) {
        if (!settings.renameClassicPage(page.id, title)) {
            toast(R.string.toast_page_name_empty)
            return
        }
        toast(R.string.toast_page_renamed)
        render()
    }

    fun setDefaultClassicPage(page: ClassicLauncherPageDefinition) {
        if (!settings.setDefaultClassicPage(page.id)) return
        settings.setDefaultHomeSlot(PageSlot.CLASSIC_PAGES)
        toast(R.string.toast_page_set_home, page.title)
        render()
    }

    fun removeClassicPage(page: ClassicLauncherPageDefinition) {
        val removed = settings.removeClassicPage(page.id) ?: return
        onClassicPageRemoved(removed)
        toast(R.string.toast_page_removed, removed.title)
        render()
    }

    fun onClassicPageRemoved(removed: ClassicLauncherPageDefinition) {
        if (editingClassicPageId == removed.id) {
            editingClassicPageId = null
        }
        pendingPlacementItemId = null
        removed.items
            .filter { item -> item.type == ClassicGridItemType.WIDGET }
            .forEach(deleteWidget)
    }

    private val ClassicStaticItem.displayLabel: String
        get() = when (this) {
            ClassicStaticItem.CLOCK -> string(R.string.static_item_clock)
            ClassicStaticItem.SEARCH -> string(R.string.static_item_search)
        }

    private fun toast(resId: Int, vararg args: Any) {
        showToast(string(resId, *args))
    }

    private fun string(resId: Int, vararg args: Any): String {
        return stringResource(resId, args)
    }
}
