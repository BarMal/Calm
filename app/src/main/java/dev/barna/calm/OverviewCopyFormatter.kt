package dev.barna.calm

class OverviewCopyFormatter {
    fun nextAlarmSummary(formattedAlarmTime: String?): String {
        return if (formattedAlarmTime == null) "No upcoming alarm is scheduled." else "Next alarm $formattedAlarmTime"
    }

    fun calendarAccessCardText(): String {
        return "Calendar access\nCalendar access is needed before Calm can index upcoming events.\nManage it in Settings."
    }

    fun emptyCalendarCardText(): String {
        return "Upcoming calendar\nNo upcoming calendar events found."
    }

    fun calendarEventPrefix(isToday: Boolean): String {
        return if (isToday) "Today" else "Upcoming"
    }
}
