package dev.barna.calm

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Date

class AgendaPageBuilder(
    private val activity: MainActivity,
    private val cardRenderer: CardRenderer,
    private val cardStackController: CardStackController,
    private val calendarRepository: CalendarRepository,
    private val contextActionFactory: LauncherContextActionFactory,
    private val focusOverlay: FocusOverlayController,
    private val activePreferences: () -> LauncherUiPreferences,
    private val barePagePanel: (Int) -> LinearLayout,
    private val render: () -> Unit,
    private val openSectionCardSettings: () -> Unit,
) {
    private val dayFormat by lazy { DateFormat.getMediumDateFormat(activity) }
    private val timeFormat by lazy { DateFormat.getTimeFormat(activity) }
    private val expandedDayGroups = mutableSetOf<Long>()

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
        return cardStackController.cardStack(
            agendaCards(groupedEvents(state.calendarEvents)),
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            CardStackStateKey.AGENDA,
        )
    }

    private fun singleStateCard(text: String, onClick: (() -> Unit)? = null): View {
        return cardStackController.cardStack(
            listOf(
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
            ),
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            CardStackStateKey.AGENDA,
        )
    }

    private fun agendaCards(groups: List<AgendaEventGroup>): List<TextView> {
        val cards = mutableListOf<TextView>()
        val mode = activePreferences().agendaSectionMode
        groups.forEach { group ->
            val expanded = group.dayStart in expandedDayGroups
            cards += sectionTitleCard(group, mode, expanded)
            if (mode == CardStackSectionMode.TITLE_CARDS || expanded) {
                cards += group.events.map(::eventCard)
            }
        }
        return cards
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

    private fun sectionTitleCard(
        group: AgendaEventGroup,
        mode: CardStackSectionMode,
        expanded: Boolean,
    ): TextView {
        val style = activePreferences().agendaSectionTitleStyle
        val count = group.events.size
        val summary = when {
            mode == CardStackSectionMode.FOLDERS && expanded -> "$count event${if (count == 1) "" else "s"} - tap to collapse"
            mode == CardStackSectionMode.FOLDERS -> "$count event${if (count == 1) "" else "s"} - tap to expand"
            else -> "$count event${if (count == 1) "" else "s"}"
        }
        val text = sectionTitleText(group.title, summary, style.underline)
        val card = if (style.transparentBackground) {
            label(text, sectionTitleSp(style.height), CalmTheme.INK, sectionTitleTypeface(style)).apply {
                background = ColorDrawable(Color.TRANSPARENT)
                elevation = 0f
            }
        } else {
            cardRenderer.stackCard(text, CalmTheme.ACCENT, activePreferences().useTintedNotificationCards).apply {
                setTypeface(Typeface.DEFAULT, sectionTitleTypeface(style))
                textSize = sectionTitleSp(style.height).toFloat()
            }
        }
        return card.apply {
            maxLines = if (style.underline == SectionTitleUnderline.FULL) 4 else 3
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(
                activity.dp(22),
                activity.dp(sectionTitleVerticalPadding(style.height)),
                activity.dp(22),
                activity.dp(sectionTitleVerticalPadding(style.height)),
            )
            paintFlags = if (style.underline == SectionTitleUnderline.TITLE) {
                paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            if (mode == CardStackSectionMode.FOLDERS) {
                setOnClickListener {
                    if (!expandedDayGroups.remove(group.dayStart)) {
                        expandedDayGroups.add(group.dayStart)
                    }
                    render()
                }
            }
            setOnLongClickListener {
                openSectionCardSettings()
                true
            }
        }
    }

    private fun groupedEvents(events: List<CalendarEvent>): List<AgendaEventGroup> {
        return events
            .groupBy { dayStart(it.begin) }
            .toSortedMap()
            .map { (day, dayEvents) ->
                AgendaEventGroup(day, dayTitle(day), dayEvents.sortedBy { it.begin })
            }
    }

    private fun sectionTitleText(title: String, summary: String, underline: SectionTitleUnderline): String {
        return when (underline) {
            SectionTitleUnderline.FULL -> "$title\n$summary\n------------------------------"
            else -> "$title\n$summary"
        }
    }

    private fun sectionTitleSp(height: SectionTitleHeight): Int {
        return when (height) {
            SectionTitleHeight.COMPACT -> 18
            SectionTitleHeight.NORMAL -> 21
            SectionTitleHeight.TALL -> 25
        }
    }

    private fun sectionTitleVerticalPadding(height: SectionTitleHeight): Int {
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
        val dayStart: Long,
        val title: String,
        val events: List<CalendarEvent>,
    )
}
