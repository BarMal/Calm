package dev.barna.calm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.Locale

class CalmLauncherRunner(private val activity: MainActivity) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = LauncherSettings(activity)
    private val notificationRepository = NotificationChapterRepository(activity, settings)
    private val calendarRepository = CalendarRepository(activity)
    private val drawables = CalmDrawables(activity)
    private val cardStackController = CardStackController(activity, mainHandler, ::performCardScrollHaptic)
    private val focusOverlay = FocusOverlayController(
        activity,
        mainHandler,
        drawables,
        ::label,
    ) { currentScreen }
    private val notificationRefresh = Runnable { mainHandler.post(::render) }

    private var selectedPackageName = CalmTheme.OVERVIEW_KEY
    private var chapterCarousel: HorizontalScrollView? = null
    private var chapterCarouselRow: LinearLayout? = null
    private var currentPager: ViewPager2? = null
    private var currentScreen: View? = null

    fun onCreate() {
        configureWindow()
        render()
    }

    fun onResume() {
        CalmNotificationListenerService.addListener(notificationRefresh)
        render()
    }

    fun onPause() {
        CalmNotificationListenerService.removeListener(notificationRefresh)
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        if (requestCode == CalmTheme.REQUEST_CALENDAR) {
            render()
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
        focusOverlay.dismiss(false)
        val notificationChapters = notificationRepository.buildNotificationChapters()
        val pages = buildPages(notificationChapters)
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
            adapter = ChapterPagerAdapter(pages, ::createPage)
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
            override fun onPageSelected(position: Int) {
                selectedPackageName = pages[position].key
                updateChapterCarousel(pages, position)
            }
        })

        root.addView(createChapterCarousel(pages, initialPage))
        root.addView(
            pager,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        updateChapterCarousel(pages, initialPage)
        activity.setContentView(screen)
    }

    private fun buildPages(notificationChapters: List<AppChapter>): List<ChapterPage> {
        val pages = ArrayList<ChapterPage>()
        pages.add(ChapterPage.overview(CalmTheme.OVERVIEW_KEY))
        var chapterNumber = 2
        notificationChapters.forEach { chapter ->
            pages.add(ChapterPage.notifications(chapter, roman(chapterNumber)))
            chapterNumber++
        }
        pages.add(ChapterPage.settings(CalmTheme.SETTINGS_KEY, roman(chapterNumber)))
        return pages
    }

    private fun resolveInitialPage(pages: List<ChapterPage>): Int {
        pages.forEachIndexed { index, page ->
            if (page.key == selectedPackageName) return index
        }
        selectedPackageName = CalmTheme.OVERVIEW_KEY
        return 0
    }

    private fun createPage(page: ChapterPage): View {
        return when {
            page.key == CalmTheme.SETTINGS_KEY -> createSettingsPage()
            page.chapter == null -> createOverviewPage(notificationRepository.buildNotificationChapters())
            else -> createChapterPage(page.chapter)
        }
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
            setPadding(activity.dp(78), activity.dp(3), activity.dp(78), activity.dp(3))
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
        return spine
    }

    private fun spineLine(): View {
        return View(activity).apply {
            setBackgroundColor(Color.argb(52, Color.red(CalmTheme.ACCENT), Color.green(CalmTheme.ACCENT), Color.blue(CalmTheme.ACCENT)))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, activity.dp(1)))
        }
    }

    private fun createOverviewPage(notificationChapters: List<AppChapter>): LinearLayout {
        return createBarePagePanel(activity.dp(20)).apply {
            addView(label("CHAPTER / OVERVIEW", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
                setPadding(0, 0, 0, activity.dp(18))
            })
            addView(sectionTitle("Next alarm"))
            addView(alarmCard())
            addView(sectionTitle("Upcoming calendar"))
            if (calendarRepository.hasCalendarPermission()) {
                val events = calendarRepository.loadUpcomingEvents()
                if (events.isEmpty()) {
                    addView(emptyNote("No upcoming calendar events found."))
                } else {
                    addView(calendarStack(events), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(224)))
                }
            } else {
                addView(emptyNote("Calendar access is needed before Calm can index upcoming events. Manage it in Settings."))
            }
            addView(sectionTitle("Active notification chapters"))
            if (notificationChapters.isEmpty()) {
                addView(emptyNote("No active notification chapters right now."))
            } else {
                notificationChapters.forEach { addView(infoCard("${it.label}\n${notificationSummary(it)}")) }
            }
            addView(sectionTitle("Launcher settings"))
            addView(fullWidthAction("Open settings", ::openSettingsChapter))
        }
    }

    private fun createSettingsPage(): LinearLayout {
        return createPagePanel(null, 0).apply {
            addView(label("CHAPTER / SETTINGS", 12, CalmTheme.ACCENT, Typeface.BOLD))
            addView(label("Launcher settings", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            })
            addView(label("Wallpaper, access, and hidden notification sources live here.", 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, activity.dp(18))
            })
            addView(sectionTitle("Appearance"))
            addView(fullWidthAction(if (settings.useTintedNotificationCards()) "Notification surface\nTinted cards" else "Notification surface\nChapter panel", ::toggleNotificationSurface))
            addView(fullWidthAction(if (settings.cardHapticsEnabled()) "Card haptics\nOn" else "Card haptics\nOff", ::toggleCardHaptics))
            addView(hapticStrengthControl())
            addView(sectionTitle("Access"))
            addView(fullWidthAction("Set wallpaper", ::openWallpaperPicker))
            addView(fullWidthAction(if (isNotificationAccessEnabled()) "Notification access enabled" else "Enable notification access", ::openNotificationAccess))
            addView(fullWidthAction(if (calendarRepository.hasCalendarPermission()) "Calendar access enabled" else "Allow calendar access", calendarRepository::requestCalendarAccess))
            addView(sectionTitle("Excluded"))
            val excluded = settings.excludedSources(notificationRepository::resolveExcludedLabel)
            if (excluded.isEmpty()) {
                addView(emptyNote("No notification sources are excluded."))
            } else {
                excluded.forEach { source ->
                    addView(fullWidthAction("Restore ${source.label}\n${source.packageName}") { restoreNotificationSource(source.packageName) })
                }
            }
        }
    }

    private fun createChapterPage(chapter: AppChapter): LinearLayout {
        val tintCards = settings.useTintedNotificationCards()
        val page = if (tintCards) {
            createBarePagePanel()
        } else {
            createPagePanel(notificationRepository.resolveChapterBackground(chapter), chapter.hueColor)
        }
        page.addView(label("CHAPTER / ${chapter.packageName}", 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })
        page.addView(label(chapter.label, 30, CalmTheme.INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(8), 0, 0)
        })
        page.addView(label(notificationSummary(chapter), 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(6), 0, activity.dp(30))
        })
        page.addView(sectionTitle("Notifications"))
        page.addView(notificationStack(chapter, tintCards), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.25f))
        return page
    }

    private fun notificationStack(chapter: AppChapter, tintCards: Boolean): View {
        return cardStackController.cardStack(
            chapter.notifications.map { notificationCard(it, chapter, tintCards) },
            activity.dp(184),
            activity.dp(58),
        )
    }

    private fun calendarStack(events: List<CalendarEvent>): View {
        return cardStackController.cardStack(events.map(::calendarCard), activity.dp(150), activity.dp(48))
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
        carousel.post { centerCarouselItem(position) }
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
                maxWidth = activity.dp(if (selected) 176 else 126)
                minWidth = activity.dp(if (selected) 118 else 74)
                setOnClickListener { currentPager?.setCurrentItem(index, true) }
            }
            row.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = activity.dp(1)
                rightMargin = activity.dp(1)
            })
        }
    }

    private fun centerCarouselItem(position: Int) {
        val row = chapterCarouselRow ?: return
        val carousel = chapterCarousel ?: return
        if (row.childCount <= position) return
        val child = row.getChildAt(position)
        val target = child.left - ((carousel.width - child.width) / 2)
        carousel.smoothScrollTo(maxOf(0, target), 0)
    }

    private fun alarmCard(): TextView {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock
        if (nextAlarm == null || nextAlarm.triggerTime <= System.currentTimeMillis()) {
            return emptyNote("No upcoming alarm is scheduled.")
        }
        val alarmTime = DateFormat.getTimeFormat(activity).format(Date(nextAlarm.triggerTime))
        return infoCard("Next alarm\n$alarmTime").apply {
            setOnClickListener { openNextAlarm(nextAlarm) }
        }
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
        return label("${if (today) "TODAY" else "UPCOMING"}\n$title\n${calendarRepository.formatEventTime(event)}$location", 15, CalmTheme.INK, Typeface.NORMAL).apply {
            setLineSpacing(activity.dp(2).toFloat(), 1.0f)
            setPadding(activity.dp(18), activity.dp(15), activity.dp(18), activity.dp(15))
            maxLines = 5
            ellipsize = TextUtils.TruncateAt.END
            background = drawables.notificationCard(activity.dp(18), if (today) CalmTheme.ACCENT else Color.rgb(122, 146, 178), true)
            elevation = activity.dp(2).toFloat()
            setOnClickListener { openCalendarEvent(event) }
            setOnLongClickListener {
                focusOverlay.show(this, calendarContextActions(event))
                true
            }
        }
    }

    private fun notificationCard(
        notification: CalmNotificationListenerService.CalmNotification,
        chapter: AppChapter,
        tintCards: Boolean,
    ): TextView {
        val title = notification.title.ifEmpty { "Untitled notification" }
        val body = notification.text.ifEmpty { notification.subText }
        val time = DateFormat.getTimeFormat(activity).format(Date(notification.postTime))
        return label("$title\n$body\n$time", 15, CalmTheme.INK, Typeface.NORMAL).apply {
            setLineSpacing(activity.dp(2).toFloat(), 1.0f)
            setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16))
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            background = drawables.notificationCard(activity.dp(18), chapter.hueColor, tintCards)
            elevation = activity.dp(2).toFloat()
            setOnClickListener { openNotification(notification) }
            setOnLongClickListener {
                focusOverlay.show(this, notificationContextActions(notification, chapter))
                true
            }
        }
    }

    private fun infoCard(text: String): TextView {
        return label(text, 15, CalmTheme.INK, Typeface.NORMAL).apply {
            setLineSpacing(activity.dp(2).toFloat(), 1.0f)
            setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            elevation = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
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
            setPadding(0, activity.dp(14), 0, activity.dp(8))
        }
    }

    private fun fullWidthAction(text: String, action: Runnable): TextView {
        return label(text, 16, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            elevation = 0f
            setOnClickListener { action.run() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(10)
            }
        }
    }

    private fun hapticStrengthControl(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(10)
            }
        }
        card.addView(label("Haptic strength", 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(hapticStrengthLabel(settings.cardHapticStrength()), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(4), 0, activity.dp(4))
        }
        card.addView(value)
        card.addView(SeekBar(activity).apply {
            max = 4
            progress = settings.cardHapticStrength() - 1
            isEnabled = settings.cardHapticsEnabled()
            alpha = if (settings.cardHapticsEnabled()) 1f else 0.42f
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val strength = progress + 1
                    value.text = hapticStrengthLabel(strength)
                    if (fromUser) {
                        settings.setCardHapticStrength(strength)
                        performCardScrollHaptic(card)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun hapticStrengthLabel(strength: Int): String = "Very light / $strength of 5"

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

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(activity.contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(activity, CalmNotificationListenerService::class.java)
        return enabledListeners != null &&
            enabledListeners.lowercase(Locale.ROOT).contains(componentName.flattenToString().lowercase(Locale.ROOT))
    }

    private fun openSettingsChapter() {
        selectedPackageName = CalmTheme.SETTINGS_KEY
        val pager = currentPager
        val settingsPage = pager?.adapter?.itemCount?.minus(1) ?: -1
        if (pager != null && settingsPage >= 0) {
            pager.setCurrentItem(settingsPage, true)
        } else {
            render()
        }
    }

    private fun openNotificationAccess() {
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openWallpaperPicker() {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))
    }

    private fun notificationContextActions(
        notification: CalmNotificationListenerService.CalmNotification,
        chapter: AppChapter,
    ): List<ContextAction> {
        return listOf(
            ContextAction("Open", Runnable { openNotification(notification) }),
            ContextAction("App", Runnable { openPackage(chapter) }),
            ContextAction("Info", Runnable { openAppInfo(chapter.packageName) }),
            ContextAction("Clear", Runnable { clearChapter(chapter) }),
            ContextAction("Hide", Runnable { excludeNotificationSource(chapter) }),
            ContextAction("Settings", Runnable { openSettingsChapter() }),
        )
    }

    private fun calendarContextActions(event: CalendarEvent): List<ContextAction> {
        return listOf(
            ContextAction("Open calendar", Runnable { openCalendarEvent(event) }),
            ContextAction(if (calendarRepository.hasCalendarPermission()) "Calendar access" else "Allow calendar", Runnable { calendarRepository.requestCalendarAccess() }),
            ContextAction("Settings", Runnable { openSettingsChapter() }),
        )
    }

    private fun openPackage(chapter: AppChapter) {
        if (!chapter.launchable) {
            Toast.makeText(activity, "This notification source has no launcher entry", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = activity.packageManager.getLaunchIntentForPackage(chapter.packageName)
        if (intent == null) {
            Toast.makeText(activity, "This app cannot be opened directly", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
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

    private fun openAppInfo(packageName: String) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun clearChapter(chapter: AppChapter) {
        CalmNotificationListenerService.clearPackage(chapter.packageName)
        Toast.makeText(activity, "Cleared ${chapter.label}", Toast.LENGTH_SHORT).show()
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
        val appIntent = activity.packageManager.getLaunchIntentForPackage(notification.packageName)
        if (appIntent == null) {
            Toast.makeText(activity, "This notification cannot be opened", Toast.LENGTH_SHORT).show()
            return
        }
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(appIntent)
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

    private fun toggleCardHaptics() {
        val nextValue = settings.toggleCardHaptics()
        if (nextValue) performCardScrollHaptic(activity.window.decorView)
        Toast.makeText(activity, if (nextValue) "Card haptics on" else "Card haptics off", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun performCardScrollHaptic(source: View) {
        if (!settings.cardHapticsEnabled()) return
        val strength = settings.cardHapticStrength()
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
