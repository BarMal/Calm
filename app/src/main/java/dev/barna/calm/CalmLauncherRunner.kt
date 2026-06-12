package dev.barna.calm

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class CalmLauncherRunner(
    private val activity: MainActivity,
    private val launcherStateViewModel: LauncherStateViewModel,
    requestCalendarPermission: () -> Unit,
    requestContactsPermission: () -> Unit,
    requestWidgetBind: (Intent) -> Unit,
    requestWidgetConfigure: (Intent) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = LauncherSettings(activity)
    private val notificationRepository = NotificationChapterRepository(activity, settings)
    private val calendarRepository = CalendarRepository(activity, requestCalendarPermission)
    private val rssFeedRepository = RssFeedRepository()
    private val contactsRepository = ContactsRepository(activity, requestContactsPermission)
    private val drawables = CalmDrawables(activity)
    private val cardSpec = CalmCardSpec()
    private val pinnedAppResolver = PinnedAppResolver()
    private val resumeRefreshPolicy = ResumeRefreshPolicy()
    private val renderModelFactory = LauncherRenderModelFactory(
        LauncherPageStateFactory(pinnedAppResolver = pinnedAppResolver),
    )
    private val appCardModelFactory = AppCardModelFactory(pinnedAppResolver = pinnedAppResolver)
    private val appCardDisplayCache = AppCardDisplayCache(notificationRepository, appCardModelFactory)
    private val notificationCardDisplayCache = NotificationCardDisplayCache(notificationRepository)
    private val carouselController = ChapterCarouselController(activity, notificationCardDisplayCache, ::navigateToChapterPage)
    private val cardRenderAssetCache = CardRenderAssetCache()
    private val cardRenderer = CardRenderer(activity, drawables, cardSpec, cardRenderAssetCache) { activePreferences }
    private val appLibraryPageModelFactory = AppLibraryPageModelFactory()
    private val appLibraryStore = AppLibraryRenderStore()
    private val appSearchState = AppSearchState(appLibraryPageModelFactory)
    private val appMutationHandler = LauncherAppMutationHandler(
        activity = activity,
        settings = settings,
        render = ::render,
        selectPage = ::selectPage,
        loadPinnedApps = ::loadPinnedApps,
        beginClassicItemPlacement = ::beginClassicItemPlacement,
    )
    private val contextActionFactory = LauncherContextActionFactory(
        callbacks = LauncherContextActionCallbacks(
            openNotification = { notificationActionController.openNotification(it) },
            openPackage = ::openPackage,
            dismissNotificationItem = { notificationActionController.dismissNotificationItem(it) },
            clearChapter = { notificationActionController.clearChapter(it) },
            performNotificationAction = { notificationActionController.performNotificationAction(it) },
            openCalendarEvent = ::openCalendarEvent,
            requestCalendarAccess = { calendarRepository.requestCalendarAccess() },
            openSettings = ::openSettingsActivity,
            openAppEntry = ::openAppEntry,
            pinApp = appMutationHandler::pinApp,
            unpinApp = appMutationHandler::unpinApp,
            openAppInfo = { app -> openAppInfo(app.packageName, app.userHandle, app.componentName) },
            hideApp = appMutationHandler::hideApp,
            appShortcuts = { app -> notificationRepository.getAppShortcuts(app) },
            launchShortcut = { shortcut ->
                if (!notificationRepository.launchShortcut(shortcut)) {
                    Toast.makeText(activity, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
                }
            },
            isDockItem = appMutationHandler::isDockItem,
            addDockItem = appMutationHandler::addDockItem,
            removeDockItem = appMutationHandler::removeDockItem,
            isClassicPageApp = appMutationHandler::isClassicPageApp,
            addAppToClassicPage = appMutationHandler::addAppToClassicPage,
        ),
        labels = LauncherContextActionLabels.from(activity),
    )
    private val cardStackController = CardStackController(activity, mainHandler, ::performCardScrollHaptic)
    private val pageRemovalPlanner = ChapterPageRemovalPlanner()
    private val pageSelectionResolver = ChapterPageSelectionResolver()
    private val pagePrewarmPlanner = ChapterPagePrewarmPlanner()
    private val appQuickScrollController = AppQuickScrollController(
        activity,
        mainHandler,
        drawables,
        cardStackController,
        ::performCardScrollHaptic,
    )
    private val focusOverlay = FocusOverlayController(
        activity,
        mainHandler,
        drawables,
        ::label,
        { currentScreen },
        { activePreferences.focusBlurRadius },
    )
    private val appLibraryController = LauncherAppLibraryController(
        activity = activity,
        cardRenderer = cardRenderer,
        cardStackController = cardStackController,
        appQuickScrollController = appQuickScrollController,
        appCardDisplayCache = appCardDisplayCache,
        contextActionFactory = contextActionFactory,
        focusOverlay = focusOverlay,
        mainHandler = mainHandler,
        activePreferences = { activePreferences },
        currentPager = { currentPager },
        pinnedKeys = { currentUiState?.pinnedKeys ?: settings.pinnedPackages() },
        openAppEntry = ::openAppEntry,
    )
    private val appSearchController = AppSearchController(
        activity = activity,
        mainHandler = mainHandler,
        drawables = drawables,
        appLibraryStore = appLibraryStore,
        appSearchState = appSearchState,
        refreshAppStack = appLibraryController::refreshAppStack,
    )
    private val entryAnimator = LauncherEntryAnimator(activity)
    private val notificationActionController = NotificationActionController(
        activity = activity,
        settings = settings,
        notificationRepository = notificationRepository,
        pageRemovalPlanner = pageRemovalPlanner,
        entryAnimator = entryAnimator,
        render = { render() },
        selectPage = ::selectPage,
        currentPages = { currentUiState?.pages.orEmpty() },
        currentPager = { currentPager },
        openAppInfo = ::openAppInfo,
        openSettings = ::openSettingsActivity,
    )
    private val settingsToggleHandler = LauncherSettingsToggleHandler(
        activity = activity,
        settings = settings,
        render = ::render,
        selectPage = ::selectPage,
        currentPageKey = { selectedPageKey },
        performCardScrollHaptic = ::performCardScrollHaptic,
    )
    private val overviewPageBuilder = OverviewPageBuilder(
        activity = activity,
        drawables = drawables,
        cardRenderer = cardRenderer,
        cardStackController = cardStackController,
        notificationCardDisplayCache = notificationCardDisplayCache,
        contextActionFactory = contextActionFactory,
        focusOverlay = focusOverlay,
        calendarRepository = calendarRepository,
        activePreferences = { activePreferences },
        createBarePagePanel = ::createBarePagePanel,
        openSettingsActivity = ::openSettingsActivity,
        openCalendarEvent = ::openCalendarEvent,
    )
    private val agendaPageBuilder = AgendaPageBuilder(
        activity = activity,
        cardRenderer = cardRenderer,
        cardStackController = cardStackController,
        calendarRepository = calendarRepository,
        contextActionFactory = contextActionFactory,
        focusOverlay = focusOverlay,
        activePreferences = { activePreferences },
        barePagePanel = ::createBarePagePanel,
        render = ::render,
        openSectionCardSettings = ::openSectionCardSettingsActivity,
    )
    private val alarmsPageBuilder = AlarmsPageBuilder(
        activity = activity,
        cardRenderer = cardRenderer,
        activePreferences = { activePreferences },
        barePagePanel = ::createBarePagePanel,
    )
    private val rssPageBuilder = RssPageBuilder(
        activity = activity,
        cardRenderer = cardRenderer,
        cardStackController = cardStackController,
        activePreferences = { activePreferences },
        barePagePanel = ::createBarePagePanel,
        label = ::label,
    )
    private val stateExecutor = Executors.newFixedThreadPool(4)
    private val chapterPageBuilder = ChapterPageBuilder(
        activity = activity,
        drawables = drawables,
        settings = settings,
        notificationCardDisplayCache = notificationCardDisplayCache,
        notificationRepository = notificationRepository,
        cardStackController = cardStackController,
        cardRenderer = cardRenderer,
        notificationActionController = notificationActionController,
        contextActionFactory = contextActionFactory,
        focusOverlay = focusOverlay,
        activePreferences = { activePreferences },
        createPagePanel = ::createPagePanel,
        createBarePagePanel = ::createBarePagePanel,
        openPackage = ::openPackage,
        toggleNotificationGrouping = settingsToggleHandler::toggleNotificationGrouping,
    )
    private val contactsPageController = ContactsPageController(
        activity = activity,
        contactsRepository = contactsRepository,
        drawables = drawables,
        cardRenderer = cardRenderer,
        cardStackController = cardStackController,
        focusOverlay = focusOverlay,
        mainHandler = mainHandler,
        backgroundExecutor = stateExecutor,
        activePreferences = { activePreferences },
        barePagePanel = ::createBarePagePanel,
        label = ::label,
    )
    private val dockController = DockController(
        activity = activity,
        drawables = drawables,
        resolveIcon = { notificationRepository.resolveAppIconBitmap(it) },
        openAppEntry = ::openAppEntry,
        openNotification = { notification -> notificationActionController.openNotification(notification) },
        openNotificationPage = { chapter ->
            val pageIndex = currentUiState?.pages.orEmpty().indexOfFirst { page -> page.key == chapter.identityKey }
            if (pageIndex >= 0) {
                navigateToChapterPage(pageIndex)
            } else {
                selectPage(chapter.identityKey)
            }
        },
        showContextMenu = { source, app, target, anchor ->
            val actions = ArrayList<ContextAction>()
            val latestNotification = target?.chapter?.notifications.orEmpty().maxByOrNull { notification -> notification.postTime }
            latestNotification?.let { notification ->
                actions.add(ContextAction(
                    activity.getString(R.string.action_open_notification),
                    Runnable { notificationActionController.openNotification(notification) },
                ))
            }
            target?.chapter?.let { chapter ->
                actions.add(ContextAction(activity.getString(R.string.action_expand_notifications), Runnable {
                    val pageIndex = currentUiState?.pages.orEmpty().indexOfFirst { page -> page.key == chapter.identityKey }
                    if (pageIndex >= 0) {
                        navigateToChapterPage(pageIndex)
                    } else {
                        selectPage(chapter.identityKey)
                    }
                }))
            }
            val pinned = app.identityKey in settings.pinnedPackages() || app.packageName in settings.pinnedPackages()
            actions.addAll(contextActionFactory.appActions(app, pinned))
            GoogleInteractionStyle.popupMenu(
                context = activity,
                source = source,
                anchor = anchor,
                actions = actions,
                destructiveLabels = setOf(
                    activity.getString(R.string.action_remove_from_dock),
                    activity.getString(R.string.action_hide),
                    activity.getString(R.string.action_dismiss),
                    activity.getString(R.string.action_clear),
                ),
            )
        },
    )
    private val classicWidgetHostController = ClassicWidgetHostController(
        activity = activity,
        settings = settings,
        requestWidgetBind = requestWidgetBind,
        requestWidgetConfigure = requestWidgetConfigure,
        render = ::render,
        selectPage = ::selectPage,
        beginClassicItemPlacement = ::beginClassicItemPlacement,
    )
    private val pageFactory = LauncherPageFactory(
        activity = activity,
        drawables = drawables,
        focusOverlay = focusOverlay,
        cardRenderer = cardRenderer,
        overviewPageBuilder = overviewPageBuilder,
        agendaPageBuilder = agendaPageBuilder,
        alarmsPageBuilder = alarmsPageBuilder,
        rssPageBuilder = rssPageBuilder,
        chapterPageBuilder = chapterPageBuilder,
        appLibraryController = appLibraryController,
        appSearchController = appSearchController,
        appLibraryPageModelFactory = appLibraryPageModelFactory,
        appLibraryStore = appLibraryStore,
        contactsPageController = contactsPageController,
        resolveIcon = { notificationRepository.resolveAppIconBitmap(it) },
        openAppEntry = ::openAppEntry,
        createWidgetView = classicWidgetHostController::createWidgetView,
        addAppToClassicPage = appMutationHandler::addAppToClassicPage,
        addWidgetToClassicPage = classicWidgetHostController::requestAddWidget,
        addStaticItemToClassicPage = ::addStaticItemToClassicPage,
        configureClassicWidget = classicWidgetHostController::requestConfigureWidget,
        canConfigureClassicWidget = classicWidgetHostController::canConfigureWidget,
        removeClassicGridItem = ::removeClassicGridItem,
        moveClassicGridItem = ::moveClassicGridItem,
        moveClassicGridItemWithinPage = ::moveClassicGridItemWithinPage,
        resizeClassicGridItem = ::resizeClassicGridItem,
        resetClassicGridItemSize = ::resetClassicGridItemSize,
        pendingClassicPlacementItemId = { pendingClassicPlacementItemId },
        finishClassicItemPlacement = ::finishClassicItemPlacement,
        addClassicPage = ::addClassicPage,
        moveClassicPage = ::moveClassicPage,
        isClassicPageEditing = ::isClassicPageEditing,
        setClassicPageEditing = ::setClassicPageEditing,
        renameClassicPage = ::renameClassicPage,
        setDefaultClassicPage = ::setDefaultClassicPage,
        removeClassicPage = ::removeClassicPage,
        barePagePanel = ::createBarePagePanel,
        label = ::label,
    )
    private val appLibraryDataManager = LauncherAppLibraryDataManager(
        notificationRepository = notificationRepository,
        settings = settings,
        appLibraryStore = appLibraryStore,
        appCardDisplayCache = appCardDisplayCache,
        notificationCardDisplayCache = notificationCardDisplayCache,
        appSearchController = appSearchController,
        mainHandler = mainHandler,
        executor = stateExecutor,
    )
    private val stateManager = LauncherStateManager(
        notificationRepository = notificationRepository,
        calendarRepository = calendarRepository,
        rssFeedRepository = rssFeedRepository,
        settings = settings,
        renderModelFactory = renderModelFactory,
        appCardDisplayCache = appCardDisplayCache,
        mainHandler = mainHandler,
        executor = stateExecutor,
        loadAppEntries = appLibraryDataManager::loadAppEntries,
        loadCachedAppEntries = appLibraryDataManager::loadCachedAppEntries,
        markLoading = launcherStateViewModel::markLoading,
        onStateReady = { state -> render(state, animate = false) },
    )
    private val deferredRender = Runnable { stateManager.refreshAsync() }
    private val notificationRefresh = Runnable {
        notificationCardDisplayCache.clear()
        requestRender()
    }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidateAppDataAndRefresh()
        }
    }

    private var selectedPageKey = launcherStateViewModel.uiState.value.selectedPageKey
        ?: settings.lastSelectedPageKey()
        ?: defaultHomeKey()
    private var currentPager: ViewPager2? = null
    private var currentPagerAdapter: ChapterPagerAdapter? = null
    private var currentScreen: FrameLayout? = null
    private var pageOverviewOverlay: View? = null
    private val pageOverviewHiddenViews = ArrayList<View>()
    private var reopenPageOverviewAfterRender = false
    private var reopenPageOverviewFocusKey: String? = null
    private val currentUiState: LauncherRenderModel?
        get() = launcherStateViewModel.uiState.value.renderModel
    private var activePreferences: LauncherUiPreferences = settings.uiPreferences()
    private var appCardSettingsSnapshot: LauncherUiPreferences = activePreferences
    private var settingsChangeToken = settings.launcherChangeToken()
    private var renderedNotificationRevision = CalmNotificationListenerService.revision()
    private var packageChangeReceiverRegistered = false
    private var pagePrewarmGeneration = 0
    private var suppressedPageEntryKey: String? = null
    private var editingClassicPageId: String? = null
    private var pendingClassicPlacementItemId: String? = null

    fun onCreate() {
        configureWindow()
        registerPackageChangeReceiver()
        notificationRepository.setOnHueResolved(Runnable {
            notificationRepository.invalidateLaunchableApps()
            appCardDisplayCache.clear()
            notificationCardDisplayCache.clear()
            requestRender()
        })
        val existingState = currentUiState
        if (existingState != null) {
            render(existingState, animate = false)
            stateManager.refreshAsync()
        } else {
            render(stateManager.buildCachedShell(), animate = true)
            stateManager.refreshAsync()
        }
        appLibraryDataManager.refreshInBackground()
    }

    fun onResume() {
        classicWidgetHostController.startListening()
        CalmNotificationListenerService.addListener(notificationRefresh)
        val hasCurrentScreen = currentScreen != null
        val hasCurrentState = currentUiState != null
        val launcherSettingsChanged = settingsChangeToken != settings.launcherChangeToken()
        // Notifications can be dismissed while paused (e.g. opening an app clears its notifications).
        // The listener service tracks the live snapshot regardless of foreground state, so compare
        // its revision against what we last rendered to drop stale cards on resume.
        val notificationsChanged = renderedNotificationRevision != CalmNotificationListenerService.revision()
        if (resumeRefreshPolicy.shouldRefreshImmediately(
                hasCurrentScreen = hasCurrentScreen,
                hasCurrentState = hasCurrentState,
                launcherSettingsChanged = launcherSettingsChanged,
                notificationsChanged = notificationsChanged,
            )
        ) {
            if (hasCurrentScreen && hasCurrentState) {
                stateManager.refreshAsync()
                return
            }
            render(stateManager.buildCachedShell(), animate = true)
            stateManager.refreshAsync()
        }
    }

    fun onPause() {
        classicWidgetHostController.stopListening()
        CalmNotificationListenerService.removeListener(notificationRefresh)
    }

    fun onDestroy() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            classicWidgetHostController.shutdown()
            stateExecutor.shutdown()
        } finally {
            unregisterPackageChangeReceiver()
        }
    }

    fun onCalendarPermissionResult() {
        stateManager.refreshAsync()
    }

    fun onContactsPermissionResult() {
        render()
    }

    fun onWidgetBindResult(resultCode: Int, data: Intent?) {
        classicWidgetHostController.onWidgetBindResult(resultCode, data)
    }

    fun onWidgetConfigureResult(resultCode: Int, data: Intent?) {
        val handledPendingAdd = classicWidgetHostController.onWidgetConfigureResult(resultCode, data)
        if (!handledPendingAdd) {
            render()
        }
    }

    private fun removeClassicGridItem(page: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val removed = settings.removeClassicGridItem(page.id, item.id) ?: return
        if (removed.type == ClassicGridItemType.WIDGET) {
            classicWidgetHostController.deleteWidget(removed)
        }
        Toast.makeText(activity, "Removed from ${page.title}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun moveClassicGridItem(
        sourcePage: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        targetPage: ClassicLauncherPageDefinition,
    ) {
        val moved = settings.moveClassicGridItem(sourcePage.id, item.id, targetPage.id)
        if (!moved) {
            Toast.makeText(activity, "${targetPage.title} is full", Toast.LENGTH_SHORT).show()
            return
        }
        selectPage(targetPage.key)
        Toast.makeText(activity, "Moved to ${targetPage.title}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun moveClassicGridItemWithinPage(
        page: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        x: Int,
        y: Int,
    ) {
        if (item.x == x && item.y == y) {
            Toast.makeText(activity, "Already there", Toast.LENGTH_SHORT).show()
            return
        }
        if (!settings.moveClassicGridItemWithinPage(page.id, item.id, x, y)) {
            Toast.makeText(activity, "Position unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "Moved", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun resizeClassicGridItem(
        page: ClassicLauncherPageDefinition,
        item: ClassicGridItem,
        width: Int,
        height: Int,
    ) {
        if (item.width == width && item.height == height) {
            Toast.makeText(activity, "Already that size", Toast.LENGTH_SHORT).show()
            return
        }
        val resized = settings.resizeClassicGridItem(page.id, item.id, width, height)
        if (!resized) {
            Toast.makeText(activity, "No room for that size", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "Resized", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun resetClassicGridItemSize(page: ClassicLauncherPageDefinition, item: ClassicGridItem) {
        val gridConfig = settings.classicGridConfig()
        val defaultSize = when (item.type) {
            ClassicGridItemType.APP -> 1 to 1
            ClassicGridItemType.STATIC -> gridConfig.columns to 1
            ClassicGridItemType.WIDGET -> item.target.toIntOrNull()
                ?.let { widgetId -> classicWidgetHostController.defaultWidgetSpan(widgetId) }
                ?: (gridConfig.columns to 2)
        }
        resizeClassicGridItem(page, item, defaultSize.first, defaultSize.second)
    }

    private fun beginClassicItemPlacement(page: ClassicLauncherPageDefinition, itemId: String) {
        editingClassicPageId = page.id
        pendingClassicPlacementItemId = itemId
    }

    private fun finishClassicItemPlacement(itemId: String) {
        if (pendingClassicPlacementItemId == itemId) {
            pendingClassicPlacementItemId = null
        }
    }

    private fun addClassicPage() {
        val page = settings.addClassicPage()
        editingClassicPageId = page.id
        selectPage(page.key)
        Toast.makeText(activity, "Added ${page.title}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun addStaticItemToClassicPage(page: ClassicLauncherPageDefinition, staticItem: ClassicStaticItem) {
        if (settings.addStaticItemToClassicPage(page.id, staticItem)) {
            beginClassicItemPlacement(page, ClassicGridItem.static(staticItem, x = 0, y = 0).id)
            Toast.makeText(activity, "Added ${staticItem.displayLabel} to ${page.title}", Toast.LENGTH_SHORT).show()
            render()
        } else {
            Toast.makeText(activity, "No room for ${staticItem.displayLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    private val ClassicStaticItem.displayLabel: String
        get() = when (this) {
            ClassicStaticItem.CLOCK -> "Clock"
            ClassicStaticItem.SEARCH -> "Search"
        }

    private fun moveClassicPage(page: ClassicLauncherPageDefinition, targetIndex: Int) {
        if (!settings.moveClassicPage(page.id, targetIndex)) return
        selectPage(page.key)
        Toast.makeText(activity, "Moved ${page.title}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun isClassicPageEditing(page: ClassicLauncherPageDefinition): Boolean {
        return editingClassicPageId == page.id
    }

    private fun setClassicPageEditing(page: ClassicLauncherPageDefinition, editing: Boolean) {
        editingClassicPageId = if (editing) page.id else null
        if (!editing) pendingClassicPlacementItemId = null
        render()
    }

    private fun renameClassicPage(page: ClassicLauncherPageDefinition, title: String) {
        if (!settings.renameClassicPage(page.id, title)) {
            Toast.makeText(activity, "Page name can't be empty", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "Renamed page", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun setDefaultClassicPage(page: ClassicLauncherPageDefinition) {
        if (!settings.setDefaultClassicPage(page.id)) return
        settings.setDefaultHomeSlot(PageSlot.CLASSIC_PAGES)
        Toast.makeText(activity, "${page.title} is now home", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun removeClassicPage(page: ClassicLauncherPageDefinition) {
        val removed = settings.removeClassicPage(page.id) ?: return
        if (editingClassicPageId == removed.id) {
            editingClassicPageId = null
        }
        pendingClassicPlacementItemId = null
        removed.items
            .filter { item -> item.type == ClassicGridItemType.WIDGET }
            .forEach(classicWidgetHostController::deleteWidget)
        Toast.makeText(activity, "Removed ${removed.title}", Toast.LENGTH_SHORT).show()
        render()
    }

    /** Dismisses the expanded/focus card on back so it returns to the current page, not overview. */
    fun onBackPressed() {
        if (pageOverviewOverlay != null) {
            hidePageOverview()
            return
        }
        if (focusOverlay.isShowing()) {
            focusOverlay.dismiss(true)
        }
    }

    private fun configureWindow() {
        CalmSystemBars.applyTransparentWallpaper(activity)
    }

    private fun render() {
        requestRender()
    }

    private fun render(state: LauncherRenderModel, animate: Boolean) {
        mainHandler.removeCallbacks(deferredRender)
        focusOverlay.dismiss(false)
        pageOverviewOverlay = null
        pageOverviewHiddenViews.clear()
        appSearchController.clear()
        launcherStateViewModel.publish(state)
        if (appCardSettingsSnapshot != state.preferences) {
            appCardSettingsSnapshot = state.preferences
        }
        appCardDisplayCache.preload(state.appEntries, state.pinnedKeys, stateExecutor)
        notificationCardDisplayCache.preload(
            state.notificationChapters,
            { chapter -> settings.groupNotifications(chapter.identityKey) },
            ::formatNotificationTime,
            stateExecutor,
        )
        activePreferences = state.preferences
        settingsChangeToken = settings.launcherChangeToken()
        renderedNotificationRevision = CalmNotificationListenerService.revision()
        val pages = state.pages
        val initialPage = resolveInitialPage(pages)
        val reopenOverview = reopenPageOverviewAfterRender
        val reopenOverviewFocusKey = reopenPageOverviewFocusKey
        reopenPageOverviewAfterRender = false
        reopenPageOverviewFocusKey = null

        val screen = object : FrameLayout(activity) {
            private var overviewScale = 1f
            private val scaleDetector = ScaleGestureDetector(
                activity,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        overviewScale = 1f
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        overviewScale *= detector.scaleFactor
                        if (overviewScale < PAGE_OVERVIEW_PINCH_THRESHOLD && pageOverviewOverlay == null) {
                            val currentIndex = currentPager?.currentItem ?: initialPage
                            currentScreen?.let { screen -> showPageOverview(screen, state, pages, currentIndex) }
                            return true
                        }
                        return false
                    }
                },
            )

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                    scaleDetector.onTouchEvent(event)
                    if (pageOverviewOverlay != null) return true
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            currentScreen = this
            setBackgroundColor(Color.TRANSPARENT)
            addView(View(activity).apply { background = drawables.wallpaperShade() }, matchParentParams())
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, activity.statusBarHeightFallback() + activity.dp(28), 0, activity.dp(34))
        }
        screen.addView(root, matchParentParams())
        root.addView(createHeader())

        val pagerAdapter = ChapterPagerAdapter(pages) { page -> pageFactory.createPage(page, state) }
        currentPagerAdapter = pagerAdapter
        val pager = ViewPager2(activity).apply {
            currentPager = this
            adapter = pagerAdapter
            clipToPadding = false
            clipChildren = false
            offscreenPageLimit = minOf(2, (pages.size - 1).coerceAtLeast(1))
            setPageTransformer(CompositePageTransformer().apply {
                addTransformer { page, position ->
                    val distance = minOf(1f, kotlin.math.abs(position))
                    page.alpha = 1f - (0.08f * distance)
                    page.scaleX = 1f
                    page.scaleY = 1f
                    page.translationX = position * activity.dp(10).toFloat()
                    page.translationY = 0f
                    page.translationZ = 0f
                }
            })
        }
        (pager.getChildAt(0) as? RecyclerView)?.apply {
            clipToPadding = false
            clipChildren = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        pager.setCurrentItem(initialPage, false)
        if (!animate) {
            suppressedPageEntryKey = pages.getOrNull(initialPage)?.key
        }
        var previousPageIndex = initialPage
        var currentNavigationDirection = 0
        val animTrigger = PageScrollAnimationTrigger()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                carouselController.scrollToPosition(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                val prev = previousPageIndex
                currentNavigationDirection = when {
                    position > prev -> 1
                    position < prev -> -1
                    else -> 0
                }
                previousPageIndex = position
                if (animTrigger.isSwipeInProgress && prev != position) {
                    entryAnimator.animatePageExit(pager, prev)
                    // Trigger entry animation now — pager.currentItem reflects the new page here,
                    // but SCROLL_STATE_SETTLING fires before onPageSelected updates currentItem,
                    // so triggering at SETTLING would animate the wrong (outgoing) page.
                    val direction = currentNavigationDirection
                    animTrigger.onSwipePageChanged(pages[position].key, suppressedPageEntryKey)
                        ?.let { pager.post { entryAnimator.animateCurrentPage(pager, direction) } }
                }
                selectPage(pages[position].key)
                if (suppressedPageEntryKey != selectedPageKey) {
                    suppressedPageEntryKey = null
                }
                appSearchController.resetInactiveExcept(selectedPageKey)
            }

            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        pagePrewarmGeneration++
                        animTrigger.onDragging()
                    }
                    ViewPager2.SCROLL_STATE_SETTLING -> {
                        // Only fires an animation if onSwipePageChanged didn't already fire one.
                        // Handles swipe-back-to-same-page and overscroll past last page.
                        val direction = currentNavigationDirection
                        animTrigger.onSettling(pages[pager.currentItem].key, suppressedPageEntryKey)
                            ?.let { pager.post { entryAnimator.animateCurrentPage(pager, direction) } }
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        val currentPage = pages[pager.currentItem]
                        carouselController.update(pages, pager.currentItem)
                        appSearchController.resetInactiveExcept(currentPage.key)
                        val direction = currentNavigationDirection
                        animTrigger.onIdle(currentPage.key, suppressedPageEntryKey)
                            ?.let { pager.post { entryAnimator.animateCurrentPage(pager, direction) } }
                        if (suppressedPageEntryKey == currentPage.key) {
                            suppressedPageEntryKey = null
                        }
                        schedulePagePrewarm(
                            pager,
                            pagerAdapter,
                            pages.size,
                            pager.currentItem,
                            PAGE_PREWARM_AFTER_NAVIGATION_DELAY_MS,
                        )
                    }
                }
            }
        })

        root.addView(carouselController.create(pages, initialPage))
        root.addView(
            pager,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        if (state.dockConfig.enabled && state.dockApps.isNotEmpty()) {
            root.addView(
                dockController.buildDock(state.dockApps, state.dockConfig, state.notificationChapters),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = activity.dp(16)
                    rightMargin = activity.dp(16)
                    topMargin = activity.dp(10)
                },
            )
        }
        carouselController.update(pages, initialPage)
        activity.setContentView(screen)
        if (animate) {
            pager.post { entryAnimator.animateCurrentPage(pager) }
        }
        schedulePagePrewarm(pager, pagerAdapter, pages.size, initialPage, PAGE_PREWARM_INITIAL_DELAY_MS)
        if (reopenOverview) {
            screen.post { showPageOverview(screen, state, pages, pager.currentItem, haptic = false, focusPageKey = reopenOverviewFocusKey) }
        }
    }

    private fun resolveInitialPage(pages: List<ChapterPage>): Int {
        val selection = pageSelectionResolver.resolve(pages, selectedPageKey)
        if (selection.key != selectedPageKey) {
            selectPage(selection.key)
        }
        return selection.index
    }

    private fun selectPage(pageKey: String) {
        selectedPageKey = pageKey
        launcherStateViewModel.selectPage(pageKey)
        settings.setLastSelectedPageKey(pageKey)
    }

    // The fixed-key landing page for the configured default-home slot, used only when no page has
    // been visited yet. NOTIFICATIONS has no fixed key, so it falls back to the overview.
    private fun defaultHomeKey(): String = when (settings.pageLayout().defaultHome) {
        PageSlot.OVERVIEW -> CalmTheme.OVERVIEW_KEY
        PageSlot.WORK_OVERVIEW -> CalmTheme.WORK_OVERVIEW_KEY
        PageSlot.PINNED -> CalmTheme.PINNED_KEY
        PageSlot.CONTACTS -> CalmTheme.CONTACTS_KEY
        PageSlot.AGENDA -> CalmTheme.AGENDA_KEY
        PageSlot.ALARMS -> CalmTheme.ALARMS_KEY
        PageSlot.RSS -> CalmTheme.RSS_KEY
        PageSlot.APPS -> CalmTheme.APP_LIBRARY_KEY
        PageSlot.CLASSIC_PAGES -> settings.homeClassicPage()?.key ?: CalmTheme.OVERVIEW_KEY
        PageSlot.NOTIFICATIONS -> CalmTheme.OVERVIEW_KEY
    }

    private fun schedulePagePrewarm(
        pager: ViewPager2,
        adapter: ChapterPagerAdapter,
        pageCount: Int,
        initialPage: Int,
        initialDelayMs: Long,
    ) {
        val generation = ++pagePrewarmGeneration
        if (pageCount <= 1) return
        val positions = pagePrewarmPlanner.positions(pageCount, initialPage, PAGE_PREWARM_MAX_PAGES)
        scheduleNextPagePrewarm(pager, adapter, positions, generation, 0, initialDelayMs)
    }

    private fun scheduleNextPagePrewarm(
        pager: ViewPager2,
        adapter: ChapterPagerAdapter,
        positions: List<Int>,
        generation: Int,
        offset: Int,
        delayMs: Long,
    ) {
        if (offset >= positions.size) return
        mainHandler.postDelayed({
            if (generation != pagePrewarmGeneration) return@postDelayed
            val recycler = pager.getChildAt(0) as? RecyclerView
            if (
                !pager.isAttachedToWindow ||
                pager.scrollState != ViewPager2.SCROLL_STATE_IDLE ||
                recycler?.isComputingLayout == true
            ) {
                scheduleNextPagePrewarm(pager, adapter, positions, generation, offset, PAGE_PREWARM_STEP_DELAY_MS)
                return@postDelayed
            }
            adapter.preload(positions[offset])
            scheduleNextPagePrewarm(pager, adapter, positions, generation, offset + 1, PAGE_PREWARM_STEP_DELAY_MS)
        }, delayMs)
    }

    private fun requestRender() {
        mainHandler.removeCallbacks(deferredRender)
        mainHandler.postDelayed(deferredRender, 90)
    }

    private fun createHeader(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(2), activity.dp(20), activity.dp(2))
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
            addView(TextClock(activity).apply {
                format12Hour = "h:mm"
                format24Hour = "HH:mm"
                setTextColor(CalmTheme.INK)
                textSize = 38f
                typeface = Typeface.DEFAULT
                includeFontPadding = false
            })
            addView(TextClock(activity).apply {
                format12Hour = "EEE, MMM d"
                format24Hour = "EEE, MMM d"
                setTextColor(CalmTheme.MUTED_INK)
                textSize = 13f
                typeface = Typeface.DEFAULT
                includeFontPadding = false
                setPadding(0, activity.dp(3), 0, 0)
            })
        }
    }

    private fun createPagePanel(backgroundImage: android.graphics.Bitmap?, hueColor: Int): LinearLayout {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.PAGE_PANEL
            val glassColor = CardVibrancyLevel.applyTo(CalmTheme.GLASS, activePreferences.cardVibrancy)
            background = if (backgroundImage != null) {
                drawables.glassWithImage(glassColor, activity.dp(22), backgroundImage, hueColor)
            } else if (hueColor != 0) {
                drawables.glassWithHue(glassColor, activity.dp(22), hueColor)
            } else {
                drawables.glass(glassColor, activity.dp(22))
            }
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(20), activity.dp(28), activity.dp(20), activity.dp(30))
            elevation = activity.dp(1).toFloat()
            translationZ = 0f
            layoutParams = pageParams()
        }
    }

    private fun createBarePagePanel(horizontalPadding: Int = activity.dp(10)): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(horizontalPadding, activity.dp(28), horizontalPadding, activity.dp(30))
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
            translationZ = 0f
            layoutParams = pageParams()
        }
    }

    private fun pageParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = activity.dp(20)
            bottomMargin = activity.dp(18)
        }
    }

    private fun navigateToChapterPage(index: Int) {
        val pager = currentPager ?: return
        val current = pager.currentItem
        if (index == current) return
        currentUiState?.pages?.getOrNull(index)?.key?.let(::selectPage)
        val smooth = kotlin.math.abs(index - current) == 1
        if (!smooth) {
            suppressedPageEntryKey = currentUiState?.pages?.getOrNull(index)?.key
        }
        pager.setCurrentItem(index, smooth)
        if (!smooth) {
            currentUiState?.pages?.let { pages ->
                carouselController.update(pages, index)
                appSearchController.resetInactiveExcept(pages[index].key)
            }
        }
    }

    private fun showPageOverview(
        screen: FrameLayout,
        state: LauncherRenderModel,
        pages: List<ChapterPage>,
        selectedIndex: Int,
        haptic: Boolean = true,
        focusPageKey: String? = null,
    ) {
        if (pageOverviewOverlay != null || pages.isEmpty()) return
        focusOverlay.dismiss(false)
        hideLauncherContentForPageOverview(screen)
        if (haptic) screen.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val overlay = FrameLayout(activity).apply {
            alpha = 0f
            isClickable = true
            setBackgroundColor(Color.argb(92, 0, 0, 0))
            setOnClickListener { hidePageOverview() }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(18), activity.statusBarHeightFallback() + activity.dp(26), activity.dp(18), activity.dp(28))
        }
        content.addView(pageOverviewHeader())
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(6), 0, activity.dp(18), 0)
        }
        row.setOnClickListener { }
        val cardWidth = (activity.resources.displayMetrics.widthPixels * 0.66f).toInt()
            .coerceIn(activity.dp(224), activity.dp(320))
        val cardHeight = (activity.resources.displayMetrics.heightPixels * 0.58f).toInt()
            .coerceIn(activity.dp(360), activity.dp(540))
        val snapshotAdapter = currentPagerAdapter
        val entries = pageOverviewEntries(pages)
        val selectedEntryIndex = pageOverviewSelectedEntryIndex(entries, selectedIndex)
        val focusEntryIndex = focusPageKey
            ?.let { key -> pages.indexOfFirst { page -> page.key == key } }
            ?.takeIf { index -> index >= 0 }
            ?.let { index -> pageOverviewSelectedEntryIndex(entries, index) }
            ?.takeIf { index -> index >= 0 }
            ?: selectedEntryIndex
        entries.forEachIndexed { entryIndex, entry ->
            val card = when (entry) {
                is PageOverviewEntry.Page -> {
                    pageOverviewCard(entry.page, state, entry.pageIndex, pages, snapshotAdapter, cardWidth, cardHeight, entry.pageIndex == selectedIndex).also {
                        installPageOverviewCardGestures(it, entry.page, entry.pageIndex, entryIndex, entries, pages, state, cardWidth)
                    }
                }
                is PageOverviewEntry.NotificationBundle -> {
                    pageOverviewNotificationBundleCard(entry, cardWidth, cardHeight, entry.containsPageIndex(selectedIndex)).also {
                        installPageOverviewNotificationBundleGestures(it, entry)
                    }
                }
            }
            row.addView(
                card,
                LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                    rightMargin = activity.dp(14)
                },
            )
        }
        row.addView(pageOverviewAddCard("New page", "Choose page type", cardWidth, cardHeight) { source ->
            showPageOverviewAddMenu(source, state, pages)
        }, LinearLayout.LayoutParams(cardWidth, cardHeight).apply { rightMargin = activity.dp(14) })
        val scroller = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { }
        }
        installPageOverviewScrollMagnet(scroller, cardWidth)
        content.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        overlay.addView(content, matchParentParams())
        screen.addView(overlay, matchParentParams())
        pageOverviewOverlay = overlay
        overlay.animate().alpha(1f).setDuration(160L).start()
        scroller.post {
            scrollPageOverviewToCard(scroller, focusEntryIndex, cardWidth, smooth = false)
        }
    }

    private fun hidePageOverview() {
        val overlay = pageOverviewOverlay ?: return
        pageOverviewOverlay = null
        restoreLauncherContentAfterPageOverview()
        overlay.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction { (overlay.parent as? ViewGroup)?.removeView(overlay) }
            .start()
    }

    private fun hideLauncherContentForPageOverview(screen: FrameLayout) {
        pageOverviewHiddenViews.clear()
        for (index in 1 until screen.childCount) {
            val child = screen.getChildAt(index)
            if (child === pageOverviewOverlay) continue
            pageOverviewHiddenViews += child
            child.animate().cancel()
            child.alpha = 0f
        }
    }

    private fun restoreLauncherContentAfterPageOverview() {
        pageOverviewHiddenViews.forEach { view ->
            if (view.parent != null) {
                view.animate().cancel()
                view.alpha = 1f
            }
        }
        pageOverviewHiddenViews.clear()
    }

    private fun pageOverviewHeader(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(4), 0, 0, activity.dp(18))
            addView(label("Pages", 28, CalmTheme.INK, Typeface.NORMAL), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun pageOverviewEntries(pages: List<ChapterPage>): List<PageOverviewEntry> {
        val dynamicEntries = pages.withIndex()
            .filter { indexed -> indexed.value.isDynamicOverviewPage() }
            .map { indexed -> PageOverviewEntry.Page(indexed.index, indexed.value) }
        if (dynamicEntries.isEmpty()) {
            return pages.mapIndexed { index, page -> PageOverviewEntry.Page(index, page) }
        }
        val result = ArrayList<PageOverviewEntry>()
        val overviewIndex = pages.indexOfFirst { page -> PageArranger.slotOf(page) == PageSlot.OVERVIEW }
        var bundleInserted = false
        pages.forEachIndexed { index, page ->
            if (page.isDynamicOverviewPage()) return@forEachIndexed
            result += PageOverviewEntry.Page(index, page)
            if (index == overviewIndex) {
                result += PageOverviewEntry.NotificationBundle(dynamicEntries)
                bundleInserted = true
            }
        }
        if (!bundleInserted) {
            val firstDynamicIndex = dynamicEntries.first().pageIndex
            val insertIndex = result.indexOfFirst { entry -> entry.firstPageIndex > firstDynamicIndex }.takeIf { it != -1 }
                ?: result.size
            result.add(insertIndex, PageOverviewEntry.NotificationBundle(dynamicEntries))
        }
        return result
    }

    private fun pageOverviewSelectedEntryIndex(entries: List<PageOverviewEntry>, selectedPageIndex: Int): Int {
        return entries.indexOfFirst { entry -> entry.containsPageIndex(selectedPageIndex) }
            .takeIf { it != -1 }
            ?: selectedPageIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    }

    private fun ChapterPage.isDynamicOverviewPage(): Boolean {
        return when (PageArranger.slotOf(this)) {
            PageSlot.NOTIFICATIONS -> true
            else -> false
        }
    }

    private fun pageOverviewAddCard(
        title: String,
        subtitle: String,
        cardWidth: Int,
        cardHeight: Int,
        action: (View) -> Unit,
    ): View {
        val colors = GoogleInteractionStyle.palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = activity.dp(26).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(activity.dp(1), colors.outlineVariant)
            }
            addView(TextView(activity).apply {
                text = "+"
                setTextColor(colors.primary)
                textSize = 48f
                typeface = Typeface.DEFAULT
                setTypeface(typeface, Typeface.NORMAL)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GoogleInteractionStyle.chipBackground(activity, selected = true)
            }, LinearLayout.LayoutParams(activity.dp(76), activity.dp(76)).apply {
                bottomMargin = activity.dp(18)
            })
            addView(label(title, 17, colors.onSurface, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            })
            addView(label(subtitle, 13, colors.onSurfaceVariant, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setPadding(0, activity.dp(6), 0, 0)
            })
            minimumWidth = cardWidth
            minimumHeight = cardHeight
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                action(this)
            }
        }
    }

    private fun pageOverviewNotificationBundleCard(
        entry: PageOverviewEntry.NotificationBundle,
        cardWidth: Int,
        cardHeight: Int,
        selected: Boolean,
    ): View {
        val colors = GoogleInteractionStyle.palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = activity.dp(26).toFloat()
                setColor(if (selected) withAlpha(colors.primaryContainer, 86) else Color.TRANSPARENT)
                setStroke(activity.dp(if (selected) 2 else 1), if (selected) colors.primary else colors.outlineVariant)
            }
            addView(
                FrameLayout(activity).apply {
                    addView(pageOverviewNotificationBundleSurface(entry, selected), matchParentParams())
                    addView(pageOverviewNotificationBundleBadge(entry, selected), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(label("Notifications", 15, if (selected) colors.onPrimaryContainer else colors.onSurface, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(activity.dp(4), activity.dp(12), activity.dp(4), 0)
            })
            minimumWidth = cardWidth
            minimumHeight = cardHeight
        }
    }

    private fun pageOverviewNotificationBundleSurface(entry: PageOverviewEntry.NotificationBundle, selected: Boolean): View {
        val colors = GoogleInteractionStyle.palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(54), activity.dp(18), activity.dp(18))
            addView(label("Live notifications", 20, if (selected) colors.primary else colors.onSurface, Typeface.BOLD).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, activity.dp(18))
            })
            entry.pages.take(5).forEach { pageEntry ->
                val chapter = pageEntry.page.chapter
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = activity.dp(16).toFloat()
                        setColor(colors.surfaceContainer)
                    }
                    addView(View(activity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(chapter?.hueColor ?: colors.primary)
                        }
                    }, LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)).apply {
                        rightMargin = activity.dp(10)
                    })
                    addView(label(pageEntry.page.title, 13, colors.onSurface, Typeface.BOLD).apply {
                        maxLines = 1
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(label("${chapter?.notifications?.size ?: 0}", 12, colors.onSurfaceVariant, Typeface.NORMAL))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = activity.dp(8)
                })
            }
            if (entry.pages.size > 5) {
                addView(label("+${entry.pages.size - 5} more", 13, colors.onSurfaceVariant, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, activity.dp(6), 0, 0)
                })
            }
        }
    }

    private fun pageOverviewNotificationBundleBadge(entry: PageOverviewEntry.NotificationBundle, selected: Boolean): View {
        val notificationCount = entry.pages.sumOf { pageEntry -> pageEntry.page.chapter?.notifications?.size ?: 0 }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(withAlpha(Color.BLACK, 114))
                cornerRadii = floatArrayOf(
                    activity.dp(18).toFloat(), activity.dp(18).toFloat(),
                    activity.dp(18).toFloat(), activity.dp(18).toFloat(),
                    0f, 0f,
                    0f, 0f,
                )
            }
            addView(label(if (selected) "Current" else "Live", 12, Color.WHITE, Typeface.BOLD), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = activity.dp(8)
            })
            addView(label("${entry.pages.size} pages, $notificationCount notifications", 12, Color.WHITE, Typeface.NORMAL).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun installPageOverviewNotificationBundleGestures(card: View, entry: PageOverviewEntry.NotificationBundle) {
        card.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showNotificationBundleMenu(it, entry)
        }
        card.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showNotificationBundleMenu(it, entry)
            true
        }
    }

    private fun showNotificationBundleMenu(source: View, entry: PageOverviewEntry.NotificationBundle) {
        val actions = entry.pages.map { pageEntry ->
            val count = pageEntry.page.chapter?.notifications?.size ?: 0
            ContextAction("${pageEntry.page.title} ($count)", Runnable {
                hidePageOverview()
                navigateToChapterPage(pageEntry.pageIndex)
            })
        }
        GoogleInteractionStyle.popupMenu(activity, source, source.screenCenter(), actions)
    }

    private fun showPageOverviewAddMenu(source: View, state: LauncherRenderModel, pages: List<ChapterPage>) {
        val actions = listOf(
            ContextAction("Classic page", Runnable { addClassicPageFromOverview() }),
            ContextAction("People page", Runnable { addPeoplePageFromOverview(pages) }),
            ContextAction("Agenda page", Runnable { addAgendaPageFromOverview(pages) }),
            ContextAction("Alarms page", Runnable { addAlarmsPageFromOverview(pages) }),
            ContextAction("RSS page", Runnable { addRssPageFromOverview(pages) }),
            ContextAction("Apps page", Runnable { addExistingOrLivePageFromOverview(PageSlot.APPS, pages, "Apps page") }),
            ContextAction("Pinned page", Runnable { addPinnedPageFromOverview(state, pages) }),
            ContextAction("Overview page", Runnable { addExistingOrLivePageFromOverview(PageSlot.OVERVIEW, pages, "Overview page") }),
            ContextAction("Work overview", Runnable { addWorkOverviewFromOverview(state, pages) }),
            ContextAction("Notification pages", Runnable { addNotificationPagesFromOverview(state, pages) }),
        )
        GoogleInteractionStyle.popupMenu(activity, source, source.screenCenter(), actions)
    }

    private fun addClassicPageFromOverview() {
        val page = settings.addClassicPage()
        editingClassicPageId = page.id
        selectPage(page.key)
        Toast.makeText(activity, "Added ${page.title}", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(page.key)
    }

    private fun addPeoplePageFromOverview(pages: List<ChapterPage>) {
        if (settings.contactsPageEnabled()) {
            addExistingOrLivePageFromOverview(PageSlot.CONTACTS, pages, "People page")
            return
        }
        settings.toggleContactsPage()
        Toast.makeText(activity, "Added People page", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview()
    }

    private fun addAgendaPageFromOverview(pages: List<ChapterPage>) {
        if (settings.agendaPageEnabled()) {
            addExistingOrLivePageFromOverview(PageSlot.AGENDA, pages, "Agenda page")
            return
        }
        settings.toggleAgendaPage()
        Toast.makeText(activity, "Added Agenda page", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(CalmTheme.AGENDA_KEY)
    }

    private fun addAlarmsPageFromOverview(pages: List<ChapterPage>) {
        if (settings.alarmsPageEnabled()) {
            addExistingOrLivePageFromOverview(PageSlot.ALARMS, pages, "Alarms page")
            return
        }
        settings.toggleAlarmsPage()
        Toast.makeText(activity, "Added Alarms page", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(CalmTheme.ALARMS_KEY)
    }

    private fun addRssPageFromOverview(pages: List<ChapterPage>) {
        if (settings.rssPageEnabled()) {
            addExistingOrLivePageFromOverview(PageSlot.RSS, pages, "RSS page")
            return
        }
        settings.setRssPageEnabled(true)
        Toast.makeText(activity, "Added RSS page", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(CalmTheme.RSS_KEY)
    }

    private fun addPinnedPageFromOverview(state: LauncherRenderModel, pages: List<ChapterPage>) {
        if (pages.any { page -> PageArranger.slotOf(page) == PageSlot.PINNED }) {
            addExistingOrLivePageFromOverview(PageSlot.PINNED, pages, "Pinned page")
            return
        }
        settings.setPinnedPageEnabled(true)
        Toast.makeText(activity, "Added Pinned page", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(CalmTheme.PINNED_KEY)
    }

    private fun addWorkOverviewFromOverview(state: LauncherRenderModel, pages: List<ChapterPage>) {
        if (state.notificationChapters.none { it.isWorkProfile }) {
            Toast.makeText(activity, "Work overview appears when work notifications arrive", Toast.LENGTH_SHORT).show()
            return
        }
        if (!settings.splitAppsByProfile()) {
            settings.toggleSplitAppsByProfile()
            Toast.makeText(activity, "Added Work overview", Toast.LENGTH_SHORT).show()
            renderAndReopenPageOverview()
            return
        }
        addExistingOrLivePageFromOverview(PageSlot.WORK_OVERVIEW, pages, "Work overview")
    }

    private fun addNotificationPagesFromOverview(state: LauncherRenderModel, pages: List<ChapterPage>) {
        if (state.notificationChapters.isEmpty()) {
            Toast.makeText(activity, "Notification pages appear when notifications arrive", Toast.LENGTH_SHORT).show()
            return
        }
        addExistingOrLivePageFromOverview(PageSlot.NOTIFICATIONS, pages, "Notification pages")
    }

    private fun addExistingOrLivePageFromOverview(slot: PageSlot, pages: List<ChapterPage>, label: String) {
        val index = pages.indexOfFirst { page -> PageArranger.slotOf(page) == slot }
        if (index == -1) {
            Toast.makeText(activity, "$label is not available yet", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, "$label is already added", Toast.LENGTH_SHORT).show()
        selectPage(pages[index].key)
        renderAndReopenPageOverview(pages[index].key)
    }

    private fun renderAndReopenPageOverview(focusPageKey: String? = null) {
        reopenPageOverviewAfterRender = true
        reopenPageOverviewFocusKey = focusPageKey
        render()
    }

    private fun installPageOverviewCardGestures(
        card: View,
        page: ChapterPage,
        index: Int,
        entryIndex: Int,
        entries: List<PageOverviewEntry>,
        pages: List<ChapterPage>,
        state: LauncherRenderModel,
        cardWidth: Int,
    ) {
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var downScrollX = 0
        var dragArmed = false
        var dragging = false
        var previewTargetEntryIndex = entryIndex
        var dropPlaceholder: GradientDrawable? = null
        val longPress = Runnable {
            dragArmed = true
            previewTargetEntryIndex = entryIndex
            dropPlaceholder = pageOverviewDropPlaceholder().also { placeholder ->
                pageOverviewRowFor(card)?.overlay?.add(placeholder)
                updatePageOverviewDropPreview(card, entryIndex, previewTargetEntryIndex, cardWidth, placeholder)
            }
            card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            card.parent?.requestDisallowInterceptTouchEvent(true)
            card.elevation = activity.dp(14).toFloat()
            card.animate().alpha(0.92f).scaleX(1.03f).scaleY(1.03f).setDuration(110L).start()
        }
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downScrollX = pageOverviewScrollerFor(view)?.scrollX ?: 0
                    dragArmed = false
                    dragging = false
                    view.animate().cancel()
                    view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragArmed) {
                        if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            view.removeCallbacks(longPress)
                        }
                        return@setOnTouchListener true
                    }
                    if (!dragging && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragArmed) {
                        autoScrollPageOverviewWhileDragging(view, event.rawX)
                        val dragX = pageOverviewDragDistance(view, downScrollX, dx)
                        view.translationX = dragX
                        view.translationY = dy.coerceIn(-activity.dp(18).toFloat(), activity.dp(18).toFloat())
                        view.scaleX = 1.03f
                        view.scaleY = 1.03f
                        val targetEntryIndex = pageOverviewTargetEntryIndex(entryIndex, entries, cardWidth, dragX)
                        if (targetEntryIndex != previewTargetEntryIndex) {
                            previewTargetEntryIndex = targetEntryIndex
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            dropPlaceholder?.let { placeholder ->
                                updatePageOverviewDropPreview(view, entryIndex, previewTargetEntryIndex, cardWidth, placeholder)
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPress)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val dragX = pageOverviewDragDistance(view, downScrollX, dx)
                    var handledByReorder = false
                    if (dragArmed && dragging) {
                        val targetEntryIndex = previewTargetEntryIndex
                        val targetIndex = entries[targetEntryIndex].firstPageIndex.coerceIn(0, pages.lastIndex)
                        if (targetEntryIndex != entryIndex && targetIndex != index) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            handledByReorder = reorderPageFromOverview(page, pages, targetIndex, state)
                        } else {
                            magnetizePageOverviewToCard(view, index, cardWidth)
                        }
                    } else if (dragArmed) {
                        showPageOverviewActions(view, page, index, entryIndex, pages, state)
                    } else if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        hidePageOverview()
                        navigateToChapterPage(index)
                    }
                    if (!handledByReorder) {
                        clearPageOverviewDropPreview(view, dropPlaceholder)
                        view.elevation = 0f
                        view.animate().translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(90L).start()
                    }
                    dropPlaceholder = null
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPress)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    clearPageOverviewDropPreview(view, dropPlaceholder)
                    view.elevation = 0f
                    view.animate().translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(90L).start()
                    dropPlaceholder = null
                    true
                }
                else -> true
            }
        }
    }

    private fun pageOverviewDragDistance(card: View, downScrollX: Int, fingerDeltaX: Float): Float {
        val scroller = pageOverviewScrollerFor(card) ?: return fingerDeltaX
        return fingerDeltaX + (scroller.scrollX - downScrollX)
    }

    private fun pageOverviewTargetEntryIndex(
        entryIndex: Int,
        entries: List<PageOverviewEntry>,
        cardWidth: Int,
        dragX: Float,
    ): Int {
        return (entryIndex + (dragX / (cardWidth * 0.72f)).roundToInt()).coerceIn(0, entries.lastIndex)
    }

    private fun pageOverviewScrollerFor(card: View): HorizontalScrollView? {
        return card.parent?.parent as? HorizontalScrollView
    }

    private fun pageOverviewRowFor(card: View): ViewGroup? {
        return card.parent as? ViewGroup
    }

    private fun pageOverviewDropPlaceholder(): GradientDrawable {
        val colors = GoogleInteractionStyle.palette(activity)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = activity.dp(26).toFloat()
            setColor(withAlpha(colors.primaryContainer, 74))
            setStroke(activity.dp(2), colors.primary)
        }
    }

    private fun updatePageOverviewDropPreview(
        card: View,
        sourceEntryIndex: Int,
        targetEntryIndex: Int,
        cardWidth: Int,
        placeholder: GradientDrawable,
    ) {
        val row = pageOverviewRowFor(card) ?: return
        val stride = pageOverviewCardStride(cardWidth)
        val targetChild = row.getChildAt(targetEntryIndex) ?: card
        placeholder.setBounds(
            targetChild.left,
            card.top,
            targetChild.left + cardWidth,
            card.bottom,
        )
        for (childIndex in 0 until row.childCount) {
            val child = row.getChildAt(childIndex)
            if (child === card) continue
            val translation = when {
                sourceEntryIndex < targetEntryIndex && childIndex in (sourceEntryIndex + 1)..targetEntryIndex -> -stride.toFloat()
                sourceEntryIndex > targetEntryIndex && childIndex in targetEntryIndex until sourceEntryIndex -> stride.toFloat()
                else -> 0f
            }
            if (child.translationX != translation) {
                child.animate().cancel()
                child.animate().translationX(translation).setDuration(PAGE_OVERVIEW_REORDER_PREVIEW_MS).start()
            }
        }
        row.invalidate()
    }

    private fun clearPageOverviewDropPreview(card: View, placeholder: GradientDrawable?) {
        val row = pageOverviewRowFor(card) ?: return
        placeholder?.let { row.overlay.remove(it) }
        for (childIndex in 0 until row.childCount) {
            val child = row.getChildAt(childIndex)
            if (child !== card && child.translationX != 0f) {
                child.animate().cancel()
                child.animate().translationX(0f).setDuration(PAGE_OVERVIEW_REORDER_PREVIEW_MS).start()
            }
        }
        row.invalidate()
    }

    private fun installPageOverviewScrollMagnet(scroller: HorizontalScrollView, cardWidth: Int) {
        var snap: Runnable? = null
        var touching = false
        var snapAfterSettle = false
        fun cancelSnap() {
            snap?.let(scroller::removeCallbacks)
            snap = null
        }
        fun scheduleSnap() {
            cancelSnap()
            snap = Runnable {
                snap = null
                if (touching) return@Runnable
                snapAfterSettle = false
                snapPageOverviewScroll(scroller, cardWidth)
            }.also {
                scroller.postDelayed(it, PAGE_OVERVIEW_MAGNET_DELAY_MS)
            }
        }
        scroller.setOnScrollChangeListener { _, _, _, _, _ ->
            if (snapAfterSettle && !touching) scheduleSnap()
        }
        scroller.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    snapAfterSettle = false
                    cancelSnap()
                }
                MotionEvent.ACTION_MOVE -> cancelSnap()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    snapAfterSettle = true
                    scheduleSnap()
                }
            }
            false
        }
    }

    private fun autoScrollPageOverviewWhileDragging(card: View, rawX: Float) {
        val scroller = pageOverviewScrollerFor(card) ?: return
        val location = IntArray(2)
        scroller.getLocationOnScreen(location)
        val leftEdge = location[0] + activity.dp(44)
        val rightEdge = location[0] + scroller.width - activity.dp(44)
        val delta = when {
            rawX < leftEdge -> -activity.dp(10)
            rawX > rightEdge -> activity.dp(10)
            else -> 0
        }
        if (delta != 0) scroller.scrollBy(delta, 0)
    }

    private fun magnetizePageOverviewToCard(card: View, index: Int, cardWidth: Int) {
        val scroller = pageOverviewScrollerFor(card) ?: return
        scroller.post { scrollPageOverviewToCard(scroller, index, cardWidth, smooth = true) }
    }

    private fun snapPageOverviewScroll(scroller: HorizontalScrollView, cardWidth: Int) {
        val stride = pageOverviewCardStride(cardWidth)
        val index = ((scroller.scrollX + activity.dp(24)) / stride.toFloat()).roundToInt().coerceAtLeast(0)
        val target = (index * stride - activity.dp(24)).coerceAtLeast(0)
        if (abs(scroller.scrollX - target) > (cardWidth * PAGE_OVERVIEW_MAGNET_THRESHOLD).roundToInt()) return
        scrollPageOverviewToCard(scroller, index, cardWidth, smooth = true)
    }

    private fun scrollPageOverviewToCard(
        scroller: HorizontalScrollView,
        index: Int,
        cardWidth: Int,
        smooth: Boolean,
    ) {
        val target = (index * pageOverviewCardStride(cardWidth) - activity.dp(24)).coerceAtLeast(0)
        if (smooth) scroller.smoothScrollTo(target, 0) else scroller.scrollTo(target, 0)
    }

    private fun pageOverviewCardStride(cardWidth: Int): Int = cardWidth + activity.dp(14)

    private fun showPageOverviewActions(
        source: View,
        page: ChapterPage,
        index: Int,
        entryIndex: Int,
        pages: List<ChapterPage>,
        state: LauncherRenderModel,
    ) {
        val actions = mutableListOf<ContextAction>()
        if (canUseAsDefaultHome(page)) {
            actions += ContextAction("Set as home", Runnable {
                setPageAsDefaultHome(page)
            })
        }
        actions += ContextAction("Customise", Runnable {
            customisePageFromOverview(page)
        })
        deletePageAction(source, page, index, entryIndex, pages)?.let { actions += it }
        GoogleInteractionStyle.popupMenu(activity, source, source.screenCenter(), actions, destructiveLabels = setOf("Delete", "Remove"))
    }

    private fun canUseAsDefaultHome(page: ChapterPage): Boolean {
        return PageArranger.slotOf(page) != PageSlot.NOTIFICATIONS
    }

    private fun setPageAsDefaultHome(page: ChapterPage) {
        page.classicPage?.let {
            if (!settings.setDefaultClassicPage(it.id)) return
            settings.setDefaultHomeSlot(PageSlot.CLASSIC_PAGES)
            Toast.makeText(activity, "${it.title} is now home", Toast.LENGTH_SHORT).show()
            renderAndReopenPageOverview(page.key)
            return
        }
        val slot = PageArranger.slotOf(page)
        if (slot == PageSlot.NOTIFICATIONS) return
        settings.setDefaultHomeSlot(slot)
        Toast.makeText(activity, "${page.title} is now home", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(page.key)
    }

    private fun customisePageFromOverview(page: ChapterPage) {
        hidePageOverview()
        page.classicPage?.let {
            editingClassicPageId = it.id
            selectPage(it.key)
            render()
            return
        }
        selectPage(page.key)
        openSettingsActivity()
    }

    private fun deletePageAction(source: View, page: ChapterPage, index: Int, entryIndex: Int, pages: List<ChapterPage>): ContextAction? {
        val nextFocusKey = pages.getOrNull(index + 1)?.key ?: pages.getOrNull(index - 1)?.key
        page.classicPage?.let { classicPage ->
            return ContextAction("Delete", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    removeClassicPageFromOverview(classicPage, nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        page.chapter?.let { chapter ->
            return ContextAction("Remove", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    hideNotificationPageFromOverview(chapter, nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        val slot = PageArranger.slotOf(page)
        if (slot == PageSlot.AGENDA) {
            return ContextAction("Remove", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    if (settings.agendaPageEnabled()) settings.toggleAgendaPage()
                    Toast.makeText(activity, "Removed Agenda page", Toast.LENGTH_SHORT).show()
                    renderAndReopenPageOverview(nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        if (slot == PageSlot.ALARMS) {
            return ContextAction("Remove", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    if (settings.alarmsPageEnabled()) settings.toggleAlarmsPage()
                    Toast.makeText(activity, "Removed Alarms page", Toast.LENGTH_SHORT).show()
                    renderAndReopenPageOverview(nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        if (slot == PageSlot.RSS) {
            return ContextAction("Remove", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    settings.setRssPageEnabled(false)
                    Toast.makeText(activity, "Removed RSS page", Toast.LENGTH_SHORT).show()
                    renderAndReopenPageOverview(nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        if (slot == PageSlot.PINNED && settings.pinnedPageEnabled()) {
            return ContextAction("Remove", Runnable {
                animatePageOverviewRemoval(source, entryIndex, index, pages) {
                    settings.setPinnedPageEnabled(false)
                    Toast.makeText(activity, "Removed Pinned page", Toast.LENGTH_SHORT).show()
                    renderAndReopenPageOverview(nextFocusKey)
                }
            }, ContextActionCloseBehavior.REMOVE_CARD)
        }
        if (slot != PageSlot.CONTACTS) return null
        return ContextAction("Remove", Runnable {
            animatePageOverviewRemoval(source, entryIndex, index, pages) {
                if (settings.contactsPageEnabled()) settings.toggleContactsPage()
                Toast.makeText(activity, "Removed People page", Toast.LENGTH_SHORT).show()
                renderAndReopenPageOverview(nextFocusKey)
            }
        }, ContextActionCloseBehavior.REMOVE_CARD)
    }

    private fun animatePageOverviewRemoval(card: View, cardIndex: Int, pageIndex: Int, pages: List<ChapterPage>, removeAction: () -> Unit) {
        val row = card.parent as? ViewGroup
        val stride = card.width.takeIf { it > 0 }?.let(::pageOverviewCardStride) ?: pageOverviewCardStride(card.measuredWidth)
        val nextFocus = pages.getOrNull(pageIndex + 1) ?: pages.getOrNull(pageIndex - 1)
        if (nextFocus != null) {
            selectPage(nextFocus.key)
        }
        card.isEnabled = false
        card.parent?.requestDisallowInterceptTouchEvent(true)
        card.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(activity.dp(18).toFloat())
            .setDuration(PAGE_OVERVIEW_REMOVE_ANIMATION_MS)
            .start()
        if (row != null) {
            for (childIndex in cardIndex + 1 until row.childCount) {
                row.getChildAt(childIndex)
                    .animate()
                    .translationX(-stride.toFloat())
                    .setDuration(PAGE_OVERVIEW_REMOVE_ANIMATION_MS)
                    .start()
            }
            if (cardIndex > 0) {
                row.getChildAt(cardIndex - 1)
                    .animate()
                    .scaleX(1.015f)
                    .scaleY(1.015f)
                    .setDuration(PAGE_OVERVIEW_REMOVE_ANIMATION_MS / 2)
                    .withEndAction {
                        row.getChildAt(cardIndex - 1)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(90L)?.start()
                    }
                    .start()
            }
        }
        mainHandler.postDelayed({
            card.parent?.requestDisallowInterceptTouchEvent(false)
            removeAction()
        }, PAGE_OVERVIEW_REMOVE_ANIMATION_MS)
    }

    private fun removeClassicPageFromOverview(page: ClassicLauncherPageDefinition, focusPageKey: String?) {
        val removed = settings.removeClassicPage(page.id) ?: return
        if (editingClassicPageId == removed.id) {
            editingClassicPageId = null
        }
        pendingClassicPlacementItemId = null
        removed.items
            .filter { item -> item.type == ClassicGridItemType.WIDGET }
            .forEach(classicWidgetHostController::deleteWidget)
        Toast.makeText(activity, "Removed ${removed.title}", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(focusPageKey)
    }

    private fun hideNotificationPageFromOverview(chapter: AppChapter, focusPageKey: String?) {
        settings.exclude(chapter)
        Toast.makeText(activity, "Removed ${chapter.label}", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(focusPageKey)
    }

    private fun reorderPageFromOverview(
        page: ChapterPage,
        pages: List<ChapterPage>,
        targetPageIndex: Int,
        state: LauncherRenderModel,
    ): Boolean {
        val targetPage = pages.getOrNull(targetPageIndex) ?: return false
        val sourceSlot = PageArranger.slotOf(page)
        val targetSlot = PageArranger.slotOf(targetPage)
        if (sourceSlot == PageSlot.CLASSIC_PAGES && targetSlot == PageSlot.CLASSIC_PAGES) {
            val classic = page.classicPage ?: return false
            val targetClassic = targetPage.classicPage ?: return false
            val targetIndex = state.classicPages.indexOfFirst { it.id == targetClassic.id }
            if (targetIndex != -1) {
                if (settings.moveClassicPage(classic.id, targetIndex)) {
                    Toast.makeText(activity, "Moved ${classic.title}", Toast.LENGTH_SHORT).show()
                    renderAndReopenPageOverview(classic.key)
                    return true
                }
            }
            return false
        }
        if (sourceSlot == targetSlot) return false
        val layout = settings.pageLayout()
        val from = layout.order.indexOf(sourceSlot)
        val to = layout.order.indexOf(targetSlot)
        if (from == -1 || to == -1 || from == to) return false
        val next = layout.order.toMutableList().apply { add(to, removeAt(from)) }
        settings.setPageLayoutOrder(next)
        Toast.makeText(activity, "Moved ${page.title}", Toast.LENGTH_SHORT).show()
        renderAndReopenPageOverview(page.key)
        return true
    }

    private fun pageOverviewCard(
        page: ChapterPage,
        state: LauncherRenderModel,
        index: Int,
        pages: List<ChapterPage>,
        adapter: ChapterPagerAdapter?,
        cardWidth: Int,
        cardHeight: Int,
        selected: Boolean,
    ): View {
        val colors = GoogleInteractionStyle.palette(activity)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = activity.dp(26).toFloat()
                setColor(if (selected) withAlpha(colors.primaryContainer, 86) else Color.TRANSPARENT)
                setStroke(activity.dp(if (selected) 2 else 1), if (selected) colors.primary else colors.outlineVariant)
            }
            addView(
                FrameLayout(activity).apply {
                    clipChildren = false
                    clipToPadding = false
                    val snapshot = pageOverviewSnapshot(adapter, index, cardWidth - activity.dp(24), cardHeight - activity.dp(74))
                    if (snapshot != null) {
                        addView(
                            ImageView(activity).apply {
                                setImageBitmap(snapshot)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                alpha = 0.96f
                            },
                            matchParentParams(),
                        )
                    } else {
                        addView(pageOverviewSurface(page, state, selected), matchParentParams())
                    }
                    addView(pageOverviewBadge(page, pages, selected), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            addView(label(page.title, 15, if (selected) colors.onPrimaryContainer else colors.onSurface, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(activity.dp(4), activity.dp(12), activity.dp(4), 0)
            })
        }
    }

    private fun pageOverviewSnapshot(
        adapter: ChapterPagerAdapter?,
        index: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val view = adapter?.pageView(index) ?: return null
        val sourceWidth = currentPager?.width?.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val sourceHeight = currentPager?.height?.takeIf { it > 0 }
            ?: (activity.resources.displayMetrics.heightPixels - activity.statusBarHeightFallback()).coerceAtLeast(activity.dp(320))
        return runCatching {
            if (view.width <= 0 || view.height <= 0 || view.parent == null) {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(sourceWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(sourceHeight, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, sourceWidth, sourceHeight)
            }
            val bitmap = Bitmap.createBitmap(targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = maxOf(
                targetWidth / sourceWidth.toFloat(),
                targetHeight / sourceHeight.toFloat(),
            )
            val dx = (targetWidth - sourceWidth * scale) / 2f
            val dy = (targetHeight - sourceHeight * scale) / 2f
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            val suppressedBackgrounds = suppressPageBackgrounds(view)
            try {
                view.draw(canvas)
            } finally {
                restoreSuppressedBackgrounds(suppressedBackgrounds)
            }
            bitmap
        }.getOrNull()
    }

    private fun suppressPageBackgrounds(view: View): List<SuppressedBackground> {
        val suppressed = ArrayList<SuppressedBackground>()
        suppressPageBackgrounds(view, suppressed)
        return suppressed
    }

    private fun suppressPageBackgrounds(view: View, suppressed: MutableList<SuppressedBackground>) {
        if (view.tag == CalmAnimationTags.PAGE_PANEL && view.background != null) {
            suppressed += SuppressedBackground(view, view.background)
            view.background = null
        }
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            suppressPageBackgrounds(view.getChildAt(index), suppressed)
        }
    }

    private fun restoreSuppressedBackgrounds(suppressed: List<SuppressedBackground>) {
        suppressed.forEach { it.view.background = it.background }
    }

    private fun pageOverviewBadge(page: ChapterPage, pages: List<ChapterPage>, selected: Boolean): View {
        val slot = PageArranger.slotOf(page)
        val home = isDefaultHomePage(page)
        val detail = when {
            page.classicPage != null -> "${page.classicPage.items.size} items"
            page.chapter != null -> "${page.chapter.notifications.size} notifications"
            page.appScope != null -> "App page"
            slot == PageSlot.PINNED -> "Pinned"
            slot == PageSlot.CONTACTS -> "People"
            slot == PageSlot.AGENDA -> "Agenda"
            slot == PageSlot.ALARMS -> "Alarms"
            slot == PageSlot.RSS -> "RSS"
            else -> if (selected) "Current" else "${pages.indexOf(page) + 1} of ${pages.size}"
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(withAlpha(Color.BLACK, 114))
                cornerRadii = floatArrayOf(
                    activity.dp(18).toFloat(), activity.dp(18).toFloat(),
                    activity.dp(18).toFloat(), activity.dp(18).toFloat(),
                    0f, 0f,
                    0f, 0f,
                )
            }
            addView(label(if (home) "Home" else page.marker, 12, Color.WHITE, Typeface.BOLD), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = activity.dp(8)
            })
            addView(label(detail, 12, Color.WHITE, Typeface.NORMAL).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun isDefaultHomePage(page: ChapterPage): Boolean {
        val layout = settings.pageLayout()
        page.classicPage?.let { classicPage ->
            return layout.defaultHome == PageSlot.CLASSIC_PAGES && settings.homeClassicPage()?.id == classicPage.id
        }
        val slot = PageArranger.slotOf(page)
        return slot != PageSlot.NOTIFICATIONS && slot == layout.defaultHome
    }

    private fun pageOverviewSurface(page: ChapterPage, state: LauncherRenderModel, selected: Boolean): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(18), activity.dp(22), activity.dp(18), activity.dp(18))
            addView(label(page.marker, 26, CalmTheme.INK, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, activity.dp(16))
            })
            when {
                page.classicPage != null -> addClassicOverviewPreview(this, page.classicPage, state.classicGridConfig)
                page.appScope != null -> addAppsOverviewPreview(this, page, state)
                page.key == CalmTheme.PINNED_KEY -> addAppRows(this, state.pinnedApps.take(5).map { it.label }.ifEmpty { listOf("No pinned apps", "Long-press apps", "Choose Pin") }, CalmTheme.ACCENT)
                page.key == CalmTheme.CONTACTS_KEY -> addGenericRows(this, listOf("Favourite people", "Recent contact", "Quick action"), CalmTheme.ACCENT)
                page.key == CalmTheme.AGENDA_KEY -> addAgendaOverviewPreview(this, state)
                page.key == CalmTheme.ALARMS_KEY -> addGenericRows(this, listOf("Next alarm", "Clock app", "Wake up"), CalmTheme.ACCENT)
                page.key == CalmTheme.RSS_KEY -> addRssOverviewPreview(this, state)
                page.key == CalmTheme.WORK_OVERVIEW_KEY -> addOverviewRows(this, state, work = true)
                page.chapter != null -> addNotificationOverviewPreview(this, page.chapter)
                else -> addOverviewRows(this, state, work = false)
            }
            if (selected) {
                addView(label("Current", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, activity.dp(12), 0, 0)
                })
            }
        }
    }

    private fun addOverviewRows(parent: LinearLayout, state: LauncherRenderModel, work: Boolean) {
        val notifications = state.notificationChapters
            .filter { chapter -> chapter.isWorkProfile == work || !work }
            .take(3)
            .map { chapter -> "${chapter.label}  ${chapter.notifications.size}" }
        val rows = buildList {
            addAll(notifications)
            if (state.calendarEvents.isNotEmpty()) add(state.calendarEvents.first().title.ifBlank { "Calendar" })
            if (state.pinnedApps.isNotEmpty()) add("Pinned apps  ${state.pinnedApps.size}")
        }.ifEmpty { listOf(if (work) "Work overview" else "Overview") }
        addGenericRows(parent, rows.take(5), CalmTheme.ACCENT)
    }

    private fun addNotificationOverviewPreview(parent: LinearLayout, chapter: AppChapter) {
        val rows = chapter.notifications.take(5).mapIndexed { index, _ -> "Notification ${index + 1}" }
            .ifEmpty { listOf("No notifications") }
        addGenericRows(parent, rows, chapter.hueColor)
    }

    private fun addAgendaOverviewPreview(parent: LinearLayout, state: LauncherRenderModel) {
        val rows = when {
            !state.hasCalendarPermission -> listOf("Calendar access", "Tap to allow")
            state.calendarEvents.isEmpty() -> listOf("No upcoming events")
            else -> state.calendarEvents.take(5).map { event -> event.title.ifBlank { "Untitled event" } }
        }
        addGenericRows(parent, rows, CalmTheme.ACCENT)
    }

    private fun addRssOverviewPreview(parent: LinearLayout, state: LauncherRenderModel) {
        val rows = when {
            state.rssFeedUrls.isEmpty() -> listOf("No feeds", "Add URLs in Settings")
            state.rssItems.isEmpty() -> listOf("No recent items", "${state.rssFeedUrls.size} feeds")
            else -> state.rssItems.take(5).map { item -> item.title.ifBlank { item.feedTitle } }
        }
        addGenericRows(parent, rows, CalmTheme.ACCENT)
    }

    private fun addAppsOverviewPreview(parent: LinearLayout, page: ChapterPage, state: LauncherRenderModel) {
        val apps = state.appEntries
            .filter { app ->
                when (page.appScope) {
                    AppLibraryScope.PERSONAL -> !app.isWorkProfile
                    AppLibraryScope.WORK -> app.isWorkProfile
                    else -> true
                }
            }
            .take(5)
            .map { it.label }
        addAppRows(parent, apps.ifEmpty { listOf("Apps") }, CalmTheme.ACCENT)
    }

    private fun addClassicOverviewPreview(
        parent: LinearLayout,
        classicPage: ClassicLauncherPageDefinition,
        gridConfig: ClassicGridConfig,
    ) {
        val preview = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            background = overviewRounded(withAlpha(CalmTheme.GLASS, 116), activity.dp(18))
        }
        classicPage.items.take(24).forEach { item ->
            val xFraction = item.x / gridConfig.columns.toFloat()
            val yFraction = item.y / gridConfig.rows.toFloat()
            val widthFraction = item.width / gridConfig.columns.toFloat()
            val heightFraction = item.height / gridConfig.rows.toFloat()
            preview.addView(
                View(activity).apply {
                    background = overviewRounded(
                        when (item.type) {
                            ClassicGridItemType.APP -> GoogleInteractionStyle.primary(activity)
                            ClassicGridItemType.WIDGET -> CalmTheme.INK
                            ClassicGridItemType.STATIC -> CalmTheme.MUTED_INK
                        },
                        activity.dp(10),
                    )
                },
                FrameLayout.LayoutParams(
                    maxOf(activity.dp(12), (activity.dp(190) * widthFraction).toInt()),
                    maxOf(activity.dp(12), (activity.dp(300) * heightFraction).toInt()),
                ).apply {
                    leftMargin = (activity.dp(190) * xFraction).toInt()
                    topMargin = (activity.dp(300) * yFraction).toInt()
                },
            )
        }
        parent.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun addAppRows(parent: LinearLayout, labels: List<String>, color: Int) {
        labels.take(5).forEach { text ->
            parent.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(activity).apply {
                    background = overviewRounded(color, activity.dp(12))
                }, LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)).apply {
                    rightMargin = activity.dp(10)
                })
                addView(label(text, 12, CalmTheme.INK, Typeface.BOLD).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            })
        }
    }

    private fun addGenericRows(parent: LinearLayout, labels: List<String>, color: Int) {
        labels.take(5).forEachIndexed { index, text ->
            parent.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = overviewRounded(withAlpha(color, if (index == 0) 180 else 124), activity.dp(16))
                setPadding(activity.dp(12), activity.dp(9), activity.dp(12), activity.dp(9))
                addView(label(text, 12, Color.WHITE, Typeface.BOLD).apply { maxLines = 1 })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(10)
            })
        }
    }

    private fun overviewRounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun View.screenCenter(): Pair<Int, Int> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0] + width / 2 to location[1] + height / 2
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun emptyNote(text: String): TextView {
        return label(text, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        }
    }

    private fun label(text: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(color)
            textSize = sp.toFloat()
            typeface = Typeface.DEFAULT
            setTypeface(typeface, style)
            includeFontPadding = true
        }
    }

    private fun formatNotificationTime(postTime: Long): String {
        return DateFormat.getTimeFormat(activity).format(Date(postTime))
    }

    private fun loadPinnedApps(): List<AppEntry> = appLibraryDataManager.loadPinnedAppEntries()

    private fun openSettingsActivity() {
        activity.startActivity(Intent(activity, CalmSettingsActivity::class.java))
    }

    private fun openSectionCardSettingsActivity() {
        activity.startActivity(
            Intent(activity, CalmSettingsActivity::class.java).apply {
                putExtra(CalmSettingsActivity.EXTRA_PAGE, CalmSettingsActivity.PAGE_SECTION_CARDS)
            },
        )
    }

    private fun openAppEntry(app: AppEntry) {
        if (notificationRepository.openApp(app)) {
            appSearchController.resetAll()
        } else {
            Toast.makeText(activity, "This app cannot be opened directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPackage(chapter: AppChapter) {
        if (!chapter.launchable) {
            Toast.makeText(activity, "This notification source has no launcher entry", Toast.LENGTH_SHORT).show()
            return
        }
        if (!notificationRepository.openChapter(chapter)) {
            Toast.makeText(activity, "This app cannot be opened directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCalendarEvent(event: CalendarEvent) {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(event.begin.toString())
            .build()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, "Calendar cannot be opened", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(
        packageName: String,
        userHandle: android.os.UserHandle? = null,
        componentName: ComponentName? = null,
    ) {
        val launcherApps = activity.getSystemService(android.content.pm.LauncherApps::class.java)
        if (userHandle != null && componentName != null && launcherApps != null) {
            val opened = runCatching {
                launcherApps.startAppDetailsActivity(componentName, userHandle, null, null)
            }.isSuccess
            if (opened) return
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, "App info screen unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performCardScrollHaptic(source: View) {
        if (!activePreferences.cardHapticsEnabled) return
        val strength = activePreferences.cardHapticStrength
        val amplitude = 12 + strength * 8
        val durationMs = 6L + strength
        val vibrator = vibrator()
        if (vibrator == null || !vibrator.hasVibrator()) {
            source.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            return
        }
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
    }

    private fun vibrator(): Vibrator? {
        return CalmVibrator.defaultVibrator(activity)
    }

    private data class SuppressedBackground(
        val view: View,
        val background: Drawable,
    )

    private sealed class PageOverviewEntry {
        abstract val firstPageIndex: Int
        abstract fun containsPageIndex(index: Int): Boolean

        data class Page(
            val pageIndex: Int,
            val page: ChapterPage,
        ) : PageOverviewEntry() {
            override val firstPageIndex: Int = pageIndex
            override fun containsPageIndex(index: Int): Boolean = index == pageIndex
        }

        data class NotificationBundle(
            val pages: List<Page>,
        ) : PageOverviewEntry() {
            override val firstPageIndex: Int = pages.firstOrNull()?.pageIndex ?: 0
            override fun containsPageIndex(index: Int): Boolean = pages.any { page -> page.pageIndex == index }
        }
    }

    private fun registerPackageChangeReceiver() {
        if (packageChangeReceiverRegistered) return
        activity.registerReceiver(packageChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        packageChangeReceiverRegistered = true
    }

    private fun unregisterPackageChangeReceiver() {
        if (!packageChangeReceiverRegistered) return
        runCatching { activity.unregisterReceiver(packageChangeReceiver) }
        packageChangeReceiverRegistered = false
    }

    private fun invalidateAppDataAndRefresh() {
        notificationRepository.invalidateAppCaches()
        appCardDisplayCache.clear()
        notificationCardDisplayCache.clear()
        cardRenderAssetCache.clear()
        cardRenderer.clearIconCache()
        appSearchController.clear()
        stateManager.refreshAsync()
    }

    private companion object {
        const val PAGE_OVERVIEW_PINCH_THRESHOLD = 0.82f
        const val PAGE_OVERVIEW_MAGNET_DELAY_MS = 120L
        const val PAGE_OVERVIEW_MAGNET_THRESHOLD = 0.16f
        const val PAGE_OVERVIEW_REMOVE_ANIMATION_MS = 190L
        const val PAGE_OVERVIEW_REORDER_PREVIEW_MS = 70L
        const val PAGE_PREWARM_INITIAL_DELAY_MS = 420L
        const val PAGE_PREWARM_AFTER_NAVIGATION_DELAY_MS = 360L
        const val PAGE_PREWARM_STEP_DELAY_MS = 180L
        const val PAGE_PREWARM_MAX_PAGES = 2
    }
}
