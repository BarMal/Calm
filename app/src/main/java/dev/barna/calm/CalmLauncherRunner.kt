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
import android.graphics.Rect
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
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.EnumMap
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
    private val pinnedAppResolver = PinnedAppResolver()
    private val resumeRefreshPolicy = ResumeRefreshPolicy()
    private val renderModelFactory = LauncherRenderModelFactory(
        LauncherPageStateFactory(pinnedAppResolver = pinnedAppResolver),
    )
    private val appCardModelFactory = AppCardModelFactory(pinnedAppResolver = pinnedAppResolver)
    private val appCardDisplayCache = AppCardDisplayCache(notificationRepository, appCardModelFactory)
    private val notificationCardDisplayCache = NotificationCardDisplayCache(notificationRepository)
    private val cardRenderAssetCache = CardRenderAssetCache()
    private val appLibraryPageModelFactory = AppLibraryPageModelFactory()
    private val appStackRenderPlanner = AppStackRenderPlanner()
    private val appLibraryStore = AppLibraryRenderStore()
    private val contextActionFactory = LauncherContextActionFactory(
        LauncherContextActionCallbacks(
            openNotification = ::openNotification,
            openPackage = ::openPackage,
            dismissNotificationItem = ::dismissNotificationItem,
            clearChapter = ::clearChapter,
            performNotificationAction = ::performNotificationAction,
            openCalendarEvent = ::openCalendarEvent,
            requestCalendarAccess = { calendarRepository.requestCalendarAccess() },
            openSettings = ::openSettingsActivity,
            openAppEntry = ::openAppEntry,
            pinApp = ::pinApp,
            unpinApp = ::unpinApp,
            openAppInfo = { app -> openAppInfo(app.packageName, app.userHandle, app.componentName) },
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
    private val settingsPageFactory = SettingsPageFactory(
        activity = activity,
        settings = settings,
        drawables = drawables,
        calendarRepository = calendarRepository,
        notificationRepository = notificationRepository,
        actions = SettingsPageActions(
            toggleNotificationSurface = ::toggleNotificationSurface,
            toggleCardHaptics = ::toggleCardHaptics,
            toggleSplitAppsByProfile = ::toggleSplitAppsByProfile,
            toggleWorkNotificationChapterPlacement = ::toggleWorkNotificationChapterPlacement,
            applyTimescapeStackPreset = ::applyTimescapeStackPreset,
            toggleAdvancedStackControls = ::toggleAdvancedStackControls,
            restoreNotificationSource = ::restoreNotificationSource,
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
    private var chapterCarousel: HorizontalScrollView? = null
    private var chapterCarouselRow: LinearLayout? = null
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
    private var selectedCarouselPosition = -1
    private val appSearchQueries = EnumMap<AppLibraryScope, String>(AppLibraryScope::class.java)
    private val appSearchPages = ArrayList<AppSearchPageState>()
    private val sideIconCache = HashMap<Int, android.graphics.Bitmap?>()

    private data class AppSearchPageState(
        val key: String,
        val scope: AppLibraryScope,
        val pageModel: ChapterPage,
        val page: View,
        val header: View,
        val stackHost: FrameLayout,
        val search: EditText,
        val cardCache: MutableMap<String, TextView>,
    )

    private data class AppSearchControl(
        val root: View,
        val search: EditText,
    )

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
        appSearchPages.clear()
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
        var userSwipeInProgress = false
        var lastAnimatedPageKey: String? = null
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                centerCarouselPosition(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                selectPage(pages[position].key)
                if (suppressedPageEntryKey != selectedPackageName) {
                    suppressedPageEntryKey = null
                }
                resetInactiveAppSearchPages(selectedPackageName)
            }

            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        userSwipeInProgress = true
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        val currentPage = pages[pager.currentItem]
                        updateChapterCarousel(pages, pager.currentItem)
                        resetInactiveAppSearchPages(currentPage.key)
                        if (!userSwipeInProgress && suppressedPageEntryKey != currentPage.key && lastAnimatedPageKey != currentPage.key) {
                            lastAnimatedPageKey = currentPage.key
                            pager.post { entryAnimator.animateCurrentPage(pager) }
                        }
                        if (suppressedPageEntryKey == currentPage.key) {
                            suppressedPageEntryKey = null
                        }
                        userSwipeInProgress = false
                    }
                }
            }
        })

        root.addView(createChapterCarousel(pages, initialPage))
        root.addView(
            pager,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        updateChapterCarousel(pages, initialPage)
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
        appSearchPages.forEach { pageState ->
            val model = appLibraryPageModelFactory.create(
                page = pageState.pageModel,
                appEntries = state.apps,
                query = appSearchQuery(pageState.scope),
                loading = state.loading,
            )
            refreshAppStack(pageState.stackHost, model, pageState.cardCache)
        }
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

    private fun createChapterCarousel(pages: List<ChapterPage>, selectedPosition: Int): View {
        val spine = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = activity.dp(12)
                bottomMargin = activity.dp(18)
            }
        }
        spine.addView(spineLine())
        chapterCarousel = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, activity.dp(3), 0, activity.dp(3))
            setBackgroundColor(Color.TRANSPARENT)
        }
        chapterCarouselRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        chapterCarousel?.addView(
            chapterCarouselRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        spine.addView(chapterCarousel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        spine.addView(spineLine())
        renderChapterCarouselItems(pages, selectedPosition)
        chapterCarousel?.post {
            updateCarouselCenterPadding()
            centerCarouselItem(selectedPosition, smooth = false)
        }
        return spine
    }

    private fun spineLine(): View {
        return View(activity).apply {
            setBackgroundColor(Color.argb(52, Color.red(CalmTheme.ACCENT), Color.green(CalmTheme.ACCENT), Color.blue(CalmTheme.ACCENT)))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, activity.dp(1)))
        }
    }

    private fun createOverviewPage(state: LauncherRenderModel): LinearLayout {
        return createBarePagePanel().apply {
            addView(overviewHeader())
            addView(sectionTitle("Upcoming calendar"))
            addView(stackToolbarSpacer(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)))
            addView(overviewCalendarStack(state), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun createPinnedPage(pinnedApps: List<AppEntry>): LinearLayout {
        return createBarePagePanel(activity.dp(20)).apply {
            addView(animatedChrome(label("CHAPTER / PINNED", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                setPadding(0, 0, 0, activity.dp(18))
            }))
            addView(animatedChrome(label("Pinned", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            }))
            addView(animatedChrome(label("Pinned apps stay one chapter left of Overview.", 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, activity.dp(24))
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
            query = appSearchQuery(scope),
            loading = appLibraryStore.state().loading,
        )
        val page = createBarePagePanel(activity.dp(20))
        val header = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(label("CHAPTER / ${model.title.uppercase(Locale.getDefault())}", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                setPadding(0, 0, 0, activity.dp(18))
            })
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

        val cardCache = LinkedHashMap<String, TextView>()
        val searchControl = appSearchBox(page, header, stackHost, pageModel, cardCache)
        page.addView(animatedChrome(searchControl.root), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12)
        })
        val search = searchControl.search
        appSearchPages.add(AppSearchPageState(pageModel.key, scope, pageModel, page, header, stackHost, search, cardCache))
        installAppSearchKeyboardAnimator(page, header, search)
        refreshAppStack(stackHost, model, cardCache)
        return page
    }

    private fun appSearchBox(
        page: LinearLayout,
        header: LinearLayout,
        stackHost: FrameLayout,
        pageModel: ChapterPage,
        cardCache: MutableMap<String, TextView>,
    ): AppSearchControl {
        val scope = pageModel.appScope ?: AppLibraryScope.ALL
        val initialQuery = appSearchQuery(scope)
        val root = FrameLayout(activity).apply {
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            clipToPadding = false
            clipChildren = false
        }
        val clearButton = ImageButton(activity).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            setImageResource(R.drawable.ic_clear_search)
            setColorFilter(CalmTheme.MUTED_INK)
            contentDescription = "Clear search"
            visibility = if (initialQuery.isBlank()) View.GONE else View.VISIBLE
        }
        val search = EditText(activity).apply {
            setText(initialQuery)
            hint = "Search apps"
            setSingleLine(true)
            setTextColor(CalmTheme.INK)
            setHintTextColor(CalmTheme.MUTED_INK)
            textSize = 16f
            typeface = Typeface.DEFAULT
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(activity.dp(16), activity.dp(12), activity.dp(50), activity.dp(12))
            setSelectAllOnFocus(false)
            setOnFocusChangeListener { view, hasFocus ->
                animateAppSearchHeader(header, hasFocus)
                if (!hasFocus) {
                    animateAppSearchPage(page, 0)
                }
                if (hasFocus) {
                    view.post {
                        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            addTextChangedListener(object : TextWatcher {
                var pendingSearchRefresh: Runnable? = null

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    if (query == appSearchQuery(scope)) return
                    setAppSearchQuery(scope, query)
                    clearButton.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                    pendingSearchRefresh?.let(mainHandler::removeCallbacks)
                    pendingSearchRefresh = Runnable {
                        refreshAppStack(
                            stackHost,
                            appLibraryPageModelFactory.create(
                                page = pageModel,
                                appEntries = appLibraryStore.state().apps,
                                query = query,
                                loading = appLibraryStore.state().loading,
                            ),
                            cardCache,
                        )
                    }.also { refresh ->
                        mainHandler.postDelayed(refresh, APP_SEARCH_REFRESH_DELAY_MS)
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        clearButton.setOnClickListener {
            search.setText("")
            search.clearFocus()
            hideKeyboard(search)
            animateAppSearchHeader(header, false)
            animateAppSearchPage(page, 0)
        }
        root.addView(search, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(clearButton, FrameLayout.LayoutParams(activity.dp(44), activity.dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = activity.dp(2)
        })
        return AppSearchControl(root, search)
    }

    private fun appSearchQuery(scope: AppLibraryScope): String = appSearchQueries[scope].orEmpty()

    private fun setAppSearchQuery(scope: AppLibraryScope, query: String) {
        if (query.isBlank()) {
            appSearchQueries.remove(scope)
        } else {
            appSearchQueries[scope] = query
        }
    }

    private fun installAppSearchKeyboardAnimator(page: LinearLayout, header: LinearLayout, search: EditText) {
        page.viewTreeObserver.addOnGlobalLayoutListener {
            if (!search.hasFocus()) return@addOnGlobalLayoutListener
            val keyboardHeight = keyboardHeight()
            val visible = keyboardHeight > activity.dp(120)
            animateAppSearchHeader(header, visible)
            animateAppSearchPage(page, if (visible) keyboardHeight else 0)
            if (!visible && search.text.isNullOrBlank()) {
                search.clearFocus()
            }
        }
    }

    private fun keyboardHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.rootWindowInsets?.let { insets ->
                if (insets.isVisible(WindowInsets.Type.ime())) {
                    return insets.getInsets(WindowInsets.Type.ime()).bottom
                }
            }
        }
        val visibleFrame = Rect()
        val root = activity.window.decorView
        root.getWindowVisibleDisplayFrame(visibleFrame)
        return maxOf(0, root.height - visibleFrame.bottom)
    }

    private fun animateAppSearchHeader(header: View, collapsed: Boolean) {
        header.animate()
            .alpha(if (collapsed) 0f else 1f)
            .translationY(if (collapsed) -activity.dp(18).toFloat() else 0f)
            .setDuration(180L)
            .start()
    }

    private fun animateAppSearchPage(page: View, keyboardHeight: Int) {
        val target = if (keyboardHeight > 0) {
            -(keyboardHeight - activity.dp(34)).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        if (kotlin.math.abs(page.translationY - target) < 1f) return
        page.animate()
            .translationY(target)
            .setDuration(220L)
            .start()
    }

    private fun resetInactiveAppSearchPages(activeKey: String) {
        appSearchPages
            .filter { it.key != activeKey }
            .forEach(::resetAppSearchPage)
    }

    private fun resetAllAppSearchPages() {
        appSearchPages.forEach(::resetAppSearchPage)
    }

    private fun resetAppSearchPage(state: AppSearchPageState) {
        if (state.search.hasFocus()) {
            state.search.clearFocus()
            hideKeyboard(state.search)
        }
        if (state.search.text?.isNotBlank() == true) {
            state.search.setText("")
        } else {
            setAppSearchQuery(state.scope, "")
        }
        state.header.animate().cancel()
        state.page.animate().cancel()
        state.header.alpha = 1f
        state.header.translationY = 0f
        state.page.translationY = 0f
    }

    private fun hideKeyboard(view: View) {
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
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
            cardStackController.appendCards(stack, renderedCards, newCards, cardHeight(), cardStep(), activePreferences.cardStackTuning)
            return true
        }
        fun ensureRendered(cardIndex: Int) {
            while (renderedCards.size <= cardIndex && appendNextBatch()) {
            }
        }
        fun scheduleNextBatch(delayMs: Long) {
            mainHandler.postDelayed({
                if (stack.parent == null || !stackHost.isAttachedToWindow) return@postDelayed
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
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
            stackKey,
        )
    }

    private fun appCardFromCache(app: AppEntry, cardCache: MutableMap<String, TextView>? = null): TextView {
        return cardCache?.getOrPut(app.identityKey) { appCard(app) } ?: appCard(app)
    }

    private fun appSearchEmptyStack(message: String, stackKey: String): View {
        val card = stackCard(
            "Search\n$message",
            CalmTheme.ACCENT,
            true,
            cardSideIcon(R.drawable.ic_search_card),
            sideImageRenderKey = "res:${R.drawable.ic_search_card}",
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 3
            isEnabled = false
        }
        return cardStackController.cardStack(
            listOf(card),
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
            stackKey,
        )
    }

    private fun appLibraryStackKey(model: AppLibraryPageModel): String {
        return CardStackStateKey.appLibrary(model.key, model.scope, model.query)
    }

    private fun cardHeight(): Int = activity.dp(cardSpec.heightDp)

    private fun cardStep(): Int = activity.dp(cardSpec.stepDp)

    private fun cardCornerRadius(): Int = activity.dp(activePreferences.cardCornerRadiusDp)

    private fun stackCard(
        text: String,
        hueColor: Int,
        tinted: Boolean,
        sideImage: android.graphics.Bitmap? = null,
        sideImageAlpha: Int = 64,
        sideImageRenderKey: String? = null,
    ): TextView {
        return label(text, cardSpec.titleSp, CalmTheme.INK, Typeface.NORMAL).apply {
            val showImageAsBackground = sideImage != null && activePreferences.useCardIconBackgrounds
            val iconRenderData = if (showImageAsBackground) {
                cardRenderAssetCache.iconRenderData(
                    imageKey = sideImageRenderKey ?: "bitmap-${sideImage.generationId}",
                    image = sideImage,
                    style = CardRenderStyleKey(
                        radiusPx = cardCornerRadius(),
                        hueColor = hueColor,
                        tintCards = tinted,
                        imageAlpha = sideImageAlpha,
                        imageBlur = activePreferences.cardIconBlur,
                        useIconBackgrounds = activePreferences.useCardIconBackgrounds,
                    ),
                )
            } else {
                null
            }
            setLineSpacing(activity.dp(2).toFloat(), 1.0f)
            setPadding(
                activity.dp(cardSpec.horizontalPaddingDp),
                activity.dp(cardSpec.verticalPaddingDp),
                activity.dp(if (showImageAsBackground) 116 else cardSpec.horizontalPaddingDp),
                activity.dp(cardSpec.verticalPaddingDp),
            )
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            background = drawables.cardWithSideImage(
                cardCornerRadius(),
                hueColor,
                tinted,
                sideImage.takeIf { showImageAsBackground },
                sideImageAlpha,
                activePreferences.cardIconBlur,
                iconRenderData,
            )
            if (sideImage != null && !showImageAsBackground) {
                compoundDrawablePadding = activity.dp(14)
                setCompoundDrawables(null, null, sideImage.toCardIconDrawable(), null)
            }
            elevation = activity.dp(2).toFloat()
        }
    }

    private fun android.graphics.Bitmap.toCardIconDrawable(): android.graphics.drawable.BitmapDrawable {
        return android.graphics.drawable.BitmapDrawable(activity.resources, this).apply {
            val size = activity.dp(cardSpec.iconSizeDp)
            setBounds(0, 0, size, size)
            alpha = 214
        }
    }

    private fun appCard(app: AppEntry): TextView {
        val data = appCardDisplayCache.getCachedOrCreateLightweight(app, currentUiState?.pinnedKeys ?: settings.pinnedPackages())
        return stackCard(data.text, data.hueColor, true, data.icon, sideImageRenderKey = data.iconRenderKey).apply {
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
                addView(label("CHAPTER / OVERVIEW", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                })
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

    private fun createChapterPage(chapter: AppChapter): LinearLayout {
        val tintCards = activePreferences.useTintedNotificationCards
        val page = if (tintCards) {
            createBarePagePanel()
        } else {
            createPagePanel(notificationRepository.resolveChapterBackground(chapter), chapter.hueColor)
        }
        page.addView(chapterHeader(chapter))
        page.addView(sectionTitle("Notifications"))
        page.addView(notificationArea(chapter, tintCards), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.25f))
        return page
    }

    private fun chapterHeader(chapter: AppChapter): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, activity.dp(24))

            addView(chapterLaunchButton(chapter), LinearLayout.LayoutParams(activity.dp(58), activity.dp(58)).apply {
                rightMargin = activity.dp(14)
            })

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("CHAPTER / ${chapter.label}", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(label(chapter.label, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, activity.dp(8), 0, 0)
                })
                addView(label(notificationSummary(chapter), 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    setPadding(0, activity.dp(6), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun chapterLaunchButton(chapter: AppChapter): ImageButton {
        return ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = drawables.notificationCard(activity.dp(18), chapter.hueColor, true)
            contentDescription = "Open ${chapter.label}"
            tooltipText = "Open ${chapter.label}"
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            notificationCardDisplayCache.chapterMaskedIcon(chapter)?.let { icon ->
                setImageDrawable(icon.toSizedDrawable(activity.dp(42)))
            }
            alpha = if (chapter.launchable) 0.96f else 0.36f
            isEnabled = chapter.launchable
            if (chapter.launchable) {
                setOnClickListener { openPackage(chapter) }
            }
        }
    }

    private fun notificationArea(chapter: AppChapter, tintCards: Boolean): View {
        val mediaControls = MediaNotificationControls.from(chapter.notifications)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(stackToolbar(groupingIconButton(chapter)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)))
            addView(notificationStack(chapter, tintCards), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            if (mediaControls.hasAnyAction) {
                addView(mediaControlsRow(mediaControls), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = activity.dp(12)
                })
            }
        }
    }

    private fun mediaControlsRow(controls: MediaNotificationControls): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipToPadding = false
            clipChildren = false
            addView(mediaControlButton(R.drawable.ic_media_previous, "Previous", controls.previous), LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply {
                rightMargin = activity.dp(8)
            })
            addView(mediaControlButton(playPauseIcon(controls), controls.playPauseLabel, controls.playPause), LinearLayout.LayoutParams(0, activity.dp(46), 1.35f).apply {
                leftMargin = activity.dp(4)
                rightMargin = activity.dp(4)
            })
            addView(mediaControlButton(R.drawable.ic_media_next, "Next", controls.next), LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply {
                leftMargin = activity.dp(8)
            })
        }
    }

    private fun stackToolbar(action: View? = null): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
            action?.let {
                addView(it, LinearLayout.LayoutParams(activity.dp(38), activity.dp(30)))
            }
        }
    }

    private fun stackToolbarSpacer(): View {
        return stackToolbar()
    }

    private fun playPauseIcon(controls: MediaNotificationControls): Int {
        val label = controls.playPauseLabel.lowercase(Locale.ROOT)
        return if (label.contains("pause")) R.drawable.ic_media_pause else R.drawable.ic_media_play
    }

    private fun mediaControlButton(iconRes: Int, description: String, action: NotificationAction?): ImageButton {
        return ImageButton(activity).apply {
            setImageResource(iconRes)
            setColorFilter(if (action == null) CalmTheme.MUTED_INK else CalmTheme.INK)
            scaleType = ImageView.ScaleType.CENTER
            alpha = if (action == null) 0.34f else 0.92f
            isEnabled = action != null
            contentDescription = description
            tooltipText = description
            setPadding(activity.dp(11), activity.dp(11), activity.dp(11), activity.dp(11))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            if (action != null) {
                setOnClickListener { performNotificationAction(action) }
            }
        }
    }

    private fun groupingIconButton(chapter: AppChapter): ImageButton {
        val grouped = settings.groupNotifications(chapter.identityKey)
        val description = if (grouped) "Notifications grouped by conversation" else "Notifications split"
        return ImageButton(activity).apply {
            setImageResource(if (grouped) R.drawable.ic_grouped_notifications else R.drawable.ic_split_notifications)
            setColorFilter(CalmTheme.INK)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            tooltipText = description
            alpha = 0.84f
            background = null
            setPadding(activity.dp(7), activity.dp(4), activity.dp(7), activity.dp(4))
            setOnClickListener { toggleNotificationGrouping(chapter) }
        }
    }

    private fun notificationStack(chapter: AppChapter, tintCards: Boolean): View {
        val cards = NotificationCardGrouper.cards(
            chapter.notifications,
            settings.groupNotifications(chapter.identityKey),
        )
        return cardStackController.cardStack(
            cards.map { notificationCard(it, chapter, tintCards) },
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
            CardStackStateKey.notifications(chapter),
        )
    }

    private fun overviewCalendarStack(state: LauncherRenderModel): View {
        val cards = mutableListOf<TextView>()
        if (state.hasCalendarPermission) {
            if (state.calendarEvents.isEmpty()) {
                cards.add(
                    stackCard(
                        "Upcoming calendar\nNo upcoming calendar events found.",
                        CalmTheme.ACCENT,
                        true,
                        cardSideIcon(R.drawable.ic_calendar_card),
                        sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
                    ),
                )
            } else {
                cards.addAll(state.calendarEvents.map(::calendarCard))
            }
        } else {
            cards.add(
                stackCard(
                    "Calendar access\nCalendar access is needed before Calm can index upcoming events.\nManage it in Settings.",
                    CalmTheme.ACCENT,
                    true,
                    cardSideIcon(R.drawable.ic_calendar_card),
                    sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
                ),
            )
        }
        return cardStackController.cardStack(
            cards,
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
            CardStackStateKey.OVERVIEW_CALENDAR,
        )
    }

    private fun createPagePanel(backgroundImage: android.graphics.Bitmap?, hueColor: Int): LinearLayout {
        return LinearLayout(activity).apply {
            background = drawables.glass(CalmTheme.GLASS, activity.dp(22))
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(20), activity.dp(28), activity.dp(20), activity.dp(30))
            elevation = activity.dp(1).toFloat()
            translationZ = 0f
            if (backgroundImage != null) {
                background = drawables.glassWithImage(CalmTheme.GLASS, activity.dp(22), backgroundImage, hueColor)
            } else if (hueColor != 0) {
                background = drawables.glassWithHue(CalmTheme.GLASS, activity.dp(22), hueColor)
            }
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

    private fun updateChapterCarousel(pages: List<ChapterPage>, position: Int) {
        val carousel = chapterCarousel ?: return
        if (chapterCarouselRow == null || pages.isEmpty()) return
        updateChapterCarouselSelection(pages, position)
        carousel.post {
            updateCarouselCenterPadding()
            centerCarouselItem(position)
        }
    }

    private fun renderChapterCarouselItems(pages: List<ChapterPage>, selectedPosition: Int) {
        val row = chapterCarouselRow ?: return
        row.removeAllViews()
        selectedCarouselPosition = selectedPosition
        pages.forEachIndexed { index, page ->
            val selected = index == selectedPosition
            val item = chapterCarouselItem(page, index, selected)
            row.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = activity.dp(1)
                rightMargin = activity.dp(1)
            })
        }
        row.post {
            updateCarouselCenterPadding()
            centerCarouselItem(selectedPosition)
        }
    }

    private fun updateChapterCarouselSelection(pages: List<ChapterPage>, selectedPosition: Int) {
        val row = chapterCarouselRow ?: return
        if (selectedCarouselPosition == selectedPosition) return
        val previousPosition = selectedCarouselPosition
        selectedCarouselPosition = selectedPosition
        if (previousPosition in 0 until row.childCount) {
            configureChapterCarouselItem(row.getChildAt(previousPosition) as TextView, pages[previousPosition], previousPosition, false)
        }
        if (selectedPosition in 0 until row.childCount) {
            configureChapterCarouselItem(row.getChildAt(selectedPosition) as TextView, pages[selectedPosition], selectedPosition, true)
        }
    }

    private fun chapterCarouselItem(page: ChapterPage, index: Int, selected: Boolean): TextView {
        return label("", if (selected) 18 else 14, if (selected) CalmTheme.INK else CalmTheme.MUTED_INK, if (selected) Typeface.BOLD else Typeface.NORMAL).apply {
            configureChapterCarouselItem(this, page, index, selected)
        }
    }

    private fun configureChapterCarouselItem(item: TextView, page: ChapterPage, index: Int, selected: Boolean) {
        item.apply {
            text = "${page.marker}  ${page.title}"
            textSize = (if (selected) 18 else 14).toFloat()
            setTextColor(if (selected) CalmTheme.INK else CalmTheme.MUTED_INK)
            typeface = Typeface.DEFAULT
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(activity.dp(if (selected) 12 else 8), activity.dp(8), activity.dp(if (selected) 12 else 8), activity.dp(8))
            alpha = if (selected) 1f else 0.5f
            background = null
            setCompoundDrawables(null, null, null, null)
            page.chapter?.let { chapter ->
                compoundDrawablePadding = activity.dp(6)
                notificationCardDisplayCache.cachedChapterMaskedIcon(chapter)?.let { icon ->
                    setCompoundDrawables(icon.toSizedDrawable(activity.dp(if (selected) 20 else 16)), null, null, null)
                }
            }
            maxWidth = activity.dp(if (selected) 176 else 126)
            minWidth = activity.dp(if (selected) 118 else 74)
            setOnClickListener { navigateToChapterPage(index) }
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
                updateChapterCarousel(pages, index)
                resetInactiveAppSearchPages(pages[index].key)
            }
        }
    }

    private fun centerCarouselItem(position: Int, smooth: Boolean = true) {
        val row = chapterCarouselRow ?: return
        val carousel = chapterCarousel ?: return
        if (row.childCount <= position) return
        updateCarouselCenterPadding()
        val child = row.getChildAt(position)
        val viewportCenter = carousel.width / 2
        val childCenter = carousel.paddingLeft + child.left + (child.width / 2)
        val target = childCenter - viewportCenter
        if (smooth) {
            carousel.smoothScrollTo(maxOf(0, target), 0)
        } else {
            carousel.scrollTo(maxOf(0, target), 0)
        }
    }

    private fun centerCarouselPosition(position: Int, offset: Float) {
        val row = chapterCarouselRow ?: return
        val carousel = chapterCarousel ?: return
        if (row.childCount == 0 || row.childCount <= position) return
        updateCarouselCenterPadding()
        val current = row.getChildAt(position)
        val currentCenter = carousel.paddingLeft + current.left + (current.width / 2f)
        val nextCenter = if (position + 1 < row.childCount) {
            val next = row.getChildAt(position + 1)
            carousel.paddingLeft + next.left + (next.width / 2f)
        } else {
            currentCenter
        }
        val interpolatedCenter = currentCenter + ((nextCenter - currentCenter) * offset.coerceIn(0f, 1f))
        val target = (interpolatedCenter - (carousel.width / 2f)).toInt().coerceAtLeast(0)
        carousel.scrollTo(target, 0)
    }

    private fun updateCarouselCenterPadding() {
        val carousel = chapterCarousel ?: return
        if (carousel.width <= 0) return
        val sidePadding = carousel.width / 2
        if (carousel.paddingLeft == sidePadding && carousel.paddingRight == sidePadding) return
        carousel.setPadding(sidePadding, carousel.paddingTop, sidePadding, carousel.paddingBottom)
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
        return stackCard(
            "${if (today) "Today" else "Upcoming"}\n$title\n${calendarRepository.formatEventTime(event)}$location",
            if (today) CalmTheme.ACCENT else Color.rgb(122, 146, 178),
            true,
            cardSideIcon(R.drawable.ic_calendar_card),
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

    private fun notificationCard(
        item: NotificationCardItem,
        chapter: AppChapter,
        tintCards: Boolean,
    ): TextView {
        val data = notificationCardDisplayCache.getOrCreate(item, chapter, ::formatNotificationTime)
        return stackCard(
            data.text,
            chapter.hueColor,
            tintCards,
            data.sideImage,
            data.sideImageAlpha,
            data.sideImageRenderKey,
        ).apply {
            if (data.mediaBackgroundImage != null) {
                background = drawables.notificationCardWithImage(
                    cardCornerRadius(),
                    data.mediaBackgroundImage,
                    chapter.hueColor,
                    tintCards,
                )
            }
            maxLines = 4
            setOnClickListener {
                focusOverlay.show(this, contextActionFactory.notificationActions(item, chapter), data.fullText)
            }
            setOnLongClickListener {
                showNotificationHideOptions(item, chapter)
                true
            }
        }
    }

    private fun cardSideIcon(drawableRes: Int): android.graphics.Bitmap? {
        return sideIconCache.getOrPut(drawableRes) {
            activity.getDrawable(drawableRes)?.toBitmap()
        }
    }

    private fun android.graphics.Bitmap.toSizedDrawable(size: Int): android.graphics.drawable.BitmapDrawable {
        return android.graphics.drawable.BitmapDrawable(activity.resources, this).apply {
            setBounds(0, 0, size, size)
        }
    }

    private fun formatNotificationTime(postTime: Long): String {
        return DateFormat.getTimeFormat(activity).format(Date(postTime))
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

    private fun notificationSummary(chapter: AppChapter): String {
        val count = chapter.notifications.size
        return if (count == 1) "1 active note" else "$count active notes"
    }

    private fun openSettingsActivity() {
        activity.startActivity(Intent(activity, CalmSettingsActivity::class.java))
    }

    private fun openAppEntry(app: AppEntry) {
        if (notificationRepository.openApp(app)) {
            resetAllAppSearchPages()
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

    private fun clearChapter(chapter: AppChapter) {
        CalmNotificationListenerService.clearPackage(chapter.packageName, chapter.notifications.firstOrNull()?.userSerial ?: AppIdentity.LEGACY_USER_SERIAL)
        Toast.makeText(activity, "Cleared ${chapter.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun dismissNotificationItem(item: NotificationCardItem) {
        CalmNotificationListenerService.dismissNotifications(item.notifications.map { it.cancelKey })
        Toast.makeText(activity, if (item.isGroup) "Dismissed notification group" else "Dismissed notification", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun showNotificationHideOptions(item: NotificationCardItem, chapter: AppChapter) {
        val options = ArrayList<Pair<String, () -> Unit>>()
        options.add("App info" to { openAppInfo(chapter.packageName, chapter.userHandle, chapter.componentName) })
        options.add("Settings" to { openSettingsActivity() })
        options.add("Hide all notifications from app" to { excludeNotificationSource(chapter) })

        val title = item.primary.title.trim()
        val body = item.primary.bodyText().trim()
        if (title.isBlank() && body.isBlank()) {
            options.add("Hide empty notifications from app" to {
                addNotificationFilter(NotificationFilter.emptyContent(chapter.identityKey, chapter.packageName), "Hidden empty notifications")
            })
        }

        if (title.isNotBlank()) {
            options.add("Hide all notifications with title\n$title" to {
                addNotificationFilter(NotificationFilter.title(chapter.identityKey, chapter.packageName, title), "Hidden matching title")
            })
            addFlexibleNotificationFilterOptions(
                options = options,
                label = "title",
                text = title,
                containsFilter = { NotificationFilter.titleContains(chapter.identityKey, chapter.packageName, it) },
                wildcardFilter = { NotificationFilter.titleWildcard(chapter.identityKey, chapter.packageName, it) },
            )
        } else {
            options.add("Hide all notifications with no title" to {
                addNotificationFilter(NotificationFilter.title(chapter.identityKey, chapter.packageName, ""), "Hidden notifications with no title")
            })
        }

        if (body.isNotBlank()) {
            options.add("Hide all notifications with body\n${body.take(80)}" to {
                addNotificationFilter(NotificationFilter.body(chapter.identityKey, chapter.packageName, body), "Hidden matching body")
            })
            addFlexibleNotificationFilterOptions(
                options = options,
                label = "body",
                text = body,
                containsFilter = { NotificationFilter.bodyContains(chapter.identityKey, chapter.packageName, it) },
                wildcardFilter = { NotificationFilter.bodyWildcard(chapter.identityKey, chapter.packageName, it) },
            )
        } else {
            options.add("Hide all notifications with no body" to {
                addNotificationFilter(NotificationFilter.body(chapter.identityKey, chapter.packageName, ""), "Hidden notifications with no body")
            })
        }

        AlertDialog.Builder(activity)
            .setTitle("Hide notifications")
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addFlexibleNotificationFilterOptions(
        options: MutableList<Pair<String, () -> Unit>>,
        label: String,
        text: String,
        containsFilter: (String) -> NotificationFilter,
        wildcardFilter: (String) -> NotificationFilter,
    ) {
        val preview = text.take(80)
        options.add("Hide notifications containing $label\n$preview" to {
            addNotificationFilter(containsFilter(text), "Hidden notifications containing $label")
        })
        val pattern = NotificationFilterPattern.generalizeNumbers(text) ?: return
        options.add("Hide similar notifications with $label\n${pattern.take(80)}" to {
            addNotificationFilter(wildcardFilter(pattern), "Hidden similar notifications")
        })
    }

    private fun addNotificationFilter(filter: NotificationFilter, message: String) {
        settings.addNotificationFilter(filter)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun performNotificationAction(action: NotificationAction) {
        val intent = action.intent
        if (intent == null) {
            Toast.makeText(activity, "Action is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (action.requiresInput) {
            promptForNotificationActionInput(action)
            return
        }
        try {
            intent.send()
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(activity, "Action expired", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForNotificationActionInput(action: NotificationAction) {
        val input = EditText(activity).apply {
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            setTextColor(CalmTheme.INK)
            setHintTextColor(CalmTheme.MUTED_INK)
            hint = action.remoteInputs.firstOrNull()?.label ?: action.label
            setPadding(activity.dp(18), activity.dp(12), activity.dp(18), activity.dp(12))
        }
        AlertDialog.Builder(activity)
            .setTitle(action.label)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                sendRemoteInputAction(action, input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun sendRemoteInputAction(action: NotificationAction, text: String) {
        val intent = action.intent ?: return
        val fillInIntent = Intent()
        val results = android.os.Bundle()
        action.remoteInputs.forEach { remoteInput ->
            results.putCharSequence(remoteInput.resultKey, text)
        }
        RemoteInput.addResultsToIntent(action.remoteInputs.toTypedArray(), fillInIntent, results)
        try {
            intent.send(activity, 0, fillInIntent)
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(activity, "Action expired", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openNotification(notification: CalmNotificationListenerService.CalmNotification) {
        val contentIntent: PendingIntent? = notification.contentIntent
        if (contentIntent != null) {
            try {
                contentIntent.send()
                return
            } catch (_: PendingIntent.CanceledException) {
            }
        }
        val apps = notificationRepository.loadAppEntries()
        val app = apps.firstOrNull { it.notificationSourceKey == notification.sourceKey }
            ?: apps.firstOrNull { it.packageName == notification.packageName }
        if (app == null || !notificationRepository.openApp(app)) {
            Toast.makeText(activity, "This notification cannot be opened", Toast.LENGTH_SHORT).show()
        }
    }

    private fun excludeNotificationSource(chapter: AppChapter) {
        val nextPageKey = pageRemovalPlanner.selectPageAfterRemoval(currentUiState?.pages.orEmpty(), chapter.identityKey)
        settings.exclude(chapter)
        selectPage(nextPageKey)
        Toast.makeText(activity, "Excluded ${chapter.label}", Toast.LENGTH_SHORT).show()
        val pager = currentPager
        if (pager == null) {
            render()
            return
        }
        entryAnimator.animateCurrentPageRemoval(pager) { render() }
    }

    private fun restoreNotificationSource(packageName: String) {
        settings.restore(packageName)
        Toast.makeText(activity, "Restored notification source", Toast.LENGTH_SHORT).show()
        render()
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
        sideIconCache.clear()
        appSearchQueries.clear()
        appSearchPages.clear()
        refreshStateAsync()
    }

    private companion object {
        const val PAGE_PREWARM_INITIAL_DELAY_MS = 260L
        const val PAGE_PREWARM_STEP_DELAY_MS = 120L
        const val PAGE_PREWARM_MAX_PAGES = 3
        const val APP_SEARCH_REFRESH_DELAY_MS = 90L
        const val APP_STACK_DEFERRED_BATCH_SIZE = 16
        const val APP_STACK_DEFERRED_INITIAL_DELAY_MS = 48L
        const val APP_STACK_PENDING_RESTORE_BATCH_DELAY_MS = 0L
        const val APP_STACK_DEFERRED_BATCH_DELAY_MS = 32L
    }
}
