package dev.barna.calm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date
import java.util.Locale

class OverviewPageBuilder(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val cardRenderer: CardRenderer,
    private val cardStackController: CardStackController,
    private val notificationCardDisplayCache: NotificationCardDisplayCache,
    private val contextActionFactory: LauncherContextActionFactory,
    private val focusOverlay: FocusOverlayController,
    private val calendarRepository: CalendarRepository,
    private val activePreferences: () -> LauncherUiPreferences,
    private val createBarePagePanel: () -> LinearLayout,
    private val openSettingsActivity: () -> Unit,
    private val openCalendarEvent: (CalendarEvent) -> Unit,
) {
    private val expandedOverviewGroups = mutableSetOf<String>()

    fun buildPage(state: LauncherRenderModel, workProfile: Boolean = false): LinearLayout {
        val chapters = overviewChapters(state, workProfile)
        return createBarePagePanel().apply {
            addView(overviewHeader(if (workProfile) "Work" else "Overview"))
            addView(sectionTitle("Notifications"))
            addView(
                stackToolbarSpacer(),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)),
            )
            val notifContainer = FrameLayout(activity).apply {
                clipChildren = false
                clipToPadding = false
            }
            var rebuild: () -> Unit = {}
            rebuild = {
                notifContainer.removeAllViews()
                notifContainer.addView(
                    overviewNotificationsStack(chapters) { rebuild() },
                    matchParentParams(),
                )
            }
            rebuild()
            addView(notifContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    /**
     * When profile split is on, the overview shows only personal notifications and the dedicated
     * work overview shows only work notifications; otherwise the overview shows everything.
     */
    private fun overviewChapters(state: LauncherRenderModel, workProfile: Boolean): List<AppChapter> {
        if (!state.preferences.splitAppsByProfile) return state.notificationChapters
        return state.notificationChapters.filter { it.isWorkProfile == workProfile }
    }

    private fun overviewHeader(title: String): View {
        val nextAlarm = nextAlarmClock()
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, activity.dp(24))

            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(title, 30, CalmTheme.INK, Typeface.NORMAL).apply {
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
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                settingsButton(),
                LinearLayout.LayoutParams(activity.dp(58), activity.dp(58)).apply {
                    leftMargin = activity.dp(14)
                },
            )
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

    private fun stackToolbarSpacer(): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
        }
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
            if (chapter.notifications.size == 1) {
                cards.add(overviewNotificationCard(NotificationCardItem(chapter.notifications), chapter, nested = false))
                continue
            }
            val isExpanded = chapter.identityKey in expandedOverviewGroups
            cards.add(overviewGroupHeaderCard(chapter, isExpanded, onRebuild))
            if (isExpanded) {
                NotificationCardGrouper.cards(chapter.notifications, groupingEnabled = false).forEach { item ->
                    cards.add(overviewNotificationCard(item, chapter, nested = true))
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
            activePreferences().cardStackTuning,
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
            activePreferences().useTintedNotificationCards,
            icon,
            sideImageRenderKey = "icon:${chapter.identityKey}",
        ).apply {
            setOnClickListener {
                if (!expandedOverviewGroups.remove(chapter.identityKey)) {
                    expandedOverviewGroups.add(chapter.identityKey)
                }
                onRebuild()
            }
            if (chapter.launchable) {
                setOnLongClickListener {
                    focusOverlay.show(this, listOf(contextActionFactory.dockAction(chapter.launcherIdentityKey, chapter.label)), chapter.label)
                    true
                }
            }
        }
    }

    private fun overviewNotificationCard(
        item: NotificationCardItem,
        chapter: AppChapter,
        nested: Boolean,
    ): TextView {
        val data = notificationCardDisplayCache.getOrCreate(item, chapter, ::formatNotificationTime)
        val hueColor = if (nested) OverviewChildCardStyle.hueColor(chapter.hueColor, item) else chapter.hueColor
        return cardRenderer.stackCard(
            data.text,
            hueColor,
            nested || activePreferences().useTintedNotificationCards,
            data.sideImage.takeUnless { nested },
            data.sideImageAlpha,
            data.sideImageRenderKey.takeUnless { nested },
        ).apply {
            maxLines = 3
            if (nested) {
                setPadding(paddingLeft + activity.dp(20), paddingTop, paddingRight, paddingBottom)
            }
            setOnClickListener {
                focusOverlay.show(this, contextActionFactory.notificationActions(item, chapter), data.fullText)
            }
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
            activePreferences().cardStackTuning,
            CardStackStateKey.OVERVIEW_CALENDAR,
        )
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
            } catch (_: PendingIntent.CanceledException) {
            }
        }
        SafeActivityLauncher.startOrToast(
            activity,
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Settings unavailable",
        )
    }

    private fun sectionTitle(text: String): TextView {
        return label(text.uppercase(Locale.getDefault()), 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
            tag = CalmAnimationTags.CHROME
            setPadding(0, activity.dp(14), 0, activity.dp(8))
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
}
