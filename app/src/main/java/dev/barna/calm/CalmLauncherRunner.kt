package dev.barna.calm

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import android.os.VibratorManager
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
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
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

class CalmLauncherRunner(private val activity: MainActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = LauncherSettings(activity)
    private val notificationRepository = NotificationChapterRepository(activity, settings)
    private val calendarRepository = CalendarRepository(activity)
    private val drawables = CalmDrawables(activity)
    private val cardSpec = CalmCardSpec()
    private val pinnedAppResolver = PinnedAppResolver()
    private val renderModelFactory = LauncherRenderModelFactory(
        LauncherPageStateFactory(pinnedAppResolver = pinnedAppResolver),
    )
    private val appCardModelFactory = AppCardModelFactory(pinnedAppResolver = pinnedAppResolver)
    private val cardStackController = CardStackController(activity, mainHandler, ::performCardScrollHaptic)
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
    private val notificationRefresh = Runnable { requestRender() }

    private var selectedPackageName = CalmTheme.OVERVIEW_KEY
    private var chapterCarousel: HorizontalScrollView? = null
    private var chapterCarouselRow: LinearLayout? = null
    private var currentPager: ViewPager2? = null
    private var currentScreen: View? = null
    private var currentUiState: LauncherRenderModel? = null
    private var activePreferences: LauncherUiPreferences = settings.uiPreferences()
    private val appSearchQueries = EnumMap<AppLibraryScope, String>(AppLibraryScope::class.java)
    private val appSearchPages = ArrayList<AppSearchPageState>()

    private data class AppSearchPageState(
        val key: String,
        val page: View,
        val header: View,
        val search: EditText,
    )

    private data class AppSearchControl(
        val root: View,
        val search: EditText,
    )

    fun onCreate() {
        configureWindow()
        notificationRepository.setOnHueResolved(Runnable { requestRender() })
        render(buildUiState(), animate = true)
    }

    fun onResume() {
        CalmNotificationListenerService.addListener(notificationRefresh)
        if (currentScreen == null || currentUiState == null) {
            render(buildUiState(), animate = true)
        } else {
            requestRender()
        }
    }

    fun onPause() {
        CalmNotificationListenerService.removeListener(notificationRefresh)
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        if (requestCode == CalmTheme.REQUEST_CALENDAR) {
            refreshStateAsync()
        }
    }

    private fun configureWindow() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        activity.window.statusBarColor = Color.TRANSPARENT
        activity.window.navigationBarColor = Color.TRANSPARENT
        activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        activity.window.decorView.systemUiVisibility = 0
    }

    private fun render() {
        render(buildUiState(), animate = true)
    }

    private fun render(state: LauncherRenderModel, animate: Boolean) {
        mainHandler.removeCallbacks(deferredRender)
        focusOverlay.dismiss(false)
        appSearchPages.clear()
        currentUiState = state
        activePreferences = state.preferences
        val pages = state.pages
        val initialPage = resolveInitialPage(pages)

        val screen = FrameLayout(activity).apply {
            currentScreen = this
            setBackgroundColor(Color.TRANSPARENT)
            addView(View(activity).apply { background = drawables.wallpaperShade() }, matchParentParams())
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(10), activity.statusBarHeightFallback() + activity.dp(28), activity.dp(10), activity.dp(34))
        }
        screen.addView(root, matchParentParams())
        root.addView(createHeader())

        val pager = ViewPager2(activity).apply {
            currentPager = this
            adapter = ChapterPagerAdapter(pages) { page -> createPage(page, state) }
            clipToPadding = false
            clipChildren = false
            offscreenPageLimit = 1
            setPageTransformer(CompositePageTransformer().apply {
                addTransformer { page, position ->
                    val distance = minOf(1f, kotlin.math.abs(position))
                    page.alpha = 1f - (0.08f * distance)
                    page.scaleX = 1f
                    page.scaleY = 1f
                    page.translationX = 0f
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
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                centerCarouselPosition(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                selectedPackageName = pages[position].key
                resetInactiveAppSearchPages(selectedPackageName)
                pager.post { entryAnimator.animateCurrentPage(pager) }
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    updateChapterCarousel(pages, pager.currentItem)
                    resetInactiveAppSearchPages(pages[pager.currentItem].key)
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
    }

    private fun resolveInitialPage(pages: List<ChapterPage>): Int {
        pages.forEachIndexed { index, page ->
            if (page.key == selectedPackageName) return index
        }
        selectedPackageName = CalmTheme.OVERVIEW_KEY
        return 0
    }

    private fun createPage(page: ChapterPage, state: LauncherRenderModel): View {
        return when {
            page.appScope != null -> createAppLibraryPage(page, state.appEntries)
            page.key == CalmTheme.PINNED_KEY -> createPinnedPage(state.pinnedApps)
            page.chapter == null -> createOverviewPage(state)
            else -> createChapterPage(page.chapter)
        }
    }

    private fun requestRender() {
        mainHandler.removeCallbacks(deferredRender)
        mainHandler.postDelayed(deferredRender, 90)
    }

    private fun refreshStateAsync() {
        val generation = stateGeneration.incrementAndGet()
        val notifications = stateExecutor.submit<List<AppChapter>> {
            notificationRepository.buildNotificationChapters()
        }
        val apps = stateExecutor.submit<List<AppEntry>> {
            notificationRepository.loadAppEntries().filterNot(settings::isAppHidden)
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
            mainHandler.post {
                if (generation == stateGeneration.get()) {
                    render(state, animate = false)
                }
            }
        }
    }

    private fun buildUiState(): LauncherRenderModel {
        val notificationChapters = notificationRepository.buildNotificationChapters()
        val appEntries = notificationRepository.loadAppEntries().filterNot(settings::isAppHidden)
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
            addView(appStack(pinnedApps), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun createAppLibraryPage(pageModel: ChapterPage, appEntries: List<AppEntry>): LinearLayout {
        val scope = pageModel.appScope ?: AppLibraryScope.ALL
        val title = pageModel.title
        val page = createBarePagePanel(activity.dp(20))
        val header = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(label("CHAPTER / ${title.uppercase(Locale.getDefault())}", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                setPadding(0, 0, 0, activity.dp(18))
            })
            addView(label(title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            })
            addView(label(appLibrarySubtitle(scope), 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, activity.dp(18))
            })
        }
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stackHost = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        page.addView(stackHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val searchControl = appSearchBox(page, header, stackHost, scope, appEntries)
        page.addView(animatedChrome(searchControl.root), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(12)
        })
        val search = searchControl.search
        appSearchPages.add(AppSearchPageState(pageModel.key, page, header, search))
        installAppSearchKeyboardAnimator(page, header, search)
        refreshAppStack(stackHost, appSearchQuery(scope), scope, appEntries)
        return page
    }

    private fun appLibrarySubtitle(scope: AppLibraryScope): String {
        return when (scope) {
            AppLibraryScope.ALL -> "Search, launch, and pin apps into the launcher spine."
            AppLibraryScope.PERSONAL -> "Personal apps stay separate from work when profile splitting is enabled."
            AppLibraryScope.WORK -> "Work-profile apps keep their own launch and notification identity."
        }
    }

    private fun appSearchBox(
        page: LinearLayout,
        header: LinearLayout,
        stackHost: FrameLayout,
        scope: AppLibraryScope,
        appEntries: List<AppEntry>,
    ): AppSearchControl {
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
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString().orEmpty()
                    setAppSearchQuery(scope, query)
                    clearButton.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
                    refreshAppStack(stackHost, query, scope, appEntries)
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

    private fun resetAppSearchPage(state: AppSearchPageState) {
        if (state.search.hasFocus()) {
            state.search.clearFocus()
            hideKeyboard(state.search)
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
        query: String,
        scope: AppLibraryScope = AppLibraryScope.ALL,
        appEntries: List<AppEntry>,
    ) {
        val filtered = appEntries.filter { app ->
            when (scope) {
                AppLibraryScope.ALL -> true
                AppLibraryScope.PERSONAL -> !app.isWorkProfile
                AppLibraryScope.WORK -> app.isWorkProfile
            }
        }.filter { app ->
            val normalizedQuery = query.trim()
            normalizedQuery.isBlank() ||
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true) ||
                app.profileLabel.contains(normalizedQuery, ignoreCase = true)
        }
        stackHost.removeAllViews()
        if (filtered.isEmpty()) {
            stackHost.addView(appSearchEmptyStack(emptyAppsMessage(scope, query)), matchParentParams())
        } else {
            stackHost.addView(appStack(filtered), matchParentParams())
        }
    }

    private fun emptyAppsMessage(scope: AppLibraryScope, query: String): String {
        if (query.isNotBlank()) return "No apps match that search."
        return when (scope) {
            AppLibraryScope.ALL -> "No apps are available."
            AppLibraryScope.PERSONAL -> "No personal apps are available."
            AppLibraryScope.WORK -> "No work apps are available."
        }
    }

    private fun appStack(apps: List<AppEntry>): View {
        return cardStackController.cardStack(
            apps.map(::appCard),
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
        )
    }

    private fun appSearchEmptyStack(message: String): View {
        val card = stackCard("Search\n$message", CalmTheme.ACCENT, true, cardSideIcon(R.drawable.ic_search_card)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 3
            isEnabled = false
        }
        return cardStackController.cardStack(
            listOf(card),
            cardHeight(),
            cardStep(),
            activePreferences.cardStackTuning,
        )
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
    ): TextView {
        return label(text, cardSpec.titleSp, CalmTheme.INK, Typeface.NORMAL).apply {
            val showImageAsBackground = sideImage != null && activePreferences.useCardIconBackgrounds
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
        val model = appCardModelFactory.create(app, currentUiState?.pinnedKeys ?: settings.pinnedPackages())
        val icon = notificationRepository.resolveAppIcon(app, cardHeight())?.bitmap
        return stackCard(model.text, model.hueColor, true, icon).apply {
            maxLines = 4
            setOnClickListener { openAppEntry(app) }
            setOnLongClickListener {
                focusOverlay.show(this, appContextActions(model.app), model.app.label)
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
        return notificationRepository.loadAppEntries()
            .filterNot(settings::isAppHidden)
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
            notificationRepository.resolveMaskedAppIcon(chapter, activity.dp(42))?.let(::setImageDrawable)
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
        )
    }

    private fun overviewCalendarStack(state: LauncherRenderModel): View {
        val cards = mutableListOf<TextView>()
        if (state.hasCalendarPermission) {
            if (state.calendarEvents.isEmpty()) {
                cards.add(stackCard("Upcoming calendar\nNo upcoming calendar events found.", CalmTheme.ACCENT, true, cardSideIcon(R.drawable.ic_calendar_card)))
            } else {
                cards.addAll(state.calendarEvents.map(::calendarCard))
            }
        } else {
            cards.add(stackCard("Calendar access\nCalendar access is needed before Calm can index upcoming events.\nManage it in Settings.", CalmTheme.ACCENT, true, cardSideIcon(R.drawable.ic_calendar_card)))
        }
        return cardStackController.cardStack(cards, cardHeight(), cardStep(), activePreferences.cardStackTuning)
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

    private fun createBarePagePanel(horizontalPadding: Int = activity.dp(4)): LinearLayout {
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
        renderChapterCarouselItems(pages, position)
        carousel.post {
            updateCarouselCenterPadding()
            centerCarouselItem(position)
        }
    }

    private fun renderChapterCarouselItems(pages: List<ChapterPage>, selectedPosition: Int) {
        val row = chapterCarouselRow ?: return
        row.removeAllViews()
        pages.forEachIndexed { index, page ->
            val selected = index == selectedPosition
            val item = label("${page.marker}  ${page.title}", if (selected) 18 else 14, if (selected) CalmTheme.INK else CalmTheme.MUTED_INK, if (selected) Typeface.BOLD else Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                setPadding(activity.dp(if (selected) 12 else 8), activity.dp(8), activity.dp(if (selected) 12 else 8), activity.dp(8))
                alpha = if (selected) 1f else 0.5f
                background = null
                page.chapter?.let { chapter ->
                    compoundDrawablePadding = activity.dp(6)
                    notificationRepository.resolveMaskedAppIcon(chapter, activity.dp(if (selected) 20 else 16))?.let { icon ->
                        setCompoundDrawables(icon, null, null, null)
                    }
                }
                maxWidth = activity.dp(if (selected) 176 else 126)
                minWidth = activity.dp(if (selected) 118 else 74)
                setOnClickListener { currentPager?.setCurrentItem(index, true) }
            }
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
        return stackCard("${if (today) "Today" else "Upcoming"}\n$title\n${calendarRepository.formatEventTime(event)}$location", if (today) CalmTheme.ACCENT else Color.rgb(122, 146, 178), true, cardSideIcon(R.drawable.ic_calendar_card)).apply {
            setOnClickListener { openCalendarEvent(event) }
            setOnLongClickListener {
                focusOverlay.show(this, calendarContextActions(event))
                true
            }
        }
    }

    private fun notificationCard(
        item: NotificationCardItem,
        chapter: AppChapter,
        tintCards: Boolean,
    ): TextView {
        val title = item.title()
        val body = item.previewText()
        val time = DateFormat.getTimeFormat(activity).format(Date(item.primary.postTime))
        val artwork = item.notifications.firstNotNullOfOrNull { it.backgroundImage }
        val isMedia = isMediaCard(item)
        val sideImage = when {
            isMedia -> null
            artwork != null -> artwork.toRectangularCardArtwork()
            else -> notificationRepository.resolveAppIcon(chapter, cardHeight())?.bitmap
        }
        val sideImageAlpha = if (artwork != null && !isMedia) 156 else 64
        return stackCard("$title\n$body\n$time", chapter.hueColor, tintCards, sideImage, sideImageAlpha).apply {
            if (artwork != null && isMedia) {
                background = drawables.notificationCardWithImage(
                    cardCornerRadius(),
                    artwork,
                    chapter.hueColor,
                    tintCards,
                )
            }
            maxLines = 4
            setOnClickListener {
                focusOverlay.show(this, notificationContextActions(item, chapter), item.fullText())
            }
            setOnLongClickListener {
                showNotificationHideOptions(item, chapter)
                true
            }
        }
    }

    private fun cardSideIcon(drawableRes: Int): android.graphics.Bitmap? {
        return activity.getDrawable(drawableRes)?.toBitmap()
    }

    private fun isMediaCard(item: NotificationCardItem): Boolean {
        return MediaNotificationControls.from(item.notifications).hasAnyAction
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

    private fun notificationContextActions(
        item: NotificationCardItem,
        chapter: AppChapter,
    ): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        actions.addAll(listOf(
            ContextAction("Open", Runnable { openNotification(item.primary) }),
            ContextAction("Open app", Runnable { openPackage(chapter) }),
            ContextAction("Dismiss", Runnable { dismissNotificationItem(item) }, ContextActionCloseBehavior.REMOVE_CARD),
            ContextAction("Clear", Runnable { clearChapter(chapter) }, ContextActionCloseBehavior.REMOVE_CARD),
        ))
        item.allActions().forEach { action ->
            actions.add(ContextAction(action.label, Runnable { performNotificationAction(action) }))
        }
        return actions
    }

    private fun calendarContextActions(event: CalendarEvent): List<ContextAction> {
        return listOf(
            ContextAction("Open calendar", Runnable { openCalendarEvent(event) }),
            ContextAction(if (calendarRepository.hasCalendarPermission()) "Calendar access" else "Allow calendar", Runnable { calendarRepository.requestCalendarAccess() }),
            ContextAction("Settings", Runnable { openSettingsActivity() }),
        )
    }

    private fun appContextActions(app: AppEntry): List<ContextAction> {
        val pinned = isPinned(app)
        return listOf(
            ContextAction("Open", Runnable { openAppEntry(app) }),
            ContextAction(if (pinned) "Unpin" else "Pin", Runnable {
                if (pinned) {
                    unpinApp(app)
                } else {
                    pinApp(app)
                }
            }),
            ContextAction("Info", Runnable { openAppInfo(app.packageName, app.userHandle, app.componentName) }),
        )
    }

    private fun openAppEntry(app: AppEntry) {
        if (!notificationRepository.openApp(app)) {
            Toast.makeText(activity, "This app cannot be opened directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinApp(app: AppEntry) {
        settings.pinPackage(app.identityKey)
        selectedPackageName = CalmTheme.PINNED_KEY
        Toast.makeText(activity, "Pinned ${app.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun unpinApp(app: AppEntry) {
        settings.unpinPackage(app.identityKey)
        settings.unpinPackage(app.packageName)
        if (loadPinnedApps().isEmpty()) {
            selectedPackageName = CalmTheme.APP_LIBRARY_KEY
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
        } else {
            options.add("Hide all notifications with no title" to {
                addNotificationFilter(NotificationFilter.title(chapter.identityKey, chapter.packageName, ""), "Hidden notifications with no title")
            })
        }

        if (body.isNotBlank()) {
            options.add("Hide all notifications with body\n${body.take(80)}" to {
                addNotificationFilter(NotificationFilter.body(chapter.identityKey, chapter.packageName, body), "Hidden matching body")
            })
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
        val app = notificationRepository.loadAppEntries()
            .firstOrNull { it.notificationSourceKey == notification.sourceKey }
            ?: notificationRepository.loadAppEntries().firstOrNull { it.packageName == notification.packageName }
        if (app == null || !notificationRepository.openApp(app)) {
            Toast.makeText(activity, "This notification cannot be opened", Toast.LENGTH_SHORT).show()
        }
    }

    private fun excludeNotificationSource(chapter: AppChapter) {
        settings.exclude(chapter)
        selectedPackageName = CalmTheme.OVERVIEW_KEY
        Toast.makeText(activity, "Excluded ${chapter.label}", Toast.LENGTH_SHORT).show()
        render()
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
            selectedPackageName = if (nextValue) CalmTheme.PERSONAL_APP_LIBRARY_KEY else CalmTheme.APP_LIBRARY_KEY
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
