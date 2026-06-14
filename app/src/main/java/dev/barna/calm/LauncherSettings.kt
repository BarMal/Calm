package dev.barna.calm

import android.content.Context
import android.content.SharedPreferences
import java.text.Collator

class LauncherSettings(private val preferences: SharedPreferences) {
    private val mutationLock = Any()

    constructor(context: Context) : this(LauncherPreferencesFactory.create(context))

    fun excludedPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_EXCLUDED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun notificationFilters(): List<NotificationFilter> {
        return preferences.getStringSet(PREF_NOTIFICATION_FILTERS, emptySet())
            .orEmpty()
            .mapNotNull(NotificationFilter::decode)
    }

    fun pinnedPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_PINNED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun hiddenAppKeys(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_HIDDEN_APP_KEYS, emptySet()) ?: emptySet())
    }

    fun setHiddenAppKeys(appKeys: Set<String>) {
        synchronized(mutationLock) {
            preferences.edit().putStringSet(PREF_HIDDEN_APP_KEYS, HashSet(appKeys)).apply()
        }
    }

    fun isAppHidden(app: AppEntry): Boolean {
        return AppVisibility.isHidden(app, hiddenAppKeys())
    }

    fun hideApp(appKey: String, label: String) {
        synchronized(mutationLock) {
            preferences.edit()
                .putStringSet(PREF_HIDDEN_APP_KEYS, hiddenAppKeys() + appKey)
                .putString(PREF_HIDDEN_APP_LABEL_PREFIX + appKey, label)
                .apply()
        }
    }

    fun showApp(appKey: String) {
        synchronized(mutationLock) {
            preferences.edit()
                .putStringSet(PREF_HIDDEN_APP_KEYS, hiddenAppKeys() - appKey)
                .remove(PREF_HIDDEN_APP_LABEL_PREFIX + appKey)
                .apply()
        }
    }

    fun hiddenApps(): List<ExcludedSource> {
        return hiddenAppKeys()
            .map { appKey ->
                val savedLabel = preferences.getString(PREF_HIDDEN_APP_LABEL_PREFIX + appKey, null)
                ExcludedSource(appKey, savedLabel ?: appKey)
            }
            .sortedWith { left, right -> Collator.getInstance().compare(left.label, right.label) }
    }

    fun pinPackage(packageName: String) {
        synchronized(mutationLock) {
            val pinned = pinnedPackages().toMutableSet()
            pinned.add(packageName)
            preferences.edit().putStringSet(PREF_PINNED_PACKAGES, pinned).apply()
        }
    }

    fun unpinPackage(packageName: String) {
        synchronized(mutationLock) {
            val pinned = pinnedPackages().toMutableSet()
            pinned.remove(packageName)
            preferences.edit().putStringSet(PREF_PINNED_PACKAGES, pinned).apply()
        }
    }

    fun pinnedChapterPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_PINNED_CHAPTER_PACKAGES, emptySet()) ?: emptySet())
    }

    fun pinChapter(packageName: String) {
        synchronized(mutationLock) {
            preferences.edit()
                .putStringSet(PREF_PINNED_CHAPTER_PACKAGES, pinnedChapterPackages() + packageName)
                .apply()
        }
    }

    fun unpinChapter(packageName: String) {
        synchronized(mutationLock) {
            preferences.edit()
                .putStringSet(PREF_PINNED_CHAPTER_PACKAGES, pinnedChapterPackages() - packageName)
                .apply()
        }
    }

    fun classicPages(): List<ClassicLauncherPageDefinition> {
        return ClassicLauncherPageDefinition.decodeList(preferences.getString(PREF_CLASSIC_PAGES, null))
    }

    fun classicPagesEnabled(): Boolean {
        return classicPages().isNotEmpty()
    }

    fun firstEnabledClassicPage(): ClassicLauncherPageDefinition? {
        return classicPages().firstOrNull()
    }

    fun homeClassicPage(): ClassicLauncherPageDefinition? {
        val pages = classicPages()
        val preferredId = preferences.getString(PREF_CLASSIC_HOME_PAGE_ID, null)
        return pages.firstOrNull { page -> page.id == preferredId }
            ?: pages.firstOrNull()
    }

    fun setClassicPagesEnabled(enabled: Boolean) {
        val pages = classicPages()
        val nextPages = if (pages.isEmpty() && enabled) {
            listOf(ClassicLauncherPageDefinition.default())
        } else {
            pages.map { page -> page.copy(enabled = enabled) }
        }
        setClassicPages(nextPages)
    }

    fun setClassicPages(pages: List<ClassicLauncherPageDefinition>) {
        preferences.edit()
            .putString(PREF_CLASSIC_PAGES, ClassicLauncherPageDefinition.encodeList(pages.distinctBy { it.id }))
            .apply()
    }

    fun addClassicPage(): ClassicLauncherPageDefinition {
        val pages = classicPages()
        val index = nextClassicPageIndex(pages)
        val page = ClassicLauncherPageDefinition.default(index)
        setClassicPages(pages + page)
        return page
    }

    fun renameClassicPage(pageId: String, title: String): Boolean {
        val cleanTitle = title.trim().takeIf { it.isNotBlank() } ?: return false
        val pages = classicPages()
        var changed = false
        val updatedPages = pages.map { page ->
            if (page.id == pageId) {
                changed = true
                page.copy(title = cleanTitle)
            } else {
                page
            }
        }
        if (!changed) return false
        setClassicPages(updatedPages)
        return true
    }

    fun setClassicPageEnabled(pageId: String, enabled: Boolean): Boolean {
        val pages = classicPages()
        var changed = false
        val updatedPages = pages.map { page ->
            if (page.id == pageId) {
                changed = true
                page.copy(enabled = enabled)
            } else {
                page
            }
        }
        if (!changed) return false
        setClassicPages(updatedPages)
        return true
    }

    fun setDefaultClassicPage(pageId: String): Boolean {
        val pages = classicPages()
        if (pages.none { it.id == pageId }) return false
        setClassicPages(pages.map { page -> if (page.id == pageId) page.copy(enabled = true) else page })
        preferences.edit().putString(PREF_CLASSIC_HOME_PAGE_ID, pageId).apply()
        return true
    }

    fun moveClassicPage(pageId: String, targetIndex: Int): Boolean {
        val pages = classicPages()
        val fromIndex = pages.indexOfFirst { it.id == pageId }
        if (fromIndex == -1) return false
        val toIndex = targetIndex.coerceIn(0, pages.lastIndex)
        if (fromIndex == toIndex) return false
        val updatedPages = pages.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        setClassicPages(updatedPages)
        return true
    }

    fun removeClassicPage(pageId: String): ClassicLauncherPageDefinition? {
        val pages = classicPages()
        val removed = pages.firstOrNull { it.id == pageId } ?: return null
        val updatedPages = pages.filterNot { it.id == pageId }
        setClassicPages(updatedPages)
        if (preferences.getString(PREF_CLASSIC_HOME_PAGE_ID, null) == pageId) {
            val nextHomeId = updatedPages.firstOrNull()?.id
            preferences.edit().apply {
                if (nextHomeId == null) remove(PREF_CLASSIC_HOME_PAGE_ID) else putString(PREF_CLASSIC_HOME_PAGE_ID, nextHomeId)
            }.apply()
        }
        return removed
    }

    fun isClassicPageApp(identityKey: String): Boolean {
        return classicPages().any { page -> page.containsApp(identityKey) }
    }

    fun isClassicPageWidget(appWidgetId: Int): Boolean {
        return classicPages().any { page -> page.containsWidget(appWidgetId) }
    }

    fun isClassicPageStaticItem(staticItem: ClassicStaticItem): Boolean {
        return classicPages().any { page -> page.containsStaticItem(staticItem) }
    }

    fun addAppToClassicPage(identityKey: String): Boolean {
        if (isClassicPageApp(identityKey)) return false
        val pages = classicPages().ifEmpty { listOf(ClassicLauncherPageDefinition.default()) }
        val target = pages.first()
        return addAppToClassicPage(target.id, identityKey, pages)
    }

    fun addAppToClassicPage(pageId: String, identityKey: String): Boolean {
        if (isClassicPageApp(identityKey)) return false
        return addAppToClassicPage(pageId, identityKey, classicPages())
    }

    fun addWidgetToClassicPage(
        pageId: String,
        appWidgetId: Int,
        width: Int = classicGridConfig().columns,
        height: Int = 2,
    ): Boolean {
        if (isClassicPageWidget(appWidgetId)) return false
        val pages = classicPages()
        val gridConfig = classicGridConfig()
        val updatedPages = pages.map { page ->
            if (page.id == pageId) page.withWidget(appWidgetId, width, height, gridConfig) ?: return false else page
        }
        if (updatedPages == pages) return false
        setClassicPages(updatedPages)
        return true
    }

    fun addStaticItemToClassicPage(
        pageId: String,
        staticItem: ClassicStaticItem,
        width: Int = classicGridConfig().columns,
        height: Int = 1,
    ): Boolean {
        if (isClassicPageStaticItem(staticItem)) return false
        val pages = classicPages()
        val gridConfig = classicGridConfig()
        val updatedPages = pages.map { page ->
            if (page.id == pageId) page.withStaticItem(staticItem, width, height, gridConfig) ?: return false else page
        }
        if (updatedPages == pages) return false
        setClassicPages(updatedPages)
        return true
    }

    fun removeClassicGridItem(pageId: String, itemId: String): ClassicGridItem? {
        val pages = classicPages()
        var removed: ClassicGridItem? = null
        val updatedPages = pages.map { page ->
            if (page.id != pageId) return@map page
            removed = page.items.firstOrNull { item -> item.id == itemId }
            page.withoutItem(itemId)
        }
        val removedItem = removed ?: return null
        setClassicPages(updatedPages)
        return removedItem
    }

    fun moveClassicGridItem(sourcePageId: String, itemId: String, targetPageId: String): Boolean {
        if (sourcePageId == targetPageId) return false
        val pages = classicPages()
        val sourcePage = pages.firstOrNull { page -> page.id == sourcePageId } ?: return false
        val item = sourcePage.items.firstOrNull { candidate -> candidate.id == itemId } ?: return false
        val targetPage = pages.firstOrNull { page -> page.id == targetPageId } ?: return false
        val gridConfig = classicGridConfig()
        val updatedTarget = targetPage
            .copy(enabled = true)
            .withItemAtNextFreeArea(item, gridConfig)
            ?: return false
        setClassicPages(
            pages.map { page ->
                when (page.id) {
                    sourcePageId -> page.withoutItem(itemId)
                    targetPageId -> updatedTarget
                    else -> page
                }
            },
        )
        return true
    }

    fun moveClassicGridItemWithinPage(pageId: String, itemId: String, x: Int, y: Int): Boolean {
        val pages = classicPages()
        val gridConfig = classicGridConfig()
        var changed = false
        val updatedPages = pages.map { page ->
            if (page.id != pageId) return@map page
            val updatedPage = page.withMovedItem(itemId, x, y, gridConfig) ?: return false
            changed = updatedPage != page
            updatedPage
        }
        if (!changed) return false
        setClassicPages(updatedPages)
        return true
    }

    fun resizeClassicGridItem(pageId: String, itemId: String, width: Int, height: Int): Boolean {
        val pages = classicPages()
        val gridConfig = classicGridConfig()
        var changed = false
        val updatedPages = pages.map { page ->
            if (page.id != pageId) return@map page
            val updatedPage = page.withResizedItem(itemId, width, height, gridConfig) ?: return false
            changed = updatedPage != page
            updatedPage
        }
        if (!changed) return false
        setClassicPages(updatedPages)
        return true
    }

    private fun addAppToClassicPage(
        pageId: String,
        identityKey: String,
        pages: List<ClassicLauncherPageDefinition>,
    ): Boolean {
        var changed = false
        val gridConfig = classicGridConfig()
        val updatedPages = pages.map { page ->
            if (page.id == pageId) {
                val enabledPage = page.copy(enabled = true)
                val updatedPage = enabledPage.withApp(identityKey, gridConfig) ?: return false
                changed = updatedPage != page
                updatedPage
            } else {
                page
            }
        }
        if (!changed) return false
        setClassicPages(updatedPages)
        return true
    }

    fun classicGridConfig(): ClassicGridConfig {
        return ClassicGridConfig.from(
            columns = preferences.getInt(PREF_CLASSIC_GRID_COLUMNS, ClassicGridConfig.DEFAULT_COLUMNS),
            rows = preferences.getInt(PREF_CLASSIC_GRID_ROWS, ClassicGridConfig.DEFAULT_ROWS),
        )
    }

    fun setClassicGridColumns(columns: Int) {
        preferences.edit()
            .putInt(PREF_CLASSIC_GRID_COLUMNS, columns.coerceIn(ClassicGridConfig.MIN_COLUMNS, ClassicGridConfig.MAX_COLUMNS))
            .apply()
    }

    fun setClassicGridRows(rows: Int) {
        preferences.edit()
            .putInt(PREF_CLASSIC_GRID_ROWS, rows.coerceIn(ClassicGridConfig.MIN_ROWS, ClassicGridConfig.MAX_ROWS))
            .apply()
    }

    fun dockConfig(): DockConfig {
        return DockConfig(
            enabled = preferences.getBoolean(PREF_DOCK_ENABLED, false),
            style = enumPreference(PREF_DOCK_STYLE, DockStyle.CLASSIC),
            itemCount = preferences.getInt(PREF_DOCK_ITEM_COUNT, DockConfig.DEFAULT_ITEM_COUNT)
                .coerceIn(DockConfig.MIN_ITEM_COUNT, DockConfig.MAX_ITEM_COUNT),
            itemSpan = preferences.getInt(PREF_DOCK_ITEM_SPAN, DockConfig.DEFAULT_ITEM_SPAN)
                .coerceIn(DockConfig.MIN_ITEM_SPAN, DockConfig.MAX_ITEM_SPAN),
            verticalPaddingDp = preferences.getInt(PREF_DOCK_VERTICAL_PADDING, DockConfig.DEFAULT_VERTICAL_PADDING_DP)
                .coerceIn(DockConfig.MIN_VERTICAL_PADDING_DP, DockConfig.MAX_VERTICAL_PADDING_DP),
            horizontalPaddingDp = preferences.getInt(PREF_DOCK_HORIZONTAL_PADDING, DockConfig.DEFAULT_HORIZONTAL_PADDING_DP)
                .coerceIn(DockConfig.MIN_HORIZONTAL_PADDING_DP, DockConfig.MAX_HORIZONTAL_PADDING_DP),
            tapAction = enumPreference(PREF_DOCK_TAP_ACTION, DockConfig.DEFAULT_TAP_ACTION),
            longPressAction = enumPreference(PREF_DOCK_LONG_PRESS_ACTION, DockConfig.DEFAULT_LONG_PRESS_ACTION),
        )
    }

    fun setDockEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_DOCK_ENABLED, enabled).apply()
    }

    fun setDockStyle(style: DockStyle) {
        preferences.edit().putString(PREF_DOCK_STYLE, style.name).apply()
    }

    fun setDockItemCount(count: Int) {
        preferences.edit()
            .putInt(PREF_DOCK_ITEM_COUNT, count.coerceIn(DockConfig.MIN_ITEM_COUNT, DockConfig.MAX_ITEM_COUNT))
            .apply()
    }

    fun setDockItemSpan(span: Int) {
        preferences.edit()
            .putInt(PREF_DOCK_ITEM_SPAN, span.coerceIn(DockConfig.MIN_ITEM_SPAN, DockConfig.MAX_ITEM_SPAN))
            .apply()
    }

    fun setDockVerticalPadding(dp: Int) {
        preferences.edit()
            .putInt(PREF_DOCK_VERTICAL_PADDING, dp.coerceIn(DockConfig.MIN_VERTICAL_PADDING_DP, DockConfig.MAX_VERTICAL_PADDING_DP))
            .apply()
    }

    fun setDockHorizontalPadding(dp: Int) {
        preferences.edit()
            .putInt(PREF_DOCK_HORIZONTAL_PADDING, dp.coerceIn(DockConfig.MIN_HORIZONTAL_PADDING_DP, DockConfig.MAX_HORIZONTAL_PADDING_DP))
            .apply()
    }

    fun setDockTapAction(action: DockInteractionAction) {
        preferences.edit().putString(PREF_DOCK_TAP_ACTION, action.name).apply()
    }

    fun setDockLongPressAction(action: DockInteractionAction) {
        preferences.edit().putString(PREF_DOCK_LONG_PRESS_ACTION, action.name).apply()
    }

    fun dockKeys(): List<String> {
        val raw = preferences.getString(PREF_DOCK_KEYS, "") ?: ""
        return raw.split('\n').filter { it.isNotEmpty() }
    }

    fun addDockKey(identityKey: String): Boolean {
        synchronized(mutationLock) {
            val config = dockConfig()
            val current = dockKeys()
            if (current.size >= config.itemCount || identityKey in current) return false
            preferences.edit().putString(PREF_DOCK_KEYS, (current + identityKey).joinToString("\n")).apply()
            return true
        }
    }

    fun removeDockKey(identityKey: String) {
        synchronized(mutationLock) {
            preferences.edit().putString(PREF_DOCK_KEYS, (dockKeys() - identityKey).joinToString("\n")).apply()
        }
    }

    fun setDockKeys(identityKeys: List<String>) {
        synchronized(mutationLock) {
            preferences.edit().putString(PREF_DOCK_KEYS, identityKeys.joinToString("\n")).apply()
        }
    }

    fun moveDockKey(identityKey: String, toIndex: Int) {
        synchronized(mutationLock) {
            val current = dockKeys().toMutableList()
            val from = current.indexOf(identityKey)
            if (from == -1) return
            current.removeAt(from)
            current.add(toIndex.coerceIn(0, current.size), identityKey)
            preferences.edit().putString(PREF_DOCK_KEYS, current.joinToString("\n")).apply()
        }
    }

    fun addNotificationFilter(filter: NotificationFilter) {
        synchronized(mutationLock) {
            val filters = preferences.getStringSet(PREF_NOTIFICATION_FILTERS, emptySet())
                .orEmpty()
                .toMutableSet()
            filters.add(filter.encode())
            preferences.edit().putStringSet(PREF_NOTIFICATION_FILTERS, filters).apply()
        }
    }

    fun cachedAppHue(packageName: String): Int {
        return preferences.getInt(PREF_APP_HUE_PREFIX + packageName, 0)
    }

    fun cacheAppHue(packageName: String, hueColor: Int) {
        if (hueColor == 0) return
        preferences.edit().putInt(PREF_APP_HUE_PREFIX + packageName, hueColor).apply()
    }

    fun cachedLaunchableAppsSnapshot(): AppLibrarySnapshot? {
        return preferences.getString(PREF_LAUNCHABLE_APPS_SNAPSHOT, null)
            ?.let(AppLibrarySnapshotCodec::decode)
    }

    fun cacheLaunchableAppsSnapshot(snapshot: AppLibrarySnapshot) {
        preferences.edit()
            .putString(PREF_LAUNCHABLE_APPS_SNAPSHOT, AppLibrarySnapshotCodec.encode(snapshot))
            .apply()
    }

    fun clearLaunchableAppsSnapshot() {
        preferences.edit().remove(PREF_LAUNCHABLE_APPS_SNAPSHOT).apply()
    }

    fun excludedSources(labelResolver: (String) -> String): List<ExcludedSource> {
        return excludedPackages()
            .map { sourceKey -> ExcludedSource(sourceKey, labelResolver(sourceKey)) }
            .sortedWith { left, right -> Collator.getInstance().compare(left.label, right.label) }
    }

    fun excludedLabel(packageName: String, fallback: () -> String): String {
        val savedLabel = preferences.getString(PREF_EXCLUDED_LABEL_PREFIX + packageName, null)
        if (!savedLabel.isNullOrBlank()) {
            return savedLabel
        }
        return fallback()
    }

    fun exclude(chapter: AppChapter) {
        synchronized(mutationLock) {
            val packages = excludedPackages().toMutableSet()
            packages.add(chapter.identityKey)
            preferences.edit()
                .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
                .putString(PREF_EXCLUDED_LABEL_PREFIX + chapter.identityKey, chapter.label)
                .apply()
        }
    }

    fun restore(packageName: String) {
        synchronized(mutationLock) {
            val packages = excludedPackages().toMutableSet()
            packages.remove(packageName)
            preferences.edit()
                .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
                .remove(PREF_EXCLUDED_LABEL_PREFIX + packageName)
                .apply()
        }
    }

    fun useTintedNotificationCards(): Boolean {
        return preferences.getBoolean(PREF_TINT_NOTIFICATION_CARDS, false)
    }

    fun pageSortOrder(): PageSortOrder {
        return PageSortOrder.decode(preferences.getString(PREF_PAGE_SORT_ORDER, null) ?: "")
    }

    fun setPageSortOrder(order: PageSortOrder) {
        preferences.edit().putString(PREF_PAGE_SORT_ORDER, order.name).apply()
    }

    fun uiPreferences(): LauncherUiPreferences {
        return LauncherUiPreferences(
            useTintedNotificationCards = useTintedNotificationCards(),
            useCardIconBackgrounds = useCardIconBackgrounds(),
            cardCornerRadiusDp = cardCornerRadiusDp(),
            cardIconBlur = cardIconBlur(),
            focusBlurRadius = focusBlurRadius(),
            splitAppsByProfile = splitAppsByProfile(),
            placeWorkNotificationChaptersBeforeApps = placeWorkNotificationChaptersBeforeApps(),
            cardHapticsEnabled = cardHapticsEnabled(),
            cardHapticStrength = cardHapticStrength(),
            cardStackTuning = cardStackTuning(),
            showAdvancedStackControls = showAdvancedStackControls(),
            cardVibrancy = cardVibrancy(),
            fullScreenModeEnabled = fullScreenModeEnabled(),
            pageSortOrder = pageSortOrder(),
            expandedCardsEnabled = expandedCardsEnabled(),
            pinnedPageEnabled = pinnedPageEnabled(),
            contactsPageEnabled = contactsPageEnabled(),
            agendaPageEnabled = agendaPageEnabled(),
            alarmsPageEnabled = alarmsPageEnabled(),
            rssPageEnabled = rssPageEnabled(),
            agendaSectionMode = agendaSectionMode(),
            agendaSectionTitleStyle = agendaSectionTitleStyle(),
            cardAppearance = cardAppearance(),
            pageLayout = pageLayout(),
            chapterSpineStyle = chapterSpineStyle(),
            appGroupingEnabled = appGroupingEnabled(),
            hasAnyCategoryAssignments = appCategoryAssignments().isNotEmpty(),
            notificationCardTapAction = notificationCardTapAction(),
        )
    }

    fun pageLayout(): LauncherPageLayout {
        val storedOrder = preferences.getString(PREF_PAGE_LAYOUT_ORDER, null)
            ?.split(',')
            ?.mapNotNull { name -> runCatching { PageSlot.valueOf(name) }.getOrNull() }
            ?.distinct()
            .orEmpty()
        // Append any slots missing from the stored order (e.g. added in a later version).
        val order = storedOrder + LauncherPageLayout.DEFAULT_ORDER.filter { it !in storedOrder }
        val home = preferences.getString(PREF_PAGE_LAYOUT_HOME, null)
            ?.let { name -> runCatching { PageSlot.valueOf(name) }.getOrNull() }
            ?: PageSlot.OVERVIEW
        val layout = LauncherPageLayout(order, emptySet(), home)
        return layout.copy(defaultHome = PageLayoutPolicy.firstEnabledHome(layout))
    }

    fun setPageLayoutOrder(order: List<PageSlot>) {
        preferences.edit().putString(PREF_PAGE_LAYOUT_ORDER, order.joinToString(",") { it.name }).apply()
    }

    fun setPageSlotEnabled(slot: PageSlot, enabled: Boolean) {
        preferences.edit()
            .remove(PREF_PAGE_LAYOUT_DISABLED)
            .apply()
    }

    fun setDefaultHomeSlot(slot: PageSlot) {
        preferences.edit()
            .remove(PREF_PAGE_LAYOUT_DISABLED)
            .putString(PREF_PAGE_LAYOUT_HOME, slot.name)
            .apply()
    }

    fun chapterSpineStyle(): ChapterSpineStyle {
        return ChapterSpineStyle(
            titleMode = enumPreference(PREF_CHAPTER_SPINE_TITLE_MODE, ChapterSpineTitleMode.TITLE_ONLY),
            position = enumPreference(PREF_CHAPTER_SPINE_POSITION, ChapterSpinePosition.TOP),
        )
    }

    fun notificationCardTapAction(): NotificationCardTapAction =
        enumPreference(PREF_NOTIF_CARD_TAP_ACTION, NotificationCardTapAction.OPEN_NOTIFICATION)

    fun setNotificationCardTapAction(action: NotificationCardTapAction) {
        preferences.edit().putString(PREF_NOTIF_CARD_TAP_ACTION, action.name).apply()
    }

    fun setChapterSpineTitleMode(mode: ChapterSpineTitleMode) {
        preferences.edit().putString(PREF_CHAPTER_SPINE_TITLE_MODE, mode.name).apply()
    }

    fun setChapterSpinePosition(position: ChapterSpinePosition) {
        preferences.edit().putString(PREF_CHAPTER_SPINE_POSITION, position.name).apply()
    }

    fun cardAppearance(): CardAppearance {
        val effect = runCatching {
            CardEffect.valueOf(preferences.getString(PREF_CARD_EFFECT, CardEffect.GLASS.name) ?: CardEffect.GLASS.name)
        }.getOrDefault(CardEffect.GLASS)
        return CardAppearance(
            effect = effect,
            effectStrength = preferences.getInt(PREF_CARD_EFFECT_STRENGTH, 100).coerceIn(0, 100),
            tintStrength = preferences.getInt(PREF_CARD_TINT_STRENGTH, 100).coerceIn(0, 100),
        )
    }

    fun setCardEffect(effect: CardEffect) {
        preferences.edit().putString(PREF_CARD_EFFECT, effect.name).apply()
    }

    fun setCardEffectStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_EFFECT_STRENGTH, strength.coerceIn(0, 100)).apply()
    }

    fun setCardTintStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_TINT_STRENGTH, strength.coerceIn(0, 100)).apply()
    }

    fun expandedCardsEnabled(): Boolean {
        return preferences.getBoolean(PREF_EXPANDED_CARDS, true)
    }

    fun toggleExpandedCards(): Boolean {
        val nextValue = !expandedCardsEnabled()
        preferences.edit().putBoolean(PREF_EXPANDED_CARDS, nextValue).apply()
        return nextValue
    }

    fun lastSelectedPageKey(): String? {
        return preferences.getString(PREF_LAST_PAGE_KEY, null)
    }

    fun setLastSelectedPageKey(pageKey: String) {
        preferences.edit().putString(PREF_LAST_PAGE_KEY, pageKey).apply()
    }

    fun pinnedPageEnabled(): Boolean {
        return preferences.getBoolean(PREF_PINNED_PAGE, false)
    }

    fun setPinnedPageEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_PINNED_PAGE, enabled).apply()
    }

    fun contactsPageEnabled(): Boolean {
        return preferences.getBoolean(PREF_CONTACTS_PAGE, false)
    }

    fun toggleContactsPage(): Boolean {
        val nextValue = !contactsPageEnabled()
        preferences.edit().putBoolean(PREF_CONTACTS_PAGE, nextValue).apply()
        return nextValue
    }

    fun agendaPageEnabled(): Boolean {
        return preferences.getBoolean(PREF_AGENDA_PAGE, false)
    }

    fun toggleAgendaPage(): Boolean {
        val nextValue = !agendaPageEnabled()
        preferences.edit().putBoolean(PREF_AGENDA_PAGE, nextValue).apply()
        return nextValue
    }

    fun alarmsPageEnabled(): Boolean {
        return preferences.getBoolean(PREF_ALARMS_PAGE, false)
    }

    fun toggleAlarmsPage(): Boolean {
        val nextValue = !alarmsPageEnabled()
        preferences.edit().putBoolean(PREF_ALARMS_PAGE, nextValue).apply()
        return nextValue
    }

    fun rssPageEnabled(): Boolean {
        return preferences.getBoolean(PREF_RSS_PAGE, false)
    }

    fun setRssPageEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_RSS_PAGE, enabled).apply()
    }

    fun toggleRssPage(): Boolean {
        val nextValue = !rssPageEnabled()
        setRssPageEnabled(nextValue)
        return nextValue
    }

    fun rssFeedUrls(): List<String> {
        return (preferences.getString(PREF_RSS_FEED_URLS, null) ?: "")
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .distinct()
            .toList()
    }

    fun addRssFeedUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank()) return false
        val current = rssFeedUrls()
        if (current.any { it.equals(clean, ignoreCase = true) }) return false
        setRssFeedUrls(current + clean)
        setRssPageEnabled(true)
        return true
    }

    fun removeRssFeedUrl(url: String) {
        setRssFeedUrls(rssFeedUrls().filterNot { it == url })
    }

    fun setRssFeedUrls(urls: List<String>) {
        preferences.edit().putString(PREF_RSS_FEED_URLS, urls.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")).apply()
    }

    fun agendaSectionMode(): CardStackSectionMode {
        return enumPreference(PREF_AGENDA_SECTION_MODE, CardStackSectionMode.TITLE_CARDS)
    }

    fun setAgendaSectionMode(mode: CardStackSectionMode) {
        preferences.edit().putString(PREF_AGENDA_SECTION_MODE, mode.name).apply()
    }

    fun agendaSectionTitleStyle(): SectionTitleCardStyle {
        return SectionTitleCardStyle(
            transparentBackground = preferences.getBoolean(PREF_AGENDA_SECTION_TRANSPARENT, true),
            bold = preferences.getBoolean(PREF_AGENDA_SECTION_BOLD, true),
            italic = preferences.getBoolean(PREF_AGENDA_SECTION_ITALIC, false),
            height = enumPreference(PREF_AGENDA_SECTION_HEIGHT, SectionTitleHeight.NORMAL),
            underline = enumPreference(PREF_AGENDA_SECTION_UNDERLINE, SectionTitleUnderline.FULL),
        )
    }

    fun setAgendaSectionTitleStyle(style: SectionTitleCardStyle) {
        preferences.edit()
            .putBoolean(PREF_AGENDA_SECTION_TRANSPARENT, style.transparentBackground)
            .putBoolean(PREF_AGENDA_SECTION_BOLD, style.bold)
            .putBoolean(PREF_AGENDA_SECTION_ITALIC, style.italic)
            .putString(PREF_AGENDA_SECTION_HEIGHT, style.height.name)
            .putString(PREF_AGENDA_SECTION_UNDERLINE, style.underline.name)
            .apply()
    }

    fun categoryList(): List<AppCategory> {
        return AppCategory.decodeList(preferences.getString(PREF_CATEGORY_LIST, null))
    }

    fun setCategoryList(categories: List<AppCategory>) {
        preferences.edit().putString(PREF_CATEGORY_LIST, AppCategory.encodeList(categories)).apply()
    }

    fun appCategoryAssignments(): Map<String, List<String>> {
        val raw = preferences.getString(PREF_APP_CATEGORY_ASSIGNMENTS, null) ?: return emptyMap()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab == -1) return@mapNotNull null
                val key = line.substring(0, tab)
                val ids = line.substring(tab + 1).split(',').filter { it.isNotBlank() }
                if (key.isBlank() || ids.isEmpty()) null else key to ids
            }
            .toMap()
    }

    fun setAppCategoryAssignments(assignments: Map<String, List<String>>) {
        synchronized(mutationLock) {
            val encoded = assignments.entries
                .filter { (_, ids) -> ids.isNotEmpty() }
                .joinToString("\n") { (key, ids) -> "$key\t${ids.distinct().joinToString(",")}" }
            preferences.edit().putString(PREF_APP_CATEGORY_ASSIGNMENTS, encoded).apply()
        }
    }

    fun setAppCategoryIds(identityKey: String, categoryIds: List<String>) {
        synchronized(mutationLock) {
            val current = appCategoryAssignments().toMutableMap()
            if (categoryIds.isEmpty()) current.remove(identityKey) else current[identityKey] = categoryIds
            setAppCategoryAssignments(current)
        }
    }

    fun appGroupingEnabled(): Boolean {
        return preferences.getBoolean(PREF_APP_GROUPING_ENABLED, false)
    }

    fun setAppGroupingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_APP_GROUPING_ENABLED, enabled).apply()
    }

    fun autoCategorise(appEntries: List<AppEntry>, categoriser: AppAutoCategoriser = AppAutoCategoriser()) {
        val assignments = appEntries
            .associate { app -> app.identityKey to categoriser.categorise(app.packageName) }
            .filterValues { it.isNotEmpty() }
        setAppCategoryAssignments(assignments)
    }

    fun addCustomCategory(title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return false
        val current = categoryList()
        val candidate = AppCategory.custom(trimmed)
        if (current.any { it.id == candidate.id }) return false
        setCategoryList(current + candidate)
        return true
    }

    fun setCategoryEnabled(id: String, enabled: Boolean) {
        val current = categoryList()
        setCategoryList(current.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun moveCategory(id: String, toIndex: Int) {
        val current = categoryList().toMutableList()
        val fromIndex = current.indexOfFirst { it.id == id }
        if (fromIndex == -1 || toIndex < 0 || toIndex >= current.size) return
        current.add(toIndex, current.removeAt(fromIndex))
        setCategoryList(current)
    }

    fun removeCategory(id: String) {
        val defaultIds = AppCategory.DEFAULTS.map { it.id }.toSet()
        if (id in defaultIds) return
        setCategoryList(categoryList().filter { it.id != id })
        val cleaned = appCategoryAssignments()
            .mapValues { (_, ids) -> ids.filter { it != id } }
            .filterValues { it.isNotEmpty() }
        setAppCategoryAssignments(cleaned)
    }

    fun launcherChangeToken(): Int {
        return listOf(
            uiPreferences(),
            excludedPackages(),
            notificationFilters().toSet(),
            pinnedPackages(),
            hiddenAppKeys(),
            splitNotificationPackages(),
            classicPages(),
            classicGridConfig(),
            dockConfig(),
            dockKeys(),
            pinnedPageEnabled(),
            agendaPageEnabled(),
            alarmsPageEnabled(),
            rssPageEnabled(),
            rssFeedUrls(),
            agendaSectionMode(),
            agendaSectionTitleStyle(),
            fullScreenModeEnabled(),
            appGroupingEnabled(),
            categoryList(),
            appCategoryAssignments(),
        ).hashCode()
    }

    fun useCardIconBackgrounds(): Boolean {
        return preferences.getBoolean(PREF_CARD_ICON_BACKGROUNDS, true)
    }

    fun toggleCardIconBackgrounds(): Boolean {
        val nextValue = !useCardIconBackgrounds()
        preferences.edit().putBoolean(PREF_CARD_ICON_BACKGROUNDS, nextValue).apply()
        return nextValue
    }

    fun cardCornerRadiusDp(): Int {
        return preferences.getInt(PREF_CARD_CORNER_RADIUS, 18).coerceIn(0, 36)
    }

    fun setCardCornerRadiusDp(radius: Int) {
        preferences.edit().putInt(PREF_CARD_CORNER_RADIUS, radius.coerceIn(0, 36)).apply()
    }

    fun cardIconBlur(): Int {
        return preferences.getInt(PREF_CARD_ICON_BLUR, 0).coerceIn(0, 100)
    }

    fun setCardIconBlur(blur: Int) {
        preferences.edit().putInt(PREF_CARD_ICON_BLUR, blur.coerceIn(0, 100)).apply()
    }

    fun focusBlurRadius(): Int {
        return preferences.getInt(PREF_FOCUS_BLUR_RADIUS, 7).coerceIn(0, 24)
    }

    fun setFocusBlurRadius(radius: Int) {
        preferences.edit().putInt(PREF_FOCUS_BLUR_RADIUS, radius.coerceIn(0, 24)).apply()
    }

    fun splitAppsByProfile(): Boolean {
        return preferences.getBoolean(PREF_SPLIT_APPS_BY_PROFILE, false)
    }

    fun toggleSplitAppsByProfile(): Boolean {
        val nextValue = !splitAppsByProfile()
        preferences.edit().putBoolean(PREF_SPLIT_APPS_BY_PROFILE, nextValue).apply()
        return nextValue
    }

    fun placeWorkNotificationChaptersBeforeApps(): Boolean {
        return preferences.getBoolean(PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS, false)
    }

    fun toggleWorkNotificationChaptersBeforeApps(): Boolean {
        val nextValue = !placeWorkNotificationChaptersBeforeApps()
        preferences.edit().putBoolean(PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS, nextValue).apply()
        return nextValue
    }

    fun toggleNotificationSurface(): Boolean {
        val nextValue = !useTintedNotificationCards()
        preferences.edit().putBoolean(PREF_TINT_NOTIFICATION_CARDS, nextValue).apply()
        return nextValue
    }

    fun cardHapticsEnabled(): Boolean {
        return preferences.getBoolean(PREF_CARD_HAPTICS_ENABLED, true)
    }

    fun cardHapticStrength(): Int {
        return preferences.getInt(PREF_CARD_HAPTICS_STRENGTH, 2).coerceIn(1, 5)
    }

    fun setCardHapticStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_HAPTICS_STRENGTH, strength.coerceIn(1, 5)).apply()
    }

    fun toggleCardHaptics(): Boolean {
        val nextValue = !cardHapticsEnabled()
        preferences.edit().putBoolean(PREF_CARD_HAPTICS_ENABLED, nextValue).apply()
        return nextValue
    }

    fun cardStackTuning(): CardStackTuning {
        return CardStackTuning(
            curve = preferences.getInt(PREF_CARD_STACK_CURVE, 50).coerceIn(0, 100),
            horizontalCurve = preferences.getInt(PREF_CARD_STACK_HORIZONTAL_CURVE, 0).coerceIn(-100, 100),
            arcWidth = preferences.getInt(PREF_CARD_STACK_ARC_WIDTH, 50).coerceIn(0, 100),
            aboveFocusCards = preferences.getInt(PREF_CARD_STACK_ABOVE_FOCUS, 2).coerceIn(0, 4),
            rotation = preferences.getInt(PREF_CARD_STACK_ROTATION, 0).coerceIn(0, 100),
            verticalSpacing = preferences.getInt(PREF_CARD_STACK_SPACING, 50).coerceIn(0, 100),
            visibleCards = preferences.getInt(PREF_CARD_STACK_VISIBLE, 3).coerceIn(1, 5),
            focusedCardGap = preferences.getInt(PREF_CARD_STACK_FOCUSED_GAP, 36).coerceIn(0, 100),
            focusedCardScale = preferences.getInt(PREF_CARD_STACK_FOCUSED_SCALE, 32).coerceIn(0, 100),
            magnetStrength = preferences.getInt(PREF_CARD_STACK_MAGNET_STRENGTH, 70).coerceIn(0, 100),
            stackPeakPosition = preferences.getInt(PREF_CARD_STACK_PEAK_POSITION, 20).coerceIn(0, 100),
            nonTopCardOpacity = preferences.getInt(PREF_CARD_STACK_NON_TOP_OPACITY, 100).coerceIn(0, 100),
        )
    }

    fun setNonTopCardOpacity(opacity: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_NON_TOP_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    fun setCardStackCurve(curve: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_CURVE, curve.coerceIn(0, 100)).apply()
    }

    fun setCardStackHorizontalCurve(curve: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_HORIZONTAL_CURVE, curve.coerceIn(-100, 100)).apply()
    }

    fun setCardStackArcWidth(width: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ARC_WIDTH, width.coerceIn(0, 100)).apply()
    }

    fun setAboveFocusCardCount(count: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ABOVE_FOCUS, count.coerceIn(0, 4)).apply()
    }

    fun setCardStackRotation(rotation: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_ROTATION, rotation.coerceIn(0, 100)).apply()
    }

    fun setCardStackSpacing(spacing: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_SPACING, spacing.coerceIn(0, 100)).apply()
    }

    fun setVisibleCardCount(count: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_VISIBLE, count.coerceIn(1, 5)).apply()
    }

    fun setFocusedCardGap(gap: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_FOCUSED_GAP, gap.coerceIn(0, 100)).apply()
    }

    fun setFocusedCardScale(scale: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_FOCUSED_SCALE, scale.coerceIn(0, 100)).apply()
    }

    fun setMagnetStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_MAGNET_STRENGTH, strength.coerceIn(0, 100)).apply()
    }

    fun setStackPeakPosition(position: Int) {
        preferences.edit().putInt(PREF_CARD_STACK_PEAK_POSITION, position.coerceIn(0, 100)).apply()
    }

    fun cardVibrancy(): Int {
        return preferences.getInt(PREF_CARD_VIBRANCY, 50).coerceIn(0, 100)
    }

    fun setCardVibrancy(vibrancy: Int) {
        preferences.edit().putInt(PREF_CARD_VIBRANCY, vibrancy.coerceIn(0, 100)).apply()
    }

    fun fullScreenModeEnabled(): Boolean {
        return preferences.getBoolean(PREF_FULL_SCREEN_MODE, false)
    }

    fun setFullScreenModeEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(PREF_FULL_SCREEN_MODE, enabled).apply()
    }

    fun toggleFullScreenMode(): Boolean {
        val nextValue = !fullScreenModeEnabled()
        setFullScreenModeEnabled(nextValue)
        return nextValue
    }

    fun showAdvancedStackControls(): Boolean {
        return preferences.getBoolean(PREF_CARD_STACK_ADVANCED, false)
    }

    fun toggleAdvancedStackControls(): Boolean {
        val nextValue = !showAdvancedStackControls()
        preferences.edit().putBoolean(PREF_CARD_STACK_ADVANCED, nextValue).apply()
        return nextValue
    }

    fun applyTimescapeStackPreset() {
        preferences.edit()
            .putInt(PREF_CARD_STACK_HORIZONTAL_CURVE, 86)
            .putInt(PREF_CARD_STACK_ARC_WIDTH, 78)
            .putInt(PREF_CARD_STACK_ABOVE_FOCUS, 3)
            .putInt(PREF_CARD_STACK_ROTATION, 24)
            .putInt(PREF_CARD_STACK_CURVE, 76)
            .putInt(PREF_CARD_STACK_SPACING, 42)
            .putInt(PREF_CARD_STACK_VISIBLE, 4)
            .putInt(PREF_CARD_STACK_FOCUSED_GAP, 54)
            .putInt(PREF_CARD_STACK_FOCUSED_SCALE, 42)
            .putInt(PREF_CARD_STACK_MAGNET_STRENGTH, 82)
            .apply()
    }

    fun groupNotifications(packageName: String): Boolean {
        return packageName !in splitNotificationPackages()
    }

    fun toggleNotificationGrouping(packageName: String): Boolean {
        synchronized(mutationLock) {
            val splitPackages = splitNotificationPackages().toMutableSet()
            val nextGrouped = if (packageName in splitPackages) {
                splitPackages.remove(packageName)
                true
            } else {
                splitPackages.add(packageName)
                false
            }
            preferences.edit().putStringSet(PREF_SPLIT_NOTIFICATION_PACKAGES, splitPackages).apply()
            return nextGrouped
        }
    }

    private fun splitNotificationPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_SPLIT_NOTIFICATION_PACKAGES, emptySet()) ?: emptySet())
    }

    private fun nextClassicPageIndex(pages: List<ClassicLauncherPageDefinition>): Int {
        val used = pages.mapNotNull { page ->
            page.id.removePrefix("classic-").toIntOrNull()
        }.toSet()
        var index = 1
        while (index in used) index += 1
        return index
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, defaultValue: T): T {
        return runCatching {
            enumValueOf<T>(preferences.getString(key, defaultValue.name) ?: defaultValue.name)
        }.getOrDefault(defaultValue)
    }

    companion object {
        private const val PREF_EXCLUDED_PACKAGES = "excluded_notification_packages"
        private const val PREF_NOTIFICATION_FILTERS = "notification_filters"
        private const val PREF_PINNED_PACKAGES = "pinned_packages"
        private const val PREF_PINNED_CHAPTER_PACKAGES = "pinned_chapter_packages"
        private const val PREF_HIDDEN_APP_KEYS = "hidden_app_keys"
        private const val PREF_HIDDEN_APP_LABEL_PREFIX = "hidden_app_label_"
        private const val PREF_APP_HUE_PREFIX = "app_hue_"
        private const val PREF_LAUNCHABLE_APPS_SNAPSHOT = "launchable_apps_snapshot"
        private const val PREF_EXCLUDED_LABEL_PREFIX = "excluded_label_"
        private const val PREF_TINT_NOTIFICATION_CARDS = "tint_notification_cards"
        private const val PREF_CARD_ICON_BACKGROUNDS = "card_icon_backgrounds"
        private const val PREF_CARD_CORNER_RADIUS = "card_corner_radius"
        private const val PREF_CARD_ICON_BLUR = "card_icon_blur"
        private const val PREF_FOCUS_BLUR_RADIUS = "focus_blur_radius"
        private const val PREF_SPLIT_APPS_BY_PROFILE = "split_apps_by_profile"
        private const val PREF_WORK_NOTIFICATION_CHAPTERS_BEFORE_APPS = "work_notification_chapters_before_apps"
        private const val PREF_CARD_HAPTICS_ENABLED = "card_haptics_enabled"
        private const val PREF_CARD_HAPTICS_STRENGTH = "card_haptics_strength"
        private const val PREF_SPLIT_NOTIFICATION_PACKAGES = "split_notification_packages"
        private const val PREF_CARD_STACK_CURVE = "card_stack_curve"
        private const val PREF_CARD_STACK_HORIZONTAL_CURVE = "card_stack_horizontal_curve"
        private const val PREF_CARD_STACK_ARC_WIDTH = "card_stack_arc_width"
        private const val PREF_CARD_STACK_ABOVE_FOCUS = "card_stack_above_focus"
        private const val PREF_CARD_STACK_ROTATION = "card_stack_rotation"
        private const val PREF_CARD_STACK_SPACING = "card_stack_spacing"
        private const val PREF_CARD_STACK_VISIBLE = "card_stack_visible"
        private const val PREF_CARD_STACK_FOCUSED_GAP = "card_stack_focused_gap"
        private const val PREF_CARD_STACK_FOCUSED_SCALE = "card_stack_focused_scale"
        private const val PREF_CARD_STACK_MAGNET_STRENGTH = "card_stack_magnet_strength"
        private const val PREF_CARD_STACK_ADVANCED = "card_stack_advanced"
        private const val PREF_CARD_STACK_PEAK_POSITION = "card_stack_peak_position"
        private const val PREF_CARD_STACK_NON_TOP_OPACITY = "card_stack_non_top_opacity"
        private const val PREF_PAGE_LAYOUT_ORDER = "page_layout_order"
        private const val PREF_PAGE_LAYOUT_DISABLED = "page_layout_disabled"
        private const val PREF_PAGE_LAYOUT_HOME = "page_layout_home"
        private const val PREF_CHAPTER_SPINE_TITLE_MODE = "chapter_spine_title_mode"
        private const val PREF_CHAPTER_SPINE_POSITION = "chapter_spine_position"
        private const val PREF_CARD_EFFECT = "card_effect"
        private const val PREF_CARD_EFFECT_STRENGTH = "card_effect_strength"
        private const val PREF_CARD_TINT_STRENGTH = "card_tint_strength"
        private const val PREF_CARD_VIBRANCY = "card_vibrancy"
        private const val PREF_FULL_SCREEN_MODE = "full_screen_mode"
        private const val PREF_PAGE_SORT_ORDER = "page_sort_order"
        private const val PREF_EXPANDED_CARDS = "expanded_cards"
        private const val PREF_PINNED_PAGE = "pinned_page"
        private const val PREF_CONTACTS_PAGE = "contacts_page"
        private const val PREF_AGENDA_PAGE = "agenda_page"
        private const val PREF_ALARMS_PAGE = "alarms_page"
        private const val PREF_RSS_PAGE = "rss_page"
        private const val PREF_RSS_FEED_URLS = "rss_feed_urls"
        private const val PREF_AGENDA_SECTION_MODE = "agenda_section_mode"
        private const val PREF_AGENDA_SECTION_TRANSPARENT = "agenda_section_transparent"
        private const val PREF_AGENDA_SECTION_BOLD = "agenda_section_bold"
        private const val PREF_AGENDA_SECTION_ITALIC = "agenda_section_italic"
        private const val PREF_AGENDA_SECTION_HEIGHT = "agenda_section_height"
        private const val PREF_AGENDA_SECTION_UNDERLINE = "agenda_section_underline"
        private const val PREF_CLASSIC_PAGES = "classic_pages"
        private const val PREF_CLASSIC_HOME_PAGE_ID = "classic_home_page_id"
        private const val PREF_CLASSIC_GRID_COLUMNS = "classic_grid_columns"
        private const val PREF_CLASSIC_GRID_ROWS = "classic_grid_rows"
        private const val PREF_LAST_PAGE_KEY = "last_page_key"
        private const val PREF_DOCK_ENABLED = "dock_enabled"
        private const val PREF_DOCK_STYLE = "dock_style"
        private const val PREF_DOCK_ITEM_COUNT = "dock_item_count"
        private const val PREF_DOCK_ITEM_SPAN = "dock_item_span"
        private const val PREF_DOCK_VERTICAL_PADDING = "dock_vertical_padding"
        private const val PREF_DOCK_HORIZONTAL_PADDING = "dock_horizontal_padding"
        private const val PREF_DOCK_TAP_ACTION = "dock_tap_action"
        private const val PREF_DOCK_LONG_PRESS_ACTION = "dock_long_press_action"
        private const val PREF_DOCK_KEYS = "dock_keys"
        private const val PREF_NOTIF_CARD_TAP_ACTION = "notif_card_tap_action"
        private const val PREF_CATEGORY_LIST = "category_list"
        private const val PREF_APP_CATEGORY_ASSIGNMENTS = "app_category_assignments"
        private const val PREF_APP_GROUPING_ENABLED = "app_grouping_enabled"
    }
}
