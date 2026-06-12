package dev.barna.calm

import android.appwidget.AppWidgetHost
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt
import java.util.Locale
import java.util.concurrent.Executors

class CalmSettingsActivity : ComponentActivity() {
    private lateinit var settings: LauncherSettings
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var appRepository: NotificationChapterRepository
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deferredRender = Runnable { render() }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private var settingsScrollY = 0
    private var cachedAppEntries: List<AppEntry>? = null
    private var destroyed = false
    private var currentSettingsPage = SettingsPage.ROOT
    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = LauncherSettings(this)
        calendarRepository = CalendarRepository(this) {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        appRepository = NotificationChapterRepository(this, settings)
        currentSettingsPage = initialSettingsPage(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentSettingsPage != SettingsPage.ROOT) {
                    openSettingsPage(SettingsPage.ROOT)
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        configureWindow()
        render()
        appLoadExecutor.execute {
            val entries = appRepository.loadAppEntries()
            mainHandler.post {
                if (destroyed) return@post
                cachedAppEntries = entries
                requestRender()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacks(deferredRender)
        appLoadExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun configureWindow() {
        CalmSystemBars.applySettingsWindow(this)
    }

    private fun render() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), statusBarHeightFallback() + dp(18), dp(18), dp(20))
            setBackgroundColor(GoogleInteractionStyle.background(this@CalmSettingsActivity))
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                settingsScrollY = scrollY
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }
        scrollView.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        screen.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(screen)

        renderSettingsContent(content)
        scrollView.post { scrollView.scrollTo(0, settingsScrollY) }
    }

    private fun renderSettingsContent(content: LinearLayout) {
        content.addView(settingsTitle())
        when (currentSettingsPage) {
            SettingsPage.ROOT -> renderSettingsHome(content)
            SettingsPage.APPEARANCE -> renderAppearanceSettings(content)
            SettingsPage.PAGES -> renderPageSettings(content)
            SettingsPage.APPS -> renderAppSettings(content)
            SettingsPage.DOCK -> renderDockSettings(content)
            SettingsPage.CARD_STACK -> renderCardStackSettings(content)
            SettingsPage.SECTION_CARDS -> renderSectionCardSettings(content)
            SettingsPage.ACCESS -> renderAccessSettings(content)
        }
    }

    private fun settingsTitle(): TextView {
        val title = if (currentSettingsPage == SettingsPage.ROOT) "Settings" else currentSettingsPage.title
        return label(title, 32, GoogleInteractionStyle.onSurface(this), Typeface.NORMAL).apply {
            setPadding(0, dp(2), 0, dp(18))
        }
    }

    private fun renderSettingsHome(content: LinearLayout) {
        content.addView(section("Launcher"))
        content.addView(settingsGroupRow(
            SettingsPage.PAGES,
            "Pages",
            "Order pages, choose the home page, and manage page-level features.",
        ))
        content.addView(settingsGroupRow(
            SettingsPage.APPS,
            "Apps and icons",
            "Pin apps, manage hidden apps, choose dock apps, and use app shortcuts.",
        ))
        content.addView(settingsGroupRow(
            SettingsPage.DOCK,
            "Dock",
            dockAppsSummary(),
        ))

        content.addView(section("Look and feel"))
        content.addView(settingsGroupRow(
            SettingsPage.APPEARANCE,
            "Appearance",
            "Card colours, effects, icons, haptics, and focus blur.",
        ))
        content.addView(settingsGroupRow(
            SettingsPage.CARD_STACK,
            "Card stack",
            "Path, spacing, fade, snap, tilt, and visible-card controls.",
        ))
        content.addView(settingsGroupRow(
            SettingsPage.SECTION_CARDS,
            "Section cards",
            "Build the title and folder cards used inside dynamic stacks.",
        ))

        content.addView(section("System"))
        content.addView(settingsGroupRow(
            SettingsPage.ACCESS,
            "Access",
            "Wallpaper, notification access, and calendar access.",
        ))
    }

    private fun renderAppearanceSettings(content: LinearLayout) {
        content.addView(section("Cards"))
        content.addView(cardAppearancePreview())
        content.addView(switchRow(
            title = "Tint notification cards",
            summary = "Move app colour from chapter panels onto cards.",
            checked = settings.useTintedNotificationCards(),
        ) { settings.toggleNotificationSurface(); requestRender() })
        content.addView(switchRow(
            title = "Icons as card backgrounds",
            summary = "Use large faded icons inside cards instead of small right-side icons.",
            checked = settings.useCardIconBackgrounds(),
        ) { settings.toggleCardIconBackgrounds(); requestRender() })
        content.addView(switchRow(
            title = "Expanded cards on long-press",
            summary = "Long-press a card to expand it with its actions. Tap still opens the app.",
            checked = settings.expandedCardsEnabled(),
        ) { settings.toggleExpandedCards(); requestRender() })
        val appearance = settings.cardAppearance()
        content.addView(actionRow("Card effect", cardEffectLabel(appearance.effect)) { showCardEffectDialog() })
        content.addView(sliderRow(
            title = "Effect strength",
            progress = appearance.effectStrength,
            max = 100,
            valueText = { if (it == 0) "Flat cards" else "${it}% effect" },
        ) { settings.setCardEffectStrength(it) })
        content.addView(sliderRow(
            title = "Card tint",
            progress = appearance.tintStrength,
            max = 100,
            valueText = { if (it == 0) "No tint" else "${it}% tint" },
        ) { settings.setCardTintStrength(it) })
        content.addView(sliderRow(
            title = "Card rounding",
            progress = settings.cardCornerRadiusDp(),
            max = 36,
            valueText = { "${it}dp corners" },
        ) { settings.setCardCornerRadiusDp(it) })
        content.addView(sliderRow(
            title = "Icon softness",
            progress = settings.cardIconBlur(),
            max = 100,
            valueText = { if (it == 0) "Sharp icons" else "${it}% softness" },
        ) { settings.setCardIconBlur(it) })
        content.addView(sliderRow(
            title = "Focus blur",
            progress = settings.focusBlurRadius(),
            max = 24,
            valueText = { if (it == 0) "No background blur" else "${it}px background blur" },
        ) { settings.setFocusBlurRadius(it) })
        content.addView(switchRow(
            title = "Card haptics",
            summary = "Very light feedback when card stacks settle.",
            checked = settings.cardHapticsEnabled(),
        ) { settings.toggleCardHaptics(); requestRender() })
        content.addView(sliderRow(
            title = "Haptic strength",
            progress = settings.cardHapticStrength() - 1,
            max = 4,
            valueText = { "Very light / ${it + 1} of 5" },
        ) { settings.setCardHapticStrength(it + 1) })
    }

    private fun cardAppearancePreview(): View {
        val colors = GoogleInteractionStyle.palette(this)
        val appearance = settings.cardAppearance()
        val tuning = settings.cardStackTuning()
        val drawables = CalmDrawables(this)
        return FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedDrawable(colors.surfaceContainer, dp(28)).apply {
                setStroke(dp(1), colors.outlineVariant)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(306)).apply {
                bottomMargin = dp(10)
            }
            addAppearancePreviewCard(
                card = appearancePreviewCard(
                    title = "Background card",
                    detail = "Dimmed by stack fade",
                    hue = Color.rgb(112, 156, 188),
                    drawables = drawables,
                    appearance = appearance,
                    alphaMultiplier = tuning.nonTopCardOpacityFactor,
                ),
                topMargin = 142,
                scale = 0.9f,
                rotationValue = -2f,
            )
            addAppearancePreviewCard(
                card = appearancePreviewCard(
                    title = "Top card",
                    detail = cardAppearanceSummary(appearance),
                    hue = CalmTheme.ACCENT,
                    drawables = drawables,
                    appearance = appearance,
                    alphaMultiplier = 1f,
                ),
                topMargin = 48,
                scale = 1f,
                rotationValue = 0f,
            )
        }
    }

    private fun FrameLayout.addAppearancePreviewCard(
        card: View,
        topMargin: Int,
        scale: Float,
        rotationValue: Float,
    ) {
        addView(
            card.apply {
                scaleX = scale
                scaleY = scale
                rotation = rotationValue
                translationZ = dp((scale * 10).roundToInt()).toFloat()
                pivotY = 0f
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                this.topMargin = dp(topMargin)
            },
        )
    }

    private fun appearancePreviewCard(
        title: String,
        detail: String,
        hue: Int,
        drawables: CalmDrawables,
        appearance: CardAppearance,
        alphaMultiplier: Float,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            background = drawables.notificationCard(
                radius = dp(settings.cardCornerRadiusDp()),
                hueColor = hue,
                tintCards = settings.useTintedNotificationCards(),
                appearance = appearance,
            )
            alpha = (0.46f + 0.54f * alphaMultiplier).coerceIn(0.18f, 1f)
            elevation = dp(2).toFloat()
            addView(label(title, 20, CalmTheme.INK, Typeface.BOLD))
            addView(label(detail, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun cardAppearanceSummary(appearance: CardAppearance): String {
        val effect = cardEffectLabel(appearance.effect).lowercase(Locale.getDefault())
        return "$effect · ${appearance.effectStrength}% effect · ${appearance.tintStrength}% tint"
    }

    private fun renderPageSettings(content: LinearLayout) {
        content.addView(section("Pages"))
        content.addView(actionRow("Page layout", "Reorder page types and set the home page.") {
            showPageLayoutDialog()
        })
        content.addView(actionRow("Classic pages", classicPagesManagementSummary()) {
            showClassicPagesDialog()
        })
        content.addView(actionRow("RSS feeds", rssFeedsSummary()) {
            showRssFeedsDialog()
        })
        val classicGrid = settings.classicGridConfig()
        content.addView(sliderRow(
            title = "Classic grid columns",
            progress = classicGrid.columns - ClassicGridConfig.MIN_COLUMNS,
            max = ClassicGridConfig.MAX_COLUMNS - ClassicGridConfig.MIN_COLUMNS,
            valueText = { "${it + ClassicGridConfig.MIN_COLUMNS} columns" },
        ) {
            settings.setClassicGridColumns(it + ClassicGridConfig.MIN_COLUMNS)
            requestRender()
        })
        content.addView(sliderRow(
            title = "Classic grid rows",
            progress = classicGrid.rows - ClassicGridConfig.MIN_ROWS,
            max = ClassicGridConfig.MAX_ROWS - ClassicGridConfig.MIN_ROWS,
            valueText = { "${it + ClassicGridConfig.MIN_ROWS} rows" },
        ) {
            settings.setClassicGridRows(it + ClassicGridConfig.MIN_ROWS)
            requestRender()
        })
        content.addView(actionRow(
            "Page order",
            "Sorted by ${pageSortLabel(settings.pageSortOrder())}.",
        ) {
            showPageSortDialog()
        })
    }

    private fun renderAppSettings(content: LinearLayout) {
        content.addView(section("Apps"))
        content.addView(switchRow(
            title = "Split personal and work apps",
            summary = "Show Work apps and Personal apps as separate chapters.",
            checked = settings.splitAppsByProfile(),
        ) { settings.toggleSplitAppsByProfile(); requestRender() })
        content.addView(switchRow(
            title = "Work notifications on the left",
            summary = "Place work notification chapters before the app chapters.",
            checked = settings.placeWorkNotificationChaptersBeforeApps(),
        ) { settings.toggleWorkNotificationChaptersBeforeApps(); requestRender() })
        content.addView(actionRow("Pinned apps", "Long-press an app card and choose Pin to add it to the Pinned page.") {
            Toast.makeText(this, "Long-press an app card, then choose Pin", Toast.LENGTH_SHORT).show()
        })
        content.addView(actionRow("App shortcuts", "Long-press an app card to launch its available shortcuts.") {
            Toast.makeText(this, "Long-press an app card to see shortcuts", Toast.LENGTH_SHORT).show()
        })
        content.addView(actionRow(getString(R.string.settings_dock_apps_title), dockAppsSummary()) { showDockAppsDialog() })
        content.addView(actionRow(
            getString(R.string.dialog_hidden_apps_title),
            hiddenAppsSummary(),
        ) {
            showHiddenAppsDialog()
        })
    }

    private fun renderDockSettings(content: LinearLayout) {
        content.addView(section(getString(R.string.settings_section_dock)))
        val dock = settings.dockConfig()
        content.addView(switchRow(
            title = getString(R.string.settings_show_dock_title),
            summary = getString(R.string.settings_show_dock_summary),
            checked = dock.enabled,
        ) { settings.setDockEnabled(!settings.dockConfig().enabled); requestRender() })
        content.addView(actionRow(getString(R.string.settings_dock_apps_title), dockAppsSummary()) { showDockAppsDialog() })
        content.addView(choiceRow(
            title = getString(R.string.settings_dock_style_title),
            options = DockStyle.entries.map { style -> dockStyleLabel(style) to style },
            selected = dock.style,
        ) { settings.setDockStyle(it); requestRender() })
        content.addView(choiceRow(
            title = getString(R.string.settings_dock_tap_action_title),
            options = DockInteractionAction.entries.map { action -> dockInteractionLabel(action) to action },
            selected = dock.tapAction,
        ) { settings.setDockTapAction(it); requestRender() })
        content.addView(choiceRow(
            title = getString(R.string.settings_dock_long_press_action_title),
            options = DockInteractionAction.entries.map { action -> dockInteractionLabel(action) to action },
            selected = dock.longPressAction,
        ) { settings.setDockLongPressAction(it); requestRender() })
        content.addView(sliderRow(
            title = getString(R.string.settings_dock_app_count_title),
            progress = dock.itemCount - DockConfig.MIN_ITEM_COUNT,
            max = DockConfig.MAX_ITEM_COUNT - DockConfig.MIN_ITEM_COUNT,
            valueText = {
                val count = it + DockConfig.MIN_ITEM_COUNT
                resources.getQuantityString(R.plurals.settings_dock_app_count_value, count, count)
            },
        ) { settings.setDockItemCount(it + DockConfig.MIN_ITEM_COUNT); requestRender() })
        content.addView(sliderRow(
            title = getString(R.string.settings_dock_item_size_title),
            progress = dock.itemSpan - DockConfig.MIN_ITEM_SPAN,
            max = DockConfig.MAX_ITEM_SPAN - DockConfig.MIN_ITEM_SPAN,
            valueText = { dockItemSizeLabel(it + DockConfig.MIN_ITEM_SPAN) },
        ) { settings.setDockItemSpan(it + DockConfig.MIN_ITEM_SPAN); requestRender() })
        content.addView(sliderRow(
            title = getString(R.string.settings_dock_height_title),
            progress = dock.verticalPaddingDp,
            max = DockConfig.MAX_VERTICAL_PADDING_DP,
            valueText = { getString(R.string.settings_dock_padding_value, it) },
        ) { settings.setDockVerticalPadding(it); requestRender() })
        content.addView(sliderRow(
            title = getString(R.string.settings_dock_side_margin_title),
            progress = dock.horizontalPaddingDp,
            max = DockConfig.MAX_HORIZONTAL_PADDING_DP,
            valueText = { getString(R.string.settings_dock_padding_value, it) },
        ) { settings.setDockHorizontalPadding(it); requestRender() })
    }

    private fun renderCardStackSettings(content: LinearLayout) {
        content.addView(section("Card stack"))
        content.addView(actionRow("Apply Timescape preset", "Restore the curved stacked-card defaults.") {
            settings.applyTimescapeStackPreset()
            Toast.makeText(this, "Timescape preset applied", Toast.LENGTH_SHORT).show()
            requestRender()
        })
        val tuning = settings.cardStackTuning()
        content.addView(signedSliderRow(
            title = "Left / right path",
            value = tuning.horizontalCurve,
            valueText = {
                when {
                    it < 0 -> "Curves from left ${kotlin.math.abs(it)}%"
                    it > 0 -> "Curves from right ${it}%"
                    else -> "Flat centre path"
                }
            },
        ) { settings.setCardStackHorizontalCurve(it) })
        content.addView(sliderRow("Arc width", tuning.arcWidth, 100, { "${it}% broadness" }) { settings.setCardStackArcWidth(it) })
        content.addView(sliderRow("Magnet strength", tuning.magnetStrength, 100, { if (it == 0) "No snap" else "${it}% snap" }) { settings.setMagnetStrength(it) })
        content.addView(sliderRow("Background card fade", tuning.nonTopCardOpacity, 100, { if (it == 100) "Default fade" else "${it}% visible" }) { settings.setNonTopCardOpacity(it) })
        content.addView(stepperRow("Cards above focus", tuning.aboveFocusCards, 0, 4) { settings.setAboveFocusCardCount(it); requestRender() })
        content.addView(sliderRow("Card fan rotation", tuning.rotation, 100, { if (it == 0) "Cards stay flat" else "${it}% tilt" }) { settings.setCardStackRotation(it) })
        content.addView(switchRow(
            title = "Advanced stack controls",
            summary = "Show depth, spacing, and visible-count controls.",
            checked = settings.showAdvancedStackControls(),
        ) { settings.toggleAdvancedStackControls(); requestRender() })
        if (settings.showAdvancedStackControls()) {
            val advancedTuning = settings.cardStackTuning()
            content.addView(sliderRow("Visual curve", advancedTuning.curve, 100, { "${it}% depth" }) { settings.setCardStackCurve(it) })
            content.addView(sliderRow("Vertical spacing", advancedTuning.verticalSpacing, 100, { "${it}% spread" }) { settings.setCardStackSpacing(it) })
            content.addView(stepperRow("Visible cards", advancedTuning.visibleCards, 1, 5) { settings.setVisibleCardCount(it); requestRender() })
        }
    }

    private fun renderAccessSettings(content: LinearLayout) {
        content.addView(section("Access"))
        content.addView(actionRow("Set wallpaper", "Open Android's wallpaper picker.") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))
        })
        content.addView(actionRow(
            if (isNotificationAccessEnabled()) "Notification access enabled" else "Enable notification access",
            "Manage Calm's notification listener permission.",
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        content.addView(actionRow(
            if (calendarRepository.hasCalendarPermission()) "Calendar access enabled" else "Allow calendar access",
            "Let the overview show upcoming calendar events.",
        ) {
            calendarRepository.requestCalendarAccess()
        })
    }

    private fun settingsGroupRow(page: SettingsPage, title: String, summary: String): View {
        return actionRow(title, summary) { openSettingsPage(page) }
    }

    private fun openSettingsPage(page: SettingsPage) {
        currentSettingsPage = page
        settingsScrollY = 0
        render()
    }

    private fun initialSettingsPage(intent: Intent): SettingsPage {
        return when (intent.getStringExtra(EXTRA_PAGE)) {
            PAGE_SECTION_CARDS -> SettingsPage.SECTION_CARDS
            else -> SettingsPage.ROOT
        }
    }

    private fun switchRow(
        title: String,
        summary: String,
        checked: Boolean,
        onToggle: () -> Unit,
    ): View {
        return rowBase().apply {
            val text = LinearLayout(this@CalmSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 16, CalmTheme.INK, Typeface.BOLD))
                addView(label(summary, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    setPadding(0, dp(4), dp(12), 0)
                })
            }
            addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(themedSwitch(checked, onToggle))
            setOnClickListener { onToggle() }
        }
    }

    // Uses the framework Switch rather than androidx SwitchCompat: this activity is a bare
    // ComponentActivity under the framework Theme.Material theme (not Theme.AppCompat), and
    // SwitchCompat is the only appcompat widget in the app — instantiating it outside an AppCompat
    // context is unsupported and is the one path unique to this screen. The framework widget is
    // tinted to read correctly on the dark settings surface.
    private fun themedSwitch(checked: Boolean, onToggle: () -> Unit): Switch {
        return Switch(this).apply {
            isChecked = checked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(
                states,
                intArrayOf(GoogleInteractionStyle.primary(this@CalmSettingsActivity), GoogleInteractionStyle.surface(this@CalmSettingsActivity)),
            )
            trackTintList = ColorStateList(
                states,
                intArrayOf(
                    withAlpha(GoogleInteractionStyle.primary(this@CalmSettingsActivity), 96),
                    GoogleInteractionStyle.surfaceContainerHigh(this@CalmSettingsActivity),
                ),
            )
            setOnClickListener { onToggle() }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun actionRow(title: String, summary: String, action: () -> Unit): View {
        return rowBase().apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16, CalmTheme.INK, Typeface.BOLD))
            addView(label(summary, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(4), 0, 0)
            })
            setOnClickListener { action() }
        }
    }

    private fun <T> choiceRow(
        title: String,
        options: List<Pair<String, T>>,
        selected: T,
        onSelected: (T) -> Unit,
    ): View {
        return rowBase().apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16, CalmTheme.INK, Typeface.BOLD))
            addView(HorizontalScrollView(this@CalmSettingsActivity).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                addView(LinearLayout(this@CalmSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, 0)
                    options.forEach { option ->
                        addView(optionChip(option.first, option.second == selected) {
                            onSelected(option.second)
                        })
                    }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun optionChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
        return label(
            text,
            14,
            if (selected) GoogleInteractionStyle.palette(this).onPrimaryContainer else CalmTheme.INK,
            Typeface.BOLD,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GoogleInteractionStyle.chipBackground(this@CalmSettingsActivity, selected = selected)
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun pageSortLabel(order: PageSortOrder): String {
        return when (order) {
            PageSortOrder.APP_NAME_ASC -> "app name (A–Z)"
            PageSortOrder.APP_NAME_DESC -> "app name (Z–A)"
            PageSortOrder.NOTIFICATION_AGE_NEWEST -> "newest notification first"
            PageSortOrder.NOTIFICATION_AGE_OLDEST -> "oldest notification first"
        }
    }

    private fun cardEffectLabel(effect: CardEffect): String {
        return when (effect) {
            CardEffect.NONE -> "Solid"
            CardEffect.FROSTED -> "Frosted"
            CardEffect.GLASS -> "Glass"
        }
    }

    private fun showCardEffectDialog() {
        val options = CardEffect.entries.toTypedArray()
        val current = options.indexOf(settings.cardAppearance().effect).coerceAtLeast(0)
        val labels = options.map(::cardEffectLabel).toTypedArray()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Card effect")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings.setCardEffect(options[which])
                requestRender()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showPageSortDialog() {
        val options = PageSortOrder.entries.toTypedArray()
        val current = options.indexOf(settings.pageSortOrder()).coerceAtLeast(0)
        val labels = options.map { order ->
            pageSortLabel(order).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }.toTypedArray()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Page order")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings.setPageSortOrder(options[which])
                requestRender()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showPageLayoutDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(16))
        }
        lateinit var rebuild: () -> Unit
        rebuild = {
            list.removeAllViews()
            val layout = settings.pageLayout()
            list.addView(pageLayoutCarousel(layout, rebuild))
            list.addView(pageLayoutActions(rebuild))
        }
        rebuild()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Page layout")
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton(R.string.action_done) { _, _ -> requestRender() }
            .show()
    }

    private fun pageLayoutCarousel(layout: LauncherPageLayout, rebuild: () -> Unit): View {
        val carousel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(14))
        }
        PageLayoutPreviewModel.segments(layout).forEachIndexed { index, segment ->
            carousel.addView(pageLayoutPreviewTile(segment, layout, index, rebuild))
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(carousel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun pageLayoutPreviewTile(
        segment: PageLayoutPreviewSegment,
        layout: LauncherPageLayout,
        index: Int,
        rebuild: () -> Unit,
    ): View {
        val status = when {
            segment.home && canUseAsDefaultHome(segment.slot) -> "Home"
            else -> "Page type"
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = pageTileBackground(segment.home)
            isClickable = true
            addView(pageTilePreview(segment.slot, segment.home))
            addView(label(segment.label, 15, CalmTheme.INK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            })
            addView(label(status, 12, if (segment.home) CalmTheme.ACCENT else CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(3), 0, 0)
            })
            setOnClickListener {
                if (canUseAsDefaultHome(segment.slot)) settings.setDefaultHomeSlot(segment.slot)
                rebuild()
            }
            layoutParams = LinearLayout.LayoutParams(dp(142), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(10)
            }
        }
        installPageTileGestures(card, segment.slot, layout, index, rebuild)
        return card
    }

    private fun pageTileBackground(home: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(if (home) GoogleInteractionStyle.primaryContainer(this@CalmSettingsActivity) else GoogleInteractionStyle.surface(this@CalmSettingsActivity))
            setStroke(dp(if (home) 2 else 1), if (home) GoogleInteractionStyle.primary(this@CalmSettingsActivity) else GoogleInteractionStyle.outlineVariant(this@CalmSettingsActivity))
        }
    }

    private fun pageTilePreview(slot: PageSlot, home: Boolean): View {
        val colors = GoogleInteractionStyle.palette(this)
        val accent = if (home) colors.primary else colors.onSurfaceVariant
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(colors.surfaceContainer)
            }
            addView(View(this@CalmSettingsActivity).apply {
                background = roundedBlock(accent, 999)
            }, LinearLayout.LayoutParams(dp(42), dp(6)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            })
            when (slot) {
                PageSlot.CLASSIC_PAGES -> addClassicPreviewRows(this, accent)
                PageSlot.APPS -> addAppPreviewRows(this, accent)
                PageSlot.NOTIFICATIONS -> addNotificationPreviewRows(this, accent)
                PageSlot.PINNED -> addPinnedPreviewRows(this, accent)
                PageSlot.CONTACTS -> addContactPreviewRows(this, accent)
                PageSlot.AGENDA -> addAgendaPreviewRows(this, accent)
                PageSlot.ALARMS -> addAlarmsPreviewRows(this, accent)
                PageSlot.RSS -> addRssPreviewRows(this, accent)
                PageSlot.WORK_OVERVIEW -> addOverviewPreviewRows(this, accent, dense = true)
                PageSlot.OVERVIEW -> addOverviewPreviewRows(this, accent, dense = false)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132))
        }
    }

    private fun addClassicPreviewRows(parent: LinearLayout, color: Int) {
        repeat(3) {
            parent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                repeat(4) {
                    addView(View(this@CalmSettingsActivity).apply {
                        background = roundedBlock(color, 10)
                    }, LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                        leftMargin = dp(3)
                        rightMargin = dp(3)
                    })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun addAppPreviewRows(parent: LinearLayout, color: Int) {
        repeat(4) {
            parent.addView(View(this).apply {
                background = roundedBlock(color, 10)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(13)).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
                bottomMargin = dp(7)
            })
        }
    }

    private fun addNotificationPreviewRows(parent: LinearLayout, color: Int) {
        listOf(72, 56, 86).forEach { width ->
            parent.addView(View(this).apply {
                background = roundedBlock(color, 12)
            }, LinearLayout.LayoutParams(dp(width), dp(22)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            })
        }
    }

    private fun addPinnedPreviewRows(parent: LinearLayout, color: Int) {
        repeat(2) {
            parent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                repeat(2) {
                    addView(View(this@CalmSettingsActivity).apply {
                        background = roundedBlock(color, 16)
                    }, LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        leftMargin = dp(6)
                        rightMargin = dp(6)
                    })
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun addContactPreviewRows(parent: LinearLayout, color: Int) {
        repeat(3) {
            parent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(this@CalmSettingsActivity).apply {
                    background = roundedBlock(color, 999)
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    rightMargin = dp(8)
                })
                addView(View(this@CalmSettingsActivity).apply {
                    background = roundedBlock(color, 999)
                }, LinearLayout.LayoutParams(0, dp(8), 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
            })
        }
    }

    private fun renderSectionCardSettings(content: LinearLayout) {
        val mode = settings.agendaSectionMode()
        val style = settings.agendaSectionTitleStyle()
        content.addView(section("Builder"))
        content.addView(sectionCardStackPreview(mode, style))
        content.addView(choiceRow(
            title = "Stack structure",
            options = listOf(
                "Title cards" to CardStackSectionMode.TITLE_CARDS,
                "Folders" to CardStackSectionMode.FOLDERS,
            ),
            selected = mode,
        ) {
            settings.setAgendaSectionMode(it)
            requestRender()
        })
        content.addView(choiceRow(
            title = "Header background",
            options = listOf(
                "Transparent" to true,
                "Card" to false,
            ),
            selected = style.transparentBackground,
        ) {
            settings.setAgendaSectionTitleStyle(style.copy(transparentBackground = it))
            requestRender()
        })
        content.addView(choiceRow(
            title = "Header height",
            options = listOf(
                "Compact" to SectionTitleHeight.COMPACT,
                "Normal" to SectionTitleHeight.NORMAL,
                "Tall" to SectionTitleHeight.TALL,
            ),
            selected = style.height,
        ) {
            settings.setAgendaSectionTitleStyle(style.copy(height = it))
            requestRender()
        })
        content.addView(choiceRow(
            title = "Underline",
            options = listOf(
                "Off" to SectionTitleUnderline.OFF,
                "Title" to SectionTitleUnderline.TITLE,
                "Full width" to SectionTitleUnderline.FULL,
            ),
            selected = style.underline,
        ) {
            settings.setAgendaSectionTitleStyle(style.copy(underline = it))
            requestRender()
        })
        content.addView(switchRow(
            title = "Bold title",
            summary = "Use stronger section headings for date or category breaks.",
            checked = style.bold,
        ) {
            settings.setAgendaSectionTitleStyle(settings.agendaSectionTitleStyle().copy(bold = !settings.agendaSectionTitleStyle().bold))
            requestRender()
        })
        content.addView(switchRow(
            title = "Italic title",
            summary = "Use an editorial title style for section headings.",
            checked = style.italic,
        ) {
            settings.setAgendaSectionTitleStyle(settings.agendaSectionTitleStyle().copy(italic = !settings.agendaSectionTitleStyle().italic))
            requestRender()
        })
        content.addView(actionRow("Reset section cards", "Restore transparent, bold, full-width title cards.") {
            settings.setAgendaSectionMode(CardStackSectionMode.TITLE_CARDS)
            settings.setAgendaSectionTitleStyle(SectionTitleCardStyle())
            requestRender()
        })
    }

    private fun sectionCardStackPreview(
        mode: CardStackSectionMode,
        style: SectionTitleCardStyle,
    ): View {
        val colors = GoogleInteractionStyle.palette(this)
        return FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedDrawable(colors.surfaceContainer, dp(28)).apply {
                setStroke(dp(1), colors.outlineVariant)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(338)).apply {
                bottomMargin = dp(10)
            }
            addPreviewCard(
                card = sectionPreviewEventCard("13:30 - 14:00", "Design review", "Studio"),
                topMargin = 204,
                scale = 0.88f,
                alphaValue = 0.58f,
                rotationValue = -2.5f,
            )
            addPreviewCard(
                card = sectionPreviewEventCard("10:00 - 11:00", "Planning", "Meet"),
                topMargin = 134,
                scale = 0.94f,
                alphaValue = 0.78f,
                rotationValue = 1.5f,
            )
            addPreviewCard(
                card = sectionPreviewHeader(mode, style),
                topMargin = 36,
                scale = 1f,
                alphaValue = 1f,
                rotationValue = 0f,
            )
        }
    }

    private fun FrameLayout.addPreviewCard(
        card: View,
        topMargin: Int,
        scale: Float,
        alphaValue: Float,
        rotationValue: Float,
    ) {
        addView(
            card.apply {
                alpha = alphaValue
                scaleX = scale
                scaleY = scale
                rotation = rotationValue
                translationZ = dp((scale * 10).roundToInt()).toFloat()
                pivotY = 0f
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                this.topMargin = dp(topMargin)
            },
        )
    }

    private fun sectionPreviewHeader(
        mode: CardStackSectionMode,
        style: SectionTitleCardStyle,
    ): View {
        val colors = GoogleInteractionStyle.palette(this)
        val count = if (mode == CardStackSectionMode.FOLDERS) "3 events - tap to expand" else "3 events"
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(sectionPreviewVerticalPadding(style.height)), dp(22), dp(sectionPreviewVerticalPadding(style.height)))
            if (style.transparentBackground) {
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                background = roundedDrawable(GoogleInteractionStyle.surface(this@CalmSettingsActivity), dp(settings.cardCornerRadiusDp())).apply {
                    setStroke(dp(1), colors.outlineVariant)
                }
                elevation = dp(2).toFloat()
            }
            addView(label("Today", sectionPreviewTitleSp(style.height), CalmTheme.INK, sectionTitleTypeface(style)).apply {
                paintFlags = if (style.underline == SectionTitleUnderline.TITLE) {
                    paintFlags or Paint.UNDERLINE_TEXT_FLAG
                } else {
                    paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
            })
            addView(label(count, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(4), 0, 0)
            })
            if (style.underline == SectionTitleUnderline.FULL) {
                addView(View(this@CalmSettingsActivity).apply {
                    setBackgroundColor(GoogleInteractionStyle.primary(this@CalmSettingsActivity))
                    alpha = 0.7f
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply {
                    topMargin = dp(10)
                })
            }
        }
    }

    private fun sectionPreviewEventCard(
        time: String,
        title: String,
        location: String,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), 0, dp(22), 0)
            background = roundedDrawable(GoogleInteractionStyle.surface(this@CalmSettingsActivity), dp(settings.cardCornerRadiusDp())).apply {
                setStroke(dp(1), GoogleInteractionStyle.outlineVariant(this@CalmSettingsActivity))
            }
            addView(label(time, 13, CalmTheme.MUTED_INK, Typeface.NORMAL))
            addView(label(title, 20, CalmTheme.INK, Typeface.BOLD).apply {
                setPadding(0, dp(4), 0, 0)
            })
            addView(label(location, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun addAgendaPreviewRows(parent: LinearLayout, color: Int) {
        repeat(3) {
            parent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(this@CalmSettingsActivity).apply {
                    background = roundedBlock(color, 8)
                }, LinearLayout.LayoutParams(dp(28), dp(10)).apply {
                    rightMargin = dp(8)
                })
                addView(View(this@CalmSettingsActivity).apply {
                    background = roundedBlock(color, 999)
                }, LinearLayout.LayoutParams(0, dp(8), 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
            })
        }
    }

    private fun addAlarmsPreviewRows(parent: LinearLayout, color: Int) {
        addPreviewBlock(parent, dp(22), dp(22), color, bottomMargin = dp(10))
        addPreviewBlock(parent, dp(88), dp(12), color, bottomMargin = dp(8))
        addPreviewBlock(parent, dp(64), dp(8), color, bottomMargin = dp(8))
    }

    private fun addRssPreviewRows(parent: LinearLayout, color: Int) {
        listOf(92, 66, 80).forEach { width ->
            parent.addView(View(this).apply {
                background = roundedBlock(color, 10)
            }, LinearLayout.LayoutParams(dp(width), dp(14)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            })
        }
    }

    private fun addPreviewBlock(parent: LinearLayout, width: Int, height: Int, color: Int, bottomMargin: Int) {
        parent.addView(View(this).apply {
            background = roundedBlock(color, 999)
        }, LinearLayout.LayoutParams(width, height).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            this.bottomMargin = bottomMargin
        })
    }

    private fun addOverviewPreviewRows(parent: LinearLayout, color: Int, dense: Boolean) {
        val rows = if (dense) listOf(84, 64, 74, 52) else listOf(68, 88, 58)
        rows.forEach { width ->
            parent.addView(View(this).apply {
                background = roundedBlock(color, 12)
            }, LinearLayout.LayoutParams(dp(width), dp(if (dense) 14 else 18)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            })
        }
    }

    private fun roundedBlock(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }
    }

    private fun roundedDrawable(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(color)
        }
    }

    private fun sectionPreviewTitleSp(height: SectionTitleHeight): Int {
        return when (height) {
            SectionTitleHeight.COMPACT -> 18
            SectionTitleHeight.NORMAL -> 21
            SectionTitleHeight.TALL -> 25
        }
    }

    private fun sectionPreviewVerticalPadding(height: SectionTitleHeight): Int {
        return when (height) {
            SectionTitleHeight.COMPACT -> 12
            SectionTitleHeight.NORMAL -> 18
            SectionTitleHeight.TALL -> 24
        }
    }

    private fun sectionTitleTypeface(style: SectionTitleCardStyle): Int {
        return when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
    }

    private fun installPageTileGestures(
        card: View,
        slot: PageSlot,
        layout: LauncherPageLayout,
        index: Int,
        rebuild: () -> Unit,
    ) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        var menuShown = false
        val longPress = Runnable {
            menuShown = true
            showPageSlotMenu(card, slot, downRawX.toInt(), downRawY.toInt(), rebuild)
        }
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    menuShown = false
                    mainHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (menuShown) return@setOnTouchListener true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                        mainHandler.removeCallbacks(longPress)
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        view.translationX = dx
                        view.translationY = dy.coerceIn(-dp(18).toFloat(), dp(18).toFloat())
                        view.elevation = dp(10).toFloat()
                        view.scaleX = 1.03f
                        view.scaleY = 1.03f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPress)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    view.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(120L).start()
                    view.elevation = 0f
                    if (dragging) {
                        pageDropIndex(layout.order, index, dx)?.let { target ->
                            if (target != index) settings.setPageLayoutOrder(movedSlot(layout.order, index, target))
                        }
                        rebuild()
                    } else if (!menuShown && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                        view.performClick()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun pageDropIndex(order: List<PageSlot>, from: Int, dx: Float): Int? {
        if (order.isEmpty()) return null
        val delta = (dx / dp(96).toFloat()).roundToInt()
        return (from + delta).coerceIn(0, order.lastIndex)
    }

    private fun showPageSlotMenu(view: View, slot: PageSlot, x: Int, y: Int, rebuild: () -> Unit) {
        val layout = settings.pageLayout()
        val actions = mutableListOf<ContextAction>()
        if (canUseAsDefaultHome(slot)) {
            actions.add(ContextAction("Set as home", Runnable {
                settings.setDefaultHomeSlot(slot)
                rebuild()
            }))
        }
        val index = layout.order.indexOf(slot)
        if (index > 0) {
            actions.add(ContextAction("Move left", Runnable {
                settings.setPageLayoutOrder(movedSlot(settings.pageLayout().order, index, index - 1))
                rebuild()
            }))
        }
        if (index in 0 until layout.order.lastIndex) {
            actions.add(ContextAction("Move right", Runnable {
                settings.setPageLayoutOrder(movedSlot(settings.pageLayout().order, index, index + 1))
                rebuild()
            }))
        }
        GoogleInteractionStyle.popupMenu(this, view, x to y, actions)
    }

    private fun canUseAsDefaultHome(slot: PageSlot): Boolean = slot != PageSlot.NOTIFICATIONS

    private fun pageLayoutActions(rebuild: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(miniButton("Reset layout") {
                settings.setPageLayoutOrder(LauncherPageLayout.DEFAULT_ORDER)
                settings.setDefaultHomeSlot(PageSlot.OVERVIEW)
                rebuild()
            })
        }
    }

    private fun miniButton(text: String, onClick: () -> Unit): TextView {
        return label(text, 13, GoogleInteractionStyle.primary(this), Typeface.NORMAL).apply {
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GoogleInteractionStyle.chipBackground(this@CalmSettingsActivity)
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun movedSlot(order: List<PageSlot>, from: Int, to: Int): List<PageSlot> {
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun showClassicPagesDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        lateinit var rebuild: () -> Unit
        rebuild = {
            list.removeAllViews()
            list.addView(miniButton("Add page") {
                settings.addClassicPage()
                rebuild()
            }.apply {
                gravity = Gravity.CENTER
                background = GoogleInteractionStyle.chipBackground(this@CalmSettingsActivity)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            })
            val pages = settings.classicPages()
            if (pages.isEmpty()) {
                list.addView(label("No Classic pages yet.", 14, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(12))
                })
            } else {
                pages.forEachIndexed { index, page ->
                    list.addView(classicPageRow(page, index, pages.lastIndex, rebuild))
                }
            }
        }
        rebuild()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Classic pages")
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton(R.string.action_done) { _, _ -> requestRender() }
            .show()
            .setOnDismissListener { requestRender() }
    }

    private fun classicPageRow(
        page: ClassicLauncherPageDefinition,
        index: Int,
        lastIndex: Int,
        rebuild: () -> Unit,
    ): View {
        val layout = settings.pageLayout()
        val home = layout.defaultHome == PageSlot.CLASSIC_PAGES && settings.homeClassicPage()?.id == page.id
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(
                LinearLayout(this@CalmSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        label(if (home) "${page.title}  -  Home" else page.title, 16, CalmTheme.INK, Typeface.BOLD),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                },
            )
            addView(label(classicPageDetail(page), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(3), 0, dp(6))
            })
            addView(
                LinearLayout(this@CalmSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(miniButton("Rename") { showRenameClassicPageDialog(page, rebuild) })
                    addView(miniButton(if (home) "Home" else "Set home") {
                        settings.setDefaultClassicPage(page.id)
                        settings.setDefaultHomeSlot(PageSlot.CLASSIC_PAGES)
                        rebuild()
                    })
                    addView(miniButton("Up") {
                        if (index > 0 && settings.moveClassicPage(page.id, index - 1)) rebuild()
                    }.apply { isEnabled = index > 0; alpha = if (index > 0) 1f else 0.38f })
                    addView(miniButton("Down") {
                        if (index < lastIndex && settings.moveClassicPage(page.id, index + 1)) rebuild()
                    }.apply { isEnabled = index < lastIndex; alpha = if (index < lastIndex) 1f else 0.38f })
                    addView(miniButton("Remove") { confirmRemoveClassicPage(page, rebuild) })
                },
            )
        }
    }

    private fun showRenameClassicPageDialog(page: ClassicLauncherPageDefinition, rebuild: () -> Unit) {
        val input = EditText(this).apply {
            setText(page.title)
            setSingleLine(true)
            setSelection(0, text.length)
        }
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Rename Classic page")
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Save") { _, _ ->
                if (!settings.renameClassicPage(page.id, input.text.toString())) {
                    Toast.makeText(this, "Page name can't be empty", Toast.LENGTH_SHORT).show()
                }
                rebuild()
            }
            .show()
    }

    private fun confirmRemoveClassicPage(page: ClassicLauncherPageDefinition, rebuild: () -> Unit) {
        val itemCount = page.items.size
        val message = if (itemCount == 0) {
            "Remove ${page.title}?"
        } else {
            "Remove ${page.title} and its $itemCount ${if (itemCount == 1) "item" else "items"}?"
        }
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Remove Classic page")
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Remove") { _, _ ->
                settings.removeClassicPage(page.id)?.let { removed ->
                    cleanupClassicPageWidgets(removed)
                }
                rebuild()
            }
            .show()
    }

    private fun cleanupClassicPageWidgets(page: ClassicLauncherPageDefinition) {
        val widgetIds = page.items
            .filter { it.type == ClassicGridItemType.WIDGET }
            .mapNotNull { it.target.toIntOrNull() }
        if (widgetIds.isEmpty()) return
        val host = AppWidgetHost(this, ClassicWidgetHostController.HOST_ID)
        widgetIds.forEach { widgetId -> runCatching { host.deleteAppWidgetId(widgetId) } }
    }

    private fun hiddenAppsSummary(): String {
        val hidden = settings.hiddenAppKeys()
        if (hidden.isEmpty()) return "Choose apps to remove from app lists."
        val apps = cachedAppEntries ?: return "${hidden.size} hidden ${if (hidden.size == 1) "app" else "apps"}."
        val matchingHiddenCount = apps.count { app -> app.identityKey in hidden || app.packageName in hidden }
        val count = maxOf(matchingHiddenCount, hidden.size)
        return "$count hidden ${if (count == 1) "app" else "apps"}."
    }

    private fun showHiddenAppsDialog() {
        val apps = cachedAppEntries ?: run {
            Toast.makeText(this, R.string.toast_app_list_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_hidden_no_apps_available, Toast.LENGTH_SHORT).show()
            return
        }
        val hidden = settings.hiddenAppKeys()
        val selected = BooleanArray(apps.size) { index ->
            val app = apps[index]
            app.identityKey in hidden || app.packageName in hidden
        }
        val labels = apps.map(::hiddenAppLabel).toTypedArray()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle(R.string.dialog_hidden_apps_title)
            .setMultiChoiceItems(labels, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_done) { _, _ ->
                settings.setHiddenAppKeys(apps.filterIndexed { index, _ -> selected[index] }.map { it.identityKey }.toSet())
                requestRender()
            }
            .show()
    }

    private fun dockAppsSummary(): String {
        val count = settings.dockKeys().size
        if (count == 0) return "Choose apps to show in the dock."
        return "$count dock ${if (count == 1) "app" else "apps"}."
    }

    private fun classicPagesSummary(): String {
        val pages = settings.classicPages()
        if (pages.isEmpty()) return "Create an empty classic page."
        return "${pages.size} classic ${if (pages.size == 1) "page" else "pages"} added."
    }

    private fun classicPagesManagementSummary(): String {
        val pages = settings.classicPages()
        if (pages.isEmpty()) return "Create and name your Classic launcher pages."
        val home = settings.homeClassicPage()?.title ?: "no home page"
        return "${pages.size} ${if (pages.size == 1) "page" else "pages"}; home is $home."
    }

    private fun rssFeedsSummary(): String {
        val count = settings.rssFeedUrls().size
        val pageState = if (settings.rssPageEnabled()) "page added" else "page not added"
        return when (count) {
            0 -> "No feeds; $pageState."
            1 -> "1 feed; $pageState."
            else -> "$count feeds; $pageState."
        }
    }

    private fun classicPageDetail(page: ClassicLauncherPageDefinition): String {
        val itemCount = page.items.size
        return "$itemCount ${if (itemCount == 1) "item" else "items"}."
    }

    private fun dockItemSizeLabel(span: Int): String {
        return if (DockConfig.showsItemLabels(span)) {
            getString(R.string.settings_dock_size_icon_with_name)
        } else {
            getString(R.string.settings_dock_size_icon_only)
        }
    }

    private fun dockStyleLabel(style: DockStyle): String {
        return when (style) {
            DockStyle.CLASSIC -> getString(R.string.settings_dock_style_classic)
            DockStyle.CARD -> getString(R.string.settings_dock_style_card)
            DockStyle.HYBRID -> getString(R.string.settings_dock_style_hybrid)
        }
    }

    private fun dockInteractionLabel(action: DockInteractionAction): String {
        return when (action) {
            DockInteractionAction.OPEN_APP -> getString(R.string.settings_dock_action_open_app)
            DockInteractionAction.OPEN_NOTIFICATION -> getString(R.string.settings_dock_action_open_notification)
            DockInteractionAction.OPEN_CONTEXT_MENU -> getString(R.string.settings_dock_action_context_menu)
            DockInteractionAction.EXPAND -> getString(R.string.settings_dock_action_expand)
        }
    }

    private fun showDockAppsDialog() {
        val apps = cachedAppEntries ?: run {
            Toast.makeText(this, R.string.toast_dock_app_list_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.toast_dock_no_apps_available, Toast.LENGTH_SHORT).show()
            return
        }
        val dockKeys = settings.dockKeys()
        val selected = BooleanArray(apps.size) { index -> apps[index].identityKey in dockKeys }
        val labels = apps.map(::hiddenAppLabel).toTypedArray()
        val maxItems = settings.dockConfig().itemCount
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle(getString(R.string.dialog_dock_apps_title, maxItems))
            .setMultiChoiceItems(labels, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val chosen = apps.filterIndexed { index, _ -> selected[index] }
                    .map { it.identityKey }
                    .take(maxItems)
                settings.setDockKeys(chosen)
                requestRender()
            }
            .show()
    }

    private fun showRssFeedsDialog() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        lateinit var rebuild: () -> Unit
        rebuild = {
            list.removeAllViews()
            list.addView(miniButton("Add feed") { showAddRssFeedDialog(rebuild) }.apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            })
            list.addView(miniButton(if (settings.rssPageEnabled()) "Remove RSS page" else "Add RSS page") {
                settings.setRssPageEnabled(!settings.rssPageEnabled())
                rebuild()
            }.apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            })
            val urls = settings.rssFeedUrls()
            if (urls.isEmpty()) {
                list.addView(label("No RSS feeds yet.", 14, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, dp(12))
                })
            } else {
                urls.forEach { url -> list.addView(rssFeedRow(url, rebuild)) }
            }
        }
        rebuild()
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("RSS feeds")
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton(R.string.action_done) { _, _ -> requestRender() }
            .show()
            .setOnDismissListener { requestRender() }
    }

    private fun showAddRssFeedDialog(rebuild: () -> Unit) {
        val input = EditText(this).apply {
            hint = "https://example.com/feed.xml"
            setSingleLine(true)
        }
        GoogleInteractionStyle.dialogBuilder(this)
            .setTitle("Add RSS feed")
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Add") { _, _ ->
                if (!settings.addRssFeedUrl(input.text?.toString().orEmpty())) {
                    Toast.makeText(this, "Enter a new feed URL", Toast.LENGTH_SHORT).show()
                }
                rebuild()
                requestRender()
            }
            .show()
    }

    private fun rssFeedRow(url: String, rebuild: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(label(url, 15, CalmTheme.INK, Typeface.BOLD).apply {
                maxLines = 2
            })
            addView(LinearLayout(this@CalmSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(miniButton("Remove") {
                    settings.removeRssFeedUrl(url)
                    rebuild()
                })
            })
        }
    }

    private fun requestRender() {
        mainHandler.removeCallbacks(deferredRender)
        mainHandler.postDelayed(deferredRender, SETTINGS_RENDER_DELAY_MS)
    }

    private fun hiddenAppLabel(app: AppEntry): String {
        val profile = app.profileLabel.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "${app.label}$profile"
    }

    private fun sliderRow(
        title: String,
        progress: Int,
        max: Int,
        valueText: (Int) -> String,
        onChanged: (Int) -> Unit,
    ): View {
        return rowBase().apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16, CalmTheme.INK, Typeface.BOLD))
            val value = label(valueText(progress), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, dp(4), 0, dp(4))
            }
            addView(value)
            addView(SeekBar(this@CalmSettingsActivity).apply {
                this.max = max
                this.progress = progress.coerceIn(0, max)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, next: Int, fromUser: Boolean) {
                        value.text = valueText(next)
                        if (fromUser) onChanged(next)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun signedSliderRow(
        title: String,
        value: Int,
        valueText: (Int) -> String,
        onChanged: (Int) -> Unit,
    ): View {
        return sliderRow(title, value + 100, 200, { valueText(it - 100) }) { onChanged(it - 100) }
    }

    private fun stepperRow(title: String, value: Int, min: Int, max: Int, onChanged: (Int) -> Unit): View {
        return rowBase().apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 16, CalmTheme.INK, Typeface.BOLD))
            val current = label(value.toString(), 20, CalmTheme.INK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }
            val row = LinearLayout(this@CalmSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(stepButton("-") {
                    val next = (current.text.toString().toIntOrNull() ?: value).minus(1).coerceIn(min, max)
                    current.text = next.toString()
                    onChanged(next)
                })
                addView(current, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(stepButton("+") {
                    val next = (current.text.toString().toIntOrNull() ?: value).plus(1).coerceIn(min, max)
                    current.text = next.toString()
                    onChanged(next)
                })
            }
            addView(row)
        }
    }

    private fun stepButton(text: String, action: () -> Unit): TextView {
        return label(text, 20, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = GoogleInteractionStyle.chipBackground(this@CalmSettingsActivity)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(44))
        }
    }

    private fun rowBase(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GoogleInteractionStyle.rowBackground(this@CalmSettingsActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun section(text: String): TextView {
        return label(text.uppercase(Locale.getDefault()), 12, GoogleInteractionStyle.primary(this), Typeface.BOLD).apply {
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun label(text: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(GoogleInteractionStyle.mapSettingsTextColor(this@CalmSettingsActivity, color))
            textSize = sp.toFloat()
            typeface = Typeface.DEFAULT
            setTypeface(typeface, style)
            includeFontPadding = true
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, CalmNotificationListenerService::class.java)
        return enabledListeners != null &&
            enabledListeners.lowercase(Locale.ROOT).contains(componentName.flattenToString().lowercase(Locale.ROOT))
    }

    private enum class SettingsPage(val title: String) {
        ROOT("Settings"),
        APPEARANCE("Appearance"),
        PAGES("Pages"),
        APPS("Apps and icons"),
        DOCK("Dock"),
        CARD_STACK("Card stack"),
        SECTION_CARDS("Section cards"),
        ACCESS("Access"),
    }

    companion object {
        const val EXTRA_PAGE = "dev.barna.calm.extra.SETTINGS_PAGE"
        const val PAGE_SECTION_CARDS = "section_cards"
        private const val SETTINGS_RENDER_DELAY_MS = 80L
    }
}
