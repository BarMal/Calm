package dev.barna.calm

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.concurrent.Executors

class CalmLauncherRunner(
    private val activity: MainActivity,
    private val launcherStateViewModel: LauncherStateViewModel,
    requestCalendarPermission: () -> Unit,
    requestContactsPermission: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = LauncherSettings(activity)
    private val notificationRepository = NotificationChapterRepository(activity, settings)
    private val calendarRepository = CalendarRepository(activity, requestCalendarPermission)
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
    )
    private val contextActionFactory = LauncherContextActionFactory(
        LauncherContextActionCallbacks(
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
        ),
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
    )
    private val pageFactory = LauncherPageFactory(
        activity = activity,
        overviewPageBuilder = overviewPageBuilder,
        chapterPageBuilder = chapterPageBuilder,
        appLibraryController = appLibraryController,
        appSearchController = appSearchController,
        appLibraryPageModelFactory = appLibraryPageModelFactory,
        appLibraryStore = appLibraryStore,
        contactsPageController = contactsPageController,
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
    private var currentScreen: View? = null
    private val currentUiState: LauncherRenderModel?
        get() = launcherStateViewModel.uiState.value.renderModel
    private var activePreferences: LauncherUiPreferences = settings.uiPreferences()
    private var appCardSettingsSnapshot: LauncherUiPreferences = activePreferences
    private var settingsChangeToken = settings.launcherChangeToken()
    private var renderedNotificationRevision = CalmNotificationListenerService.revision()
    private var packageChangeReceiverRegistered = false
    private var pagePrewarmGeneration = 0
    private var suppressedPageEntryKey: String? = null

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
            render(stateManager.buildSync(), animate = true)
        }
        appLibraryDataManager.refreshInBackground()
    }

    fun onResume() {
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
            render(stateManager.buildSync(), animate = true)
        }
    }

    fun onPause() {
        CalmNotificationListenerService.removeListener(notificationRefresh)
    }

    fun onDestroy() {
        try {
            mainHandler.removeCallbacksAndMessages(null)
            stateExecutor.shutdownNow()
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

    /** Dismisses the expanded/focus card on back so it returns to the current page, not overview. */
    fun onBackPressed() {
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

        val screen = FrameLayout(activity).apply {
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
        val pager = ViewPager2(activity).apply {
            currentPager = this
            adapter = pagerAdapter
            clipToPadding = false
            clipChildren = false
            offscreenPageLimit = 1
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
                dockController.buildDock(state.dockApps, state.dockConfig),
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
        schedulePagePrewarm(pager, pagerAdapter, pages.size, initialPage)
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
        PageSlot.APPS -> CalmTheme.APP_LIBRARY_KEY
        PageSlot.NOTIFICATIONS -> CalmTheme.OVERVIEW_KEY
    }

    private fun schedulePagePrewarm(
        pager: ViewPager2,
        adapter: ChapterPagerAdapter,
        pageCount: Int,
        initialPage: Int,
    ) {
        val generation = ++pagePrewarmGeneration
        if (pageCount <= 1) return
        val positions = pagePrewarmPlanner.positions(pageCount, initialPage, PAGE_PREWARM_MAX_PAGES)
        scheduleNextPagePrewarm(pager, adapter, positions, generation, 0, PAGE_PREWARM_INITIAL_DELAY_MS)
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
            if (pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
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
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
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
        const val PAGE_PREWARM_INITIAL_DELAY_MS = 260L
        const val PAGE_PREWARM_STEP_DELAY_MS = 120L
        const val PAGE_PREWARM_MAX_PAGES = 3
    }
}
