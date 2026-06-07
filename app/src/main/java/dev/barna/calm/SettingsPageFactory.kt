package dev.barna.calm

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class SettingsPageFactory(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val drawables: CalmDrawables,
    private val calendarRepository: CalendarRepository,
    private val notificationRepository: NotificationChapterRepository,
    private val copyFormatter: SettingsCopyFormatter = SettingsCopyFormatter(),
    private val actions: SettingsPageActions,
) {
    fun create(): View {
        val page = createPagePanel()
        val scrollView = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, activity.dp(18))
        }
        scrollView.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.apply {
            addView(label("CHAPTER / SETTINGS", 12, CalmTheme.ACCENT, Typeface.BOLD))
            addView(label("Launcher settings", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(8), 0, 0)
            })
            addView(label("Wallpaper, access, and hidden notification sources live here.", 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, activity.dp(18))
            })
            addView(sectionTitle("Appearance"))
            addView(fullWidthAction(copyFormatter.notificationSurface(settings.useTintedNotificationCards()), actions.toggleNotificationSurface))
            addView(fullWidthAction(copyFormatter.cardHaptics(settings.cardHapticsEnabled()), actions.toggleCardHaptics))
            addView(hapticStrengthControl())
            addView(sectionTitle("Apps"))
            addView(fullWidthAction(copyFormatter.appLibrary(settings.splitAppsByProfile()), actions.toggleSplitAppsByProfile))
            addView(fullWidthAction(copyFormatter.workNotificationPlacement(settings.placeWorkNotificationChaptersBeforeApps()), actions.toggleWorkNotificationChapterPlacement))
            addView(sectionTitle("Card stack"))
            addView(fullWidthAction("Timescape preset", actions.applyTimescapeStackPreset))
            addView(cardStackHorizontalCurveControl())
            addView(cardStackArcWidthControl())
            addView(aboveFocusCardCountControl())
            addView(focusedCardGapControl())
            addView(focusedCardScaleControl())
            addView(stackPeakPositionControl())
            addView(cardStackRotationControl())
            addView(fullWidthAction(copyFormatter.advancedStackControls(settings.showAdvancedStackControls()), actions.toggleAdvancedStackControls))
            if (settings.showAdvancedStackControls()) {
                addView(cardStackCurveControl())
                addView(cardStackSpacingControl())
                addView(visibleCardCountControl())
                addView(magnetStrengthControl())
            }
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
                    addView(fullWidthAction("Restore ${source.label}") { actions.restoreNotificationSource(source.packageName) })
                }
            }
        }
        return page
    }

    private fun createPagePanel(): LinearLayout {
        return LinearLayout(activity).apply {
            background = drawables.glass(CalmTheme.GLASS, activity.dp(22))
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(activity.dp(20), activity.dp(28), activity.dp(20), activity.dp(30))
            elevation = activity.dp(1).toFloat()
            translationZ = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = activity.dp(20)
                bottomMargin = activity.dp(18)
            }
        }
    }

    private fun hapticStrengthControl(): View {
        val card = controlCard()
        card.addView(label("Haptic strength", 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(copyFormatter.hapticStrength(settings.cardHapticStrength()), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
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
                    value.text = copyFormatter.hapticStrength(strength)
                    if (fromUser) {
                        settings.setCardHapticStrength(strength)
                        actions.performCardScrollHaptic(card)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun cardStackCurveControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Visual curve",
            initialProgress = tuning.curve,
            valueText = copyFormatter::visualCurve,
            onChanged = settings::setCardStackCurve,
        )
    }

    private fun cardStackSpacingControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Vertical spacing",
            initialProgress = tuning.verticalSpacing,
            valueText = copyFormatter::verticalSpacing,
            onChanged = settings::setCardStackSpacing,
        )
    }

    private fun cardStackHorizontalCurveControl(): View {
        val tuning = settings.cardStackTuning()
        return signedSliderCard(
            title = "Left / right path",
            initialValue = tuning.horizontalCurve,
            valueText = copyFormatter::horizontalCurve,
            onChanged = settings::setCardStackHorizontalCurve,
        )
    }

    private fun cardStackArcWidthControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Arc width",
            initialProgress = tuning.arcWidth,
            valueText = copyFormatter::cardArcWidth,
            onChanged = settings::setCardStackArcWidth,
        )
    }

    private fun cardStackRotationControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Card fan rotation",
            initialProgress = tuning.rotation,
            valueText = copyFormatter::cardFanRotation,
            onChanged = settings::setCardStackRotation,
        )
    }

    private fun focusedCardGapControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Focused card gap",
            initialProgress = tuning.focusedCardGap,
            valueText = copyFormatter::focusedCardGap,
            onChanged = settings::setFocusedCardGap,
        )
    }

    private fun focusedCardScaleControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Focused card size",
            initialProgress = tuning.focusedCardScale,
            valueText = copyFormatter::focusedCardScale,
            onChanged = settings::setFocusedCardScale,
        )
    }

    private fun magnetStrengthControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Magnet strength",
            initialProgress = tuning.magnetStrength,
            valueText = copyFormatter::magnetStrength,
            onChanged = settings::setMagnetStrength,
        )
    }

    private fun stackPeakPositionControl(): View {
        val tuning = settings.cardStackTuning()
        return sliderCard(
            title = "Stack peak position",
            initialProgress = tuning.stackPeakPosition,
            valueText = copyFormatter::stackPeakPosition,
            onChanged = settings::setStackPeakPosition,
        )
    }

    private fun aboveFocusCardCountControl(): View {
        val tuning = settings.cardStackTuning()
        val card = controlCard()
        card.addView(label("Cards above focus", 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(copyFormatter.visibleCards(tuning.aboveFocusCards), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(4), 0, activity.dp(8))
        }
        card.addView(value)
        val row = stepperRow(tuning.aboveFocusCards)
        val countLabel = row.getChildAt(1) as TextView
        (row.getChildAt(0) as TextView).setOnClickListener {
            val next = (settings.cardStackTuning().aboveFocusCards - 1).coerceIn(0, 4)
            settings.setAboveFocusCardCount(next)
            value.text = copyFormatter.visibleCards(next)
            countLabel.text = next.toString()
            actions.render()
        }
        (row.getChildAt(2) as TextView).setOnClickListener {
            val next = (settings.cardStackTuning().aboveFocusCards + 1).coerceIn(0, 4)
            settings.setAboveFocusCardCount(next)
            value.text = copyFormatter.visibleCards(next)
            countLabel.text = next.toString()
            actions.render()
        }
        card.addView(row)
        return card
    }

    private fun visibleCardCountControl(): View {
        val tuning = settings.cardStackTuning()
        val card = controlCard()
        card.addView(label("Visible cards", 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(copyFormatter.visibleCards(tuning.visibleCards), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(4), 0, activity.dp(8))
        }
        card.addView(value)
        val row = stepperRow(tuning.visibleCards)
        val countLabel = row.getChildAt(1) as TextView
        (row.getChildAt(0) as TextView).setOnClickListener {
            val next = (settings.cardStackTuning().visibleCards - 1).coerceIn(1, 5)
            settings.setVisibleCardCount(next)
            value.text = copyFormatter.visibleCards(next)
            countLabel.text = next.toString()
            actions.render()
        }
        (row.getChildAt(2) as TextView).setOnClickListener {
            val next = (settings.cardStackTuning().visibleCards + 1).coerceIn(1, 5)
            settings.setVisibleCardCount(next)
            value.text = copyFormatter.visibleCards(next)
            countLabel.text = next.toString()
            actions.render()
        }
        card.addView(row)
        return card
    }

    private fun sliderCard(
        title: String,
        initialProgress: Int,
        valueText: (Int) -> String,
        onChanged: (Int) -> Unit,
    ): View {
        val card = controlCard()
        card.addView(label(title, 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(valueText(initialProgress), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(4), 0, activity.dp(4))
        }
        card.addView(value)
        card.addView(SeekBar(activity).apply {
            max = 100
            progress = initialProgress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    value.text = valueText(progress)
                    if (fromUser) onChanged(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    actions.render()
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun signedSliderCard(
        title: String,
        initialValue: Int,
        valueText: (Int) -> String,
        onChanged: (Int) -> Unit,
    ): View {
        val card = controlCard()
        card.addView(label(title, 15, CalmTheme.INK, Typeface.BOLD))
        val value = label(valueText(initialValue), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
            setPadding(0, activity.dp(4), 0, activity.dp(4))
        }
        card.addView(value)
        card.addView(SeekBar(activity).apply {
            max = 200
            progress = initialValue + 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val signedValue = progress - 100
                    value.text = valueText(signedValue)
                    if (fromUser) onChanged(signedValue)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    actions.render()
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun stepperRow(initialValue: Int): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(stepperButton("-"))
            addView(label(initialValue.toString(), 18, CalmTheme.INK, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(stepperButton("+"))
        }
    }

    private fun controlCard(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(10)
            }
        }
    }

    private fun fullWidthAction(text: String, action: () -> Unit): TextView {
        return label(text, 16, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(16))
            elevation = 0f
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(10)
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

    private fun stepperButton(text: String): TextView {
        return label(text, 22, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            layoutParams = LinearLayout.LayoutParams(activity.dp(46), activity.dp(38))
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

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(activity.contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(activity, CalmNotificationListenerService::class.java)
        return enabledListeners != null &&
            enabledListeners.lowercase(Locale.ROOT).contains(componentName.flattenToString().lowercase(Locale.ROOT))
    }

    private fun openNotificationAccess() {
        activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openWallpaperPicker() {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper"))
    }
}

data class SettingsPageActions(
    val toggleNotificationSurface: () -> Unit,
    val toggleCardHaptics: () -> Unit,
    val toggleSplitAppsByProfile: () -> Unit,
    val toggleWorkNotificationChapterPlacement: () -> Unit,
    val applyTimescapeStackPreset: () -> Unit,
    val toggleAdvancedStackControls: () -> Unit,
    val restoreNotificationSource: (String) -> Unit,
    val render: () -> Unit,
    val performCardScrollHaptic: (View) -> Unit,
)
