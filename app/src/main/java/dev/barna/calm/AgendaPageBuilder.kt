package dev.barna.calm

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AgendaPageBuilder(
    private val activity: MainActivity,
    private val cardRenderer: CardRenderer,
    private val calendarRepository: CalendarRepository,
    private val contextActionFactory: LauncherContextActionFactory,
    private val focusOverlay: FocusOverlayController,
    private val activePreferences: () -> LauncherUiPreferences,
    private val barePagePanel: (Int) -> LinearLayout,
) {
    private val dayFormat by lazy { DateFormat.getMediumDateFormat(activity) }
    private val timeFormat by lazy { DateFormat.getTimeFormat(activity) }

    fun buildPage(state: LauncherRenderModel): LinearLayout {
        return barePagePanel(activity.dp(20)).apply {
            addView(agendaHeader(state))
            addView(agendaContent(state), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun agendaHeader(state: LauncherRenderModel): View {
        val subtitle = when {
            !state.hasCalendarPermission -> "Calendar access needed"
            state.calendarEvents.isEmpty() -> "No events in the next 7 days"
            state.calendarEvents.size == 1 -> "1 upcoming event"
            else -> "${state.calendarEvents.size} upcoming events"
        }
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(8), 0, activity.dp(18))
            addView(label("Agenda", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(label(subtitle, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, 0)
            })
        }
    }

    private fun agendaContent(state: LauncherRenderModel): View {
        if (!state.hasCalendarPermission) {
            return singleStateCard(
                "Calendar access\nAllow Calm to show upcoming events in Agenda.",
                onClick = { calendarRepository.requestCalendarAccess() },
            )
        }
        if (state.calendarEvents.isEmpty()) {
            return singleStateCard("All clear\nNo upcoming calendar events found.")
        }
        return ScrollView(activity).apply {
            clipToPadding = false
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    clipChildren = false
                    clipToPadding = false
                    groupedEvents(state.calendarEvents).forEach { group ->
                        addView(sectionTitle(group.title))
                        group.events.forEach { event ->
                            addView(
                                eventCard(event),
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardRenderer.cardHeight()).apply {
                                    bottomMargin = activity.dp(12)
                                },
                            )
                        }
                    }
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun singleStateCard(text: String, onClick: (() -> Unit)? = null): View {
        return LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            addView(
                cardRenderer.stackCard(
                    text,
                    CalmTheme.ACCENT,
                    true,
                    cardRenderer.cardSideIcon(R.drawable.ic_calendar_card),
                    sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
                ).apply {
                    maxLines = 4
                    if (onClick != null) setOnClickListener { onClick() }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardRenderer.cardHeight()),
            )
        }
    }

    private fun eventCard(event: CalendarEvent): TextView {
        val title = event.title.ifBlank { "Untitled event" }
        val location = event.location.ifBlank { "" }.let { if (it.isBlank()) "" else "\n$it" }
        val text = "${eventTime(event)}\n$title$location"
        val today = isToday(event.begin)
        return cardRenderer.stackCard(
            text,
            if (today) CalmTheme.ACCENT else Color.rgb(122, 146, 178),
            activePreferences().useTintedNotificationCards,
            cardRenderer.cardSideIcon(R.drawable.ic_calendar_card),
            sideImageRenderKey = "res:${R.drawable.ic_calendar_card}",
        ).apply {
            maxLines = 4
            setOnClickListener {
                contextActionFactory.calendarActions(event, calendarRepository.hasCalendarPermission())
                    .firstOrNull()
                    ?.action
                    ?.run()
            }
            setOnLongClickListener {
                focusOverlay.show(
                    this,
                    contextActionFactory.calendarActions(event, calendarRepository.hasCalendarPermission()),
                )
                true
            }
        }
    }

    private fun sectionTitle(text: String): TextView {
        return label(text.uppercase(Locale.getDefault()), 12, CalmTheme.ACCENT, Typeface.BOLD).apply {
            tag = CalmAnimationTags.CHROME
            setPadding(0, activity.dp(12), 0, activity.dp(8))
        }
    }

    private fun groupedEvents(events: List<CalendarEvent>): List<AgendaEventGroup> {
        return events
            .groupBy { dayStart(it.begin) }
            .toSortedMap()
            .map { (day, dayEvents) ->
                AgendaEventGroup(dayTitle(day), dayEvents.sortedBy { it.begin })
            }
    }

    private fun dayTitle(dayStart: Long): String {
        return when {
            isToday(dayStart) -> "Today"
            isTomorrow(dayStart) -> "Tomorrow"
            else -> dayFormat.format(Date(dayStart))
        }
    }

    private fun eventTime(event: CalendarEvent): String {
        if (event.allDay) return "All day"
        val start = timeFormat.format(Date(event.begin))
        val end = timeFormat.format(Date(event.end))
        return "$start - $end"
    }

    private fun dayStart(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isToday(timeMillis: Long): Boolean {
        return calendarRepository.isToday(timeMillis)
    }

    private fun isTomorrow(timeMillis: Long): Boolean {
        val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return target.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
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

    private data class AgendaEventGroup(
        val title: String,
        val events: List<CalendarEvent>,
    )
}
