package dev.barna.calm

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CalmLauncherRunner(
    private val activity: MainActivity,
    private val launcherStateViewModel: LauncherStateViewModel,
    requestCalendarPermission: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = LauncherSettings(activity)
    private val notificationRepository = NotificationChapterRepository(activity, settings)
    private val calendarRepository = CalendarRepository(activity, requestCalendarPermission)
    private val drawables = CalmDrawables(activity)
    private val cardSpec = CalmCardSpec()
    private val expandedOverviewGroups = mutableSetOf<String>()
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
    private val appStackRenderPlanner = AppStackRenderPlanner()
    private val appLibraryStore = AppLibraryRenderStore()
    private val appSearchState = AppSearchState(appLibraryPageModelFactory)
    private val appSearchController = AppSearchController(
        activity = activity,
        mainHandler = mainHandler,
        drawables = drawables,
        appLibraryStore = appLibraryStore,
        appSearchState = appSearchState,
        refreshAppStack = ::refreshAppStack,
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
            pinApp = ::pinApp,
            unpinApp = ::unpinApp,
            openAppInfo = { app -> openAppInfo(app.packageName, app.userHandle, app.componentName) },
            hideApp = ::hideApp,
            appShortcuts = { chapter -> notificationRepository.getAppShortcuts(chapter) },
            launchShortcut = { shortcut ->
                if (!notificationRepository.launchShortcut(shortcut)) {
                    Toast.makeText(activity, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
                }
            },
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
    private val settingsPageFactory = SettingsPageFactory(
        activity = activity,
        settings = settings,
        drawables = drawables,
        calendarRepository = calendarRepository,
        notificationRepository = notificationRepository,
        cardStackController = cardStackController,
        actions = SettingsPageActions(
            toggleNotificationSurface = ::toggleNotificationSurface,
            toggleCardHaptics = ::toggleCardHaptics,
            toggleSplitAppsByProfile = ::toggleSplitAppsByProfile,
            toggleWorkNotificationChapterPlacement = ::toggleWorkNotificationChapterPlacement,
            applyTimescapeStackPreset = ::applyTimescapeStackPreset,
            toggleAdvancedStackControls = ::toggleAdvancedStackControls,
            restoreNotificationSource = notificationActionController::restoreNotificationSource,
            render = { render() },
            performCardScrollHaptic = ::performCardScrollHaptic,
        ),
    )
    private val focusOverlay = FocusOverlayController(
        activity,
        mainHandler,
        drawables,
        ::label,
        { currentScreen },
        { activePreferences.focusBlurRadius },
    )
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
        toggleNotificationGrouping = ::toggleNotificationGrouping,
    )
    private val stateExecutor = Executors.newFixedThreadPool(4)
    private val stateGeneration = AtomicInteger(0)
    private val deferredRender = Runnable { refreshStateAsync() }
    private val notificationRefresh = Runnable {
        notificationCardDisplayCache.clear()
        requestRender()
    }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidateAppDataAndRefresh()
        }
    }

    private var selectedPackageName = launcherStateViewModel.uiState.value.selectedPageKey ?: CalmTheme.OVERVIEW_KEY
    private var currentPager: ViewPager2? = null
    private var currentScreen: View? = null
    private val currentUiState: LauncherRenderModel?
        get() = launcherStateViewModel.uiState.value.renderModel
    private var activePreferences: LauncherUiPreferences = settings.uiPreferences()
    private var appCardSettingsSnapshot: LauncherUiPreferences = activePreferences
    private var settingsChangeToken = settings.launcherChangeToken()
    private var packageChangeReceiverRegistered = false
    private var pagePrewarmGeneration = 0
    private var appLibraryEventGeneration = 0
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
            refreshStateAsync()
        } else {
            render(buildUiState(), animate = true)
        }
        refreshLaunchableAppsInBackground()
    }

    fun onResume() {
        CalmNotificationListenerService.addListener(notificationRefresh)
        val hasCurrentScreen = currentScreen != null
        val hasCurrentState = currentUiState != null
        val launcherSettingsChanged = settingsChangeToken != settings.launcherChangeToken()
        if (resumeRefreshPolicy.shouldRefreshImmediately(
                hasCurrentScreen = hasCurrentScreen,
                hasCurrentState = hasCurrentState,
                launcherSettingsChanged = launcherSettingsChanged,
            )
        ) {
            if (hasCurrentScreen && hasCurrentState) {
                refreshStateAsync()
                return
            }
            render(buildUiState(), animate = true)
        }
    }

    fun onPause() {
        CalmNotificationListenerService.removeListener(notificationRefresh)
    }

    fun onDestroy() {
        unregisterPackageChangeReceiver()
    }

    fun onCalendarPermissionResult() {
        refreshStateAsync()
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

        val pagerAdapter = ChapterPagerAdapter(pages) { page -> createPage(page, state) }
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
                if (suppressedPageEntryKey != selectedPackageName) {
                    suppressedPageEntryKey = null
                }
                appSearchController.resetInactiveExcept(selectedPackageName)
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
        carouselController.update(pages, initialPage)
        activity.setContentView(screen)
        if (animate) {
            pager.post { entryAnimator.animateCurrentPage(pager) }
        }
        schedulePagePrewarm(pager, pagerAdapter, pages.size, initialPage)
    }

    private fun resolveInitialPage(pages: List<ChapterPage>): Int {
        val selection = pageSelectionResolver.resolve(pages, selectedPackageName)
        if (selection.key != selectedPackageName) {
            selectPage(selection.key)
        }
        return selection.index
    }

    private fun selectPage(pageKey: String) {
        selectedPackageName = pageKey
        launcherStateViewModel.selectPage(pageKey)
    }

    private fun createPage(page: ChapterPage, state: LauncherRenderModel): View {
        return when {
            page.appScope != null -> createAppLibraryPage(page, state.appEntries)
            page.key == CalmTheme.PINNED_KEY -> createPinnedPage(state.pinnedApps)
            page.chapter == null -> createOverviewPage(state)
            else -> createChapterPage(page.chapter)
        }
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

    private fun refreshStateAsync() {
        launcherStateViewModel.markLoading()
        val generation = stateGeneration.incrementAndGet()
        val notifications = stateExecutor.submit<List<AppChapter>> {
            notificationRepository.buildNotificationChapters()
        }
        val apps = stateExecutor.submit<List<AppEntry>> {
            loadAppEntries()
        }
        val calendar = stateExecutor.submit<Pair<Boolean, List<CalendarEvent>>> {
            val hasPermission = calendarRepository.hasCalendarPermission()
            hasPermission to if (hasPermission) calendarRepository.loadUpcomingEvents() else emptyList()
        }
        stateExecutor.execute {
            val appEntries = apps.get()
            val pinnedKeys = settings.pinnedPackages()
            val calendarState = calendar.get()
            val state = renderModelFactory.create(
                preferences = settings.uiPreferences(),
                notificationChapters = notifications.get(),
                appEntries = appEntries,
                pinnedKeys = pinnedKeys,
                hasCalendarPermission = calendarState.first,
                calendarEvents = calendarState.second,
            )
            appCardDisplayCache.preloadNow(state.appEntries, state.pinnedKeys)
            mainHandler.post {
                if (generation == stateGeneration.get()) {
                    render(state, animate = false)
                }
            }
        }
    }

    private fun refreshLaunchableAppsInBackground() {
        val scheduled = notificationRepository.refreshLaunchableApps(stateExecutor) { result ->
            mainHandler.post {
                if (result.changed) {
                    appCardDisplayCache.clear()
                    notificationCardDisplayCache.clear()
                }
                appLibraryEventGeneration++
                val state = appLibraryStore.replace(result.apps.filterNot(settings::isAppHidden))
                refreshVisibleAppLibraryPages(state)
            }
        }
        if (scheduled) {
            applyAppLibraryEvent(AppLibraryRenderEvent.LoadingStarted)
        }
    }

    private fun applyAppLibraryEvent(event: AppLibraryRenderEvent) {
        val state = appLibraryStore.dispatch(event)
        refreshVisibleAppLibraryPages(state)
    }

    private fun refreshVisibleAppLibraryPages(state: AppLibraryRenderState) {
        appSearchController.refreshVisible(state)
    }

    private fun buildUiState(): LauncherRenderModel {
        val appEntries = loadCachedAppEntries()
        val notificationChapters = notificationRepository.buildNotificationChapters(appEntries)
        val pinnedKeys = settings.pinnedPackages()
        val hasCalendarPermission = calendarRepository.hasCalendarPermission()
        return renderModelFactory.create(
            preferences = settings.uiPreferences(),
            notificationChapters = notificationChapters,
            appEntries = appEntries,
            pinnedKeys = pinnedKeys,
            hasCalendarPermission = hasCalendarPermission,
            calendarEvents = if (hasCalendarPermission) calendarRepository.loadUpcomingEvents() else emptyList(),
        )
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

    private fun createOverviewPage(state: LauncherRenderModel): LinearLayout {
        return createBarePagePanel().apply {
            addView(overviewHeader())
            addView(sectionTitle("Notifications"))
            addView(stackToolbarSpacer(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)))
            val notifContainer = FrameLayout(activity).apply {
                clipChildren = false
                clipToPadding = false
            }
            var rebuild: () -> Unit = {}
            rebuild = {
                notifContainer.removeAllViews()
                notifContainer.addView(
                    overviewNotificationsStack(state.notificationChapters) { rebuild() },
                    matchParentParams()
                )
            }
            rebuild()
            addView(notifContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun createPinnedPage(pinnedApps: List<AppEntry>): LinearLayout {
        return createBarePagePanel(activity.dp(20)).apply {
            addView(animatedChrome(label("Pinned", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, activity.dp(24))
            }))
            addView(
                appStack(pinnedApps, stackKey = CardStackStateKey.appEntries("pinned", pinnedApps)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
    }

    private fun createAppLibraryPage(pageModel: ChapterPage, appEntries: List<AppEntry>): LinearLayout {
        val scope = pageModel.appScope ?: AppLibraryScope.ALL
        val model = appLibraryPageModelFactory.create(
            page = pageModel,
            appEntries = appEntries,
            query = appSearchController.queryFor(scope),
            loading = appLibraryStore.state().loading,
        )
        val page = createBarePagePanel(activity.dp(20))
        val header = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(label(model.title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            })
            model.subtitle?.let { subtitle ->
                addView(label(subtitle, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    setPadding(0, activity.dp(6), 0, activity.dp(18))
                })
            }
        }
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val stackHost = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        page.addView(stackHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val searchBox = appSearchController.registerPage(pageModel, page, header, stackHost, model)
        page.addView(animatedChrome(searchBox), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12)
        })
        return page
    }

    private fun refreshAppStack(
        stackHost: FrameLayout,
        model: AppLibraryPageModel,
        cardCache: MutableMap<String, TextView>? = null,
    ) {
        cardCache?.values?.forEach { card ->
            (card.parent as? ViewGroup)?.removeView(card)
        }
        stackHost.removeAllViews()
        if (model.apps.isEmpty()) {
            stackHost.addView(appSearchEmptyStack(model.emptyMessage, appLibraryStackKey(model)), matchParentParams())
        } else {
            val plan = appStackRenderPlanner.plan(model.apps, activePreferences.cardStackTuning)
            val cards = plan.initialApps.map { app -> appCardFromCache(app, cardCache) }.toMutableList()
            val stack = appStack(cards, appLibraryStackKey(model))
            stackHost.addView(stack, matchParentParams())
            appendDeferredAppCards(stackHost, stack, cards, plan.deferredApps, cardCache, model)
        }
    }

    private fun appendDeferredAppCards(
        stackHost: FrameLayout,
        stack: ScrollView,
        renderedCards: MutableList<TextView>,
        deferredApps: List<AppEntry>,
        cardCache: MutableMap<String, TextView>?,
        model: AppLibraryPageModel,
    ) {
        var nextDeferredIndex = 0
        fun appendNextBatch(): Boolean {
            if (nextDeferredIndex >= deferredApps.size) return false
            val end = (nextDeferredIndex + APP_STACK_DEFERRED_BATCH_SIZE).coerceAtMost(deferredApps.size)
            val batch = deferredApps.subList(nextDeferredIndex, end)
            nextDeferredIndex = end
            val newCards = batch.map { app -> appCardFromCache(app, cardCache) }
            cardStackController.appendCards(stack, renderedCards, newCards, cardRenderer.cardHeight(), cardRenderer.cardStep(), activePreferences.cardStackTuning)
            return true
        }
        fun ensureRendered(cardIndex: Int) {
            while (renderedCards.size <= cardIndex && appendNextBatch()) {
            }
        }
        fun scheduleNextBatch(delayMs: Long) {
            mainHandler.postDelayed({
                if (stack.parent == null) return@postDelayed
                if (!stackHost.isAttachedToWindow) {
                    scheduleNextBatch(APP_STACK_DEFERRED_BATCH_DELAY_MS)
                    return@postDelayed
                }
                if (currentPager?.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                    scheduleNextBatch(APP_STACK_DEFERRED_BATCH_DELAY_MS)
                    return@postDelayed
                }
                if (appendNextBatch()) {
                    scheduleNextBatch(nextDeferredBatchDelay(stack))
                }
            }, delayMs)
        }
        appQuickScrollController.attach(stackHost, stack, model, activePreferences.cardStackTuning, ::ensureRendered)
        if (deferredApps.isNotEmpty()) {
            scheduleNextBatch(APP_STACK_DEFERRED_INITIAL_DELAY_MS)
        }
    }

    private fun nextDeferredBatchDelay(stack: ScrollView): Long {
        return if (cardStackController.hasPendingRestore(stack)) {
            APP_STACK_PENDING_RESTORE_BATCH_DELAY_MS
        } else {
            APP_STACK_DEFERRED_BATCH_DELAY_MS
        }
    }

    private fun appStack(
        apps: List<AppEntry>,
        cardCache: MutableMap<String, TextView>? = null,
        stackKey: String = CardStackStateKey.appEntries("apps", apps),
    ): ScrollView {
        return appStack(apps.map { app -> appCardFromCache(app, cardCache) }.toMutableList(), stackKey)
    }

    private fun appStack(cards: MutableList<TextView>, stackKey: String): ScrollView {
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences.cardStackTuning,
            stackKey,
        )
    }

    private fun appCardFromCache(app: AppEntry, cardCache: MutableMap<String, TextView>? = null): TextView {
        return cardCache?.getOrPut(app.identityKey) { appCard(app) } ?: appCard(app)
    }

    private fun appSearchEmptyStack(message: String, stackKey: String): View {
        val card = cardRenderer.stackCard(
            "Search\n$message",
            CalmTheme.ACCENT,
            true,
            cardRenderer.cardSideIcon(R.drawable.ic_search_card),
            sideImageRenderKey = "res:${R.drawable.ic_search_card}",
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 3
            isEnabled = false
        }
        return cardStackController.cardStack(
            listOf(card),
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences.cardStackTuning,
            stackKey,
        )
    }

    private fun appLibraryStackKey(model: AppLibraryPageModel): String {
        return CardStackStateKey.appLibrary(model.key, model.scope, model.query)
    }

    private fun appCard(app: AppEntry): TextView {
        val data = appCardDisplayCache.getCachedOrCreateLightweight(app, currentUiState?.pinnedKeys ?: settings.pinnedPackages())
        return cardRenderer.stackCard(data.text, data.hueColor, true, data.icon, sideImageRenderKey = data.iconRenderKey).apply {
            maxLines = 4
            setOnClickListener { openAppEntry(app) }
            setOnLongClickListener {
                focusOverlay.show(this, contextActionFactory.appActions(data.app, data.isPinned), data.app.label)
                true
            }
        }
    }

    private fun settingsButton(): ImageButton {
        return ImageButton(activity).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(CalmTheme.INK)
            scaleType = ImageView.ScaleType.CENTER
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(14))
            contentDescription = "Open settings"
            tooltipText = "Settings"
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            setOnClickListener { openSettingsActivity() }
        }
    }

    private fun overviewHeader(): View {
        val nextAlarm = nextAlarmClock()
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, activity.dp(24))

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("Overview", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, activity.dp(8), 0, 0)
                })
                addView(label(nextAlarmSummary(nextAlarm), 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    setPadding(0, activity.dp(6), 0, 0)
                })
                if (nextAlarm != null) {
                    isClickable = true
                    setOnClickListener { openNextAlarm(nextAlarm) }
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(settingsButton(), LinearLayout.LayoutParams(activity.dp(58), activity.dp(58)).apply {
                leftMargin = activity.dp(14)
            })
        }
    }

    private fun loadAppEntries(): List<AppEntry> {
        val apps = notificationRepository.loadAppEntries()
            .filterNot(settings::isAppHidden)
        appLibraryEventGeneration++
        appLibraryStore.replace(apps)
        return apps
    }

    private fun loadCachedAppEntries(): List<AppEntry> {
        val apps = notificationRepository.loadCachedAppEntries()
            .filterNot(settings::isAppHidden)
        appLibraryEventGeneration++
        appLibraryStore.replace(apps)
        return apps
    }

    private fun loadPinnedApps(): List<AppEntry> {
        val pinnedPackages = settings.pinnedPackages()
        if (pinnedPackages.isEmpty()) return emptyList()
        return notificationRepository.loadPinnedAppEntries(pinnedPackages)
            .filterNot(settings::isAppHidden)
    }

    private fun isPinned(app: AppEntry): Boolean {
        val pinned = currentUiState?.pinnedKeys ?: settings.pinnedPackages()
        return pinnedAppResolver.isPinned(app, pinned)
    }

    private fun createSettingsPage(): View = settingsPageFactory.create()

    private fun createChapterPage(chapter: AppChapter): LinearLayout = chapterPageBuilder.buildPage(chapter)

    private fun stackToolbarSpacer(): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
        }
    }

    private fun overviewCalendarStack(state: LauncherRenderModel): View {
        val cards = mutableListOf<TextView>()
        if (state.hasCalendarPermission) {
            if (state.calendarEvents.isEmpty()) {
                cards.add(
                    cardRenderer.stackCard(
                        "Upcoming calendar\nNo upcoming calendar events found.",
                        CalmTheme.ACCENT,
                        true,
                        cardRenderer.cardSideIcon(R.drawable.ic_calendar_card),
                        sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
                    ),
                )
            } else {
                cards.addAll(state.calendarEvents.map(::calendarCard))
            }
        } else {
            cards.add(
                cardRenderer.stackCard(
                    "Calendar access\nCalendar access is needed before Calm can index upcoming events.\nManage it in Settings.",
                    CalmTheme.ACCENT,
                    true,
                    cardRenderer.cardSideIcon(R.drawable.ic_calendar_card),
                    sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
                ),
            )
        }
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences.cardStackTuning,
            CardStackStateKey.OVERVIEW_CALENDAR,
        )
    }

    private fun overviewNotificationsStack(
        chapters: List<AppChapter>,
        onRebuild: () -> Unit,
    ): ScrollView {
        val sortedChapters = chapters
            .filter { it.notifications.isNotEmpty() }
            .sortedByDescending { chapter -> chapter.notifications.maxOf { it.postTime } }
        val cards = mutableListOf<TextView>()
        for (chapter in sortedChapters) {
            val isExpanded = chapter.identityKey in expandedOverviewGroups
            cards.add(overviewGroupHeaderCard(chapter, isExpanded, onRebuild))
            if (isExpanded) {
                NotificationCardGrouper.cards(chapter.notifications, groupingEnabled = false).forEach { item ->
                    cards.add(overviewNotificationSubCard(item, chapter))
                }
            }
        }
        if (cards.isEmpty()) {
            cards.add(
                cardRenderer.stackCard(
                    "All clear\nNo active notifications right now.",
                    CalmTheme.ACCENT,
                    true,
                ),
            )
        }
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences.cardStackTuning,
            CardStackStateKey.OVERVIEW_NOTIFICATIONS,
        )
    }

    private fun overviewGroupHeaderCard(
        chapter: AppChapter,
        isExpanded: Boolean,
        onRebuild: () -> Unit,
    ): TextView {
        val count = chapter.notifications.size
        val action = if (isExpanded) "collapse" else "expand"
        val text = "${chapter.label}\n$count notification${if (count == 1) "" else "s"} · tap to $action"
        val icon = notificationCardDisplayCache.chapterMaskedIcon(chapter)
        return cardRenderer.stackCard(
            text,
            chapter.hueColor,
            activePreferences.useTintedNotificationCards,
            icon,
            sideImageRenderKey = "icon:${chapter.identityKey}",
        ).apply {
            setOnClickListener {
                if (!expandedOverviewGroups.remove(chapter.identityKey)) {
                    expandedOverviewGroups.add(chapter.identityKey)
                }
                onRebuild()
            }
        }
    }

    private fun overviewNotificationSubCard(
        item: NotificationCardItem,
        chapter: AppChapter,
    ): TextView {
        val data = notificationCardDisplayCache.getOrCreate(item, chapter, ::formatNotificationTime)
        return cardRenderer.stackCard(
            data.text,
            chapter.hueColor,
            activePreferences.useTintedNotificationCards,
            data.sideImage,
            data.sideImageAlpha,
            data.sideImageRenderKey,
        ).apply {
            maxLines = 3
            setPadding(paddingLeft + activity.dp(20), paddingTop, paddingRight, paddingBottom)
            setOnClickListener {
                focusOverlay.show(this, contextActionFactory.notificationActions(item, chapter), data.fullText)
            }
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

    private fun nextAlarmClock(): AlarmManager.AlarmClockInfo? {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock
        return nextAlarm?.takeIf { it.triggerTime > System.currentTimeMillis() }
    }

    private fun nextAlarmSummary(nextAlarm: AlarmManager.AlarmClockInfo?): String {
        if (nextAlarm == null) return "No upcoming alarm is scheduled."
        val alarmTime = DateFormat.getTimeFormat(activity).format(Date(nextAlarm.triggerTime))
        return "Next alarm $alarmTime"
    }

    private fun openNextAlarm(nextAlarm: AlarmManager.AlarmClockInfo) {
        val showIntent = nextAlarm.showIntent
        if (showIntent != null) {
            try {
                showIntent.send()
                return
            } catch (_: Exception) {
            }
        }
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun calendarCard(event: CalendarEvent): TextView {
        val title = event.title.takeUnless { it.isBlank() } ?: "Untitled event"
        val location = event.location.takeUnless { it.isBlank() }?.let { "\n$it" }.orEmpty()
        val today = calendarRepository.isToday(event.begin)
        return cardRenderer.stackCard(
            "${if (today) "Today" else "Upcoming"}\n$title\n${calendarRepository.formatEventTime(event)}$location",
            if (today) CalmTheme.ACCENT else Color.rgb(122, 146, 178),
            true,
            cardRenderer.cardSideIcon(R.drawable.ic_calendar_card),
            sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
        ).apply {
            setOnClickListener { openCalendarEvent(event) }
            setOnLongClickListener {
                focusOverlay.show(
                    this,
                    contextActionFactory.calendarActions(event, calendarRepository.hasCalendarPermission()),
                )
                true
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

    private fun sectionTitle(text: String): TextView {
        return label(text.uppercase(Locale.getDefault()), 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
            tag = CalmAnimationTags.CHROME
            setPadding(0, activity.dp(14), 0, activity.dp(8))
        }
    }

    private fun animatedChrome(view: View): View {
        return view.apply { tag = CalmAnimationTags.CHROME }
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

    private fun pinApp(app: AppEntry) {
        settings.pinPackage(app.identityKey)
        selectPage(CalmTheme.PINNED_KEY)
        Toast.makeText(activity, "Pinned ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun unpinApp(app: AppEntry) {
        settings.unpinPackage(app.identityKey)
        settings.unpinPackage(app.packageName)
        if (loadPinnedApps().isEmpty()) {
            selectPage(CalmTheme.APP_LIBRARY_KEY)
        }
        Toast.makeText(activity, "Unpinned ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun hideApp(app: AppEntry) {
        settings.setHiddenAppKeys(settings.hiddenAppKeys() + app.identityKey)
        Toast.makeText(activity, "Hidden ${app.label}", Toast.LENGTH_SHORT).show()
        render()
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
        } catch (_: Exception) {
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

    private fun toggleNotificationSurface() {
        val nextValue = settings.toggleNotificationSurface()
        Toast.makeText(activity, if (nextValue) "Notification cards are tinted" else "Chapter panels are tinted", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleSplitAppsByProfile() {
        val nextValue = settings.toggleSplitAppsByProfile()
        if (selectedPackageName == CalmTheme.APP_LIBRARY_KEY ||
            selectedPackageName == CalmTheme.PERSONAL_APP_LIBRARY_KEY ||
            selectedPackageName == CalmTheme.WORK_APP_LIBRARY_KEY
        ) {
            selectPage(if (nextValue) CalmTheme.PERSONAL_APP_LIBRARY_KEY else CalmTheme.APP_LIBRARY_KEY)
        }
        Toast.makeText(activity, if (nextValue) "Apps split by profile" else "Apps combined", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleWorkNotificationChapterPlacement() {
        val nextValue = settings.toggleWorkNotificationChaptersBeforeApps()
        Toast.makeText(activity, if (nextValue) "Work notification chapters moved left" else "Work notification chapters moved right", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleNotificationGrouping(chapter: AppChapter) {
        val nextGrouped = settings.toggleNotificationGrouping(chapter.identityKey)
        Toast.makeText(activity, if (nextGrouped) "Notifications grouped" else "Notifications split", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleCardHaptics() {
        val nextValue = settings.toggleCardHaptics()
        if (nextValue) performCardScrollHaptic(activity.window.decorView)
        Toast.makeText(activity, if (nextValue) "Card haptics on" else "Card haptics off", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun applyTimescapeStackPreset() {
        settings.applyTimescapeStackPreset()
        Toast.makeText(activity, "Timescape stack preset applied", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleAdvancedStackControls() {
        val nextValue = settings.toggleAdvancedStackControls()
        Toast.makeText(activity, if (nextValue) "Advanced stack controls shown" else "Advanced stack controls hidden", Toast.LENGTH_SHORT).show()
        render()
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
        refreshStateAsync()
    }

    private companion object {
        const val PAGE_PREWARM_INITIAL_DELAY_MS = 260L
        const val PAGE_PREWARM_STEP_DELAY_MS = 120L
        const val PAGE_PREWARM_MAX_PAGES = 3
        const val APP_STACK_DEFERRED_BATCH_SIZE = 16
        const val APP_STACK_DEFERRED_INITIAL_DELAY_MS = 48L
        const val APP_STACK_PENDING_RESTORE_BATCH_DELAY_MS = 0L
        const val APP_STACK_DEFERRED_BATCH_DELAY_MS = 32L
    }
}
