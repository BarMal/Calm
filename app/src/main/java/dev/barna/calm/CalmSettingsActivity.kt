package dev.barna.calm

import android.app.AlertDialog
import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

class CalmSettingsActivity : ComponentActivity() {
    private lateinit var settings: LauncherSettings
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var appRepository: NotificationChapterRepository
    private lateinit var drawables: CalmDrawables
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deferredRender = Runnable { render() }
    private var settingsScrollY = 0
    private var cachedAppEntries: List<AppEntry>? = null
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
        drawables = CalmDrawables(this)
        configureWindow()
        render()
        Thread {
            val entries = appRepository.loadAppEntries()
            mainHandler.post {
                cachedAppEntries = entries
                requestRender()
            }
        }.start()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(deferredRender)
        super.onDestroy()
    }

    private fun configureWindow() {
        CalmSystemBars.applySettingsWindow(this)
    }

    private fun render() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), statusBarHeightFallback() + dp(18), dp(18), dp(20))
            setBackgroundColor(CalmTheme.SURFACE)
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

        content.addView(label("Settings", 32, CalmTheme.INK, Typeface.NORMAL).apply {
            setPadding(0, dp(2), 0, dp(18))
        })
        content.addView(section("Appearance"))
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
        content.addView(actionRow(
            "Page order",
            "Sorted by ${pageSortLabel(settings.pageSortOrder())}.",
        ) {
            showPageSortDialog()
        })
        content.addView(actionRow(
            "Hidden apps",
            hiddenAppsSummary(),
        ) {
            showHiddenAppsDialog()
        })

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
        scrollView.post { scrollView.scrollTo(0, settingsScrollY) }
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
            thumbTintList = ColorStateList(states, intArrayOf(CalmTheme.ACCENT, CalmTheme.INK))
            trackTintList = ColorStateList(
                states,
                intArrayOf(withAlpha(CalmTheme.ACCENT, 140), withAlpha(CalmTheme.MUTED_INK, 90)),
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

    private fun pageSortLabel(order: PageSortOrder): String {
        return when (order) {
            PageSortOrder.APP_NAME_ASC -> "app name (A–Z)"
            PageSortOrder.APP_NAME_DESC -> "app name (Z–A)"
            PageSortOrder.NOTIFICATION_AGE_NEWEST -> "newest notification first"
            PageSortOrder.NOTIFICATION_AGE_OLDEST -> "oldest notification first"
        }
    }

    private fun showPageSortDialog() {
        val options = PageSortOrder.entries.toTypedArray()
        val current = options.indexOf(settings.pageSortOrder()).coerceAtLeast(0)
        val labels = options.map { order ->
            pageSortLabel(order).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Page order")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings.setPageSortOrder(options[which])
                requestRender()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            Toast.makeText(this, "App list is loading, try again shortly", Toast.LENGTH_SHORT).show()
            return
        }
        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps are available to hide", Toast.LENGTH_SHORT).show()
            return
        }
        val hidden = settings.hiddenAppKeys()
        val selected = BooleanArray(apps.size) { index ->
            val app = apps[index]
            app.identityKey in hidden || app.packageName in hidden
        }
        val labels = apps.map(::hiddenAppLabel).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Hidden apps")
            .setMultiChoiceItems(labels, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ ->
                settings.setHiddenAppKeys(apps.filterIndexed { index, _ -> selected[index] }.map { it.identityKey }.toSet())
                requestRender()
            }
            .show()
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
            background = drawables.glass(CalmTheme.QUIET_GLASS, dp(14))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(44))
        }
    }

    private fun rowBase(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(CalmTheme.SURFACE_CONTAINER)
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun section(text: String): TextView {
        return label(text.uppercase(Locale.getDefault()), 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun label(text: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
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

    private companion object {
        const val SETTINGS_RENDER_DELAY_MS = 80L
    }
}
