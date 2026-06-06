package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewCopyFormatterTest {
    private val formatter = OverviewCopyFormatter()

    @Test
    fun nextAlarmSummaryHandlesMissingAndPresentAlarm() {
        assertEquals("No upcoming alarm is scheduled.", formatter.nextAlarmSummary(null))
        assertEquals("Next alarm 07:30", formatter.nextAlarmSummary("07:30"))
    }

    @Test
    fun calendarCardCopyMatchesLauncherText() {
        assertEquals(
            "Calendar access\nCalendar access is needed before Calm can index upcoming events.\nManage it in Settings.",
            formatter.calendarAccessCardText(),
        )
        assertEquals("Upcoming calendar\nNo upcoming calendar events found.", formatter.emptyCalendarCardText())
    }

    @Test
    fun calendarEventPrefixUsesTodayOrUpcoming() {
        assertEquals("Today", formatter.calendarEventPrefix(true))
        assertEquals("Upcoming", formatter.calendarEventPrefix(false))
    }
}
