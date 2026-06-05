package dev.barna.calm

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Date

class CalendarRepository(private val activity: Activity) {
    fun hasCalendarPermission(): Boolean {
        return activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCalendarAccess() {
        activity.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), CalmTheme.REQUEST_CALENDAR)
    }

    fun loadUpcomingEvents(): List<CalendarEvent> {
        val events = ArrayList<CalendarEvent>()
        val now = System.currentTimeMillis()
        val horizon = now + 7L * 24L * 60L * 60L * 1000L
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, horizon)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
        )

        try {
            activity.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 5) {
                    events.add(
                        CalendarEvent(
                            cursor.getString(0),
                            cursor.getLong(1),
                            cursor.getLong(2),
                            cursor.getString(3),
                            cursor.getInt(4) == 1,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            return events
        }
        return events
    }

    fun isToday(timeMillis: Long): Boolean {
        val then = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val now = Calendar.getInstance()
        return then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    fun formatEventTime(event: CalendarEvent): String {
        if (event.allDay) {
            return DateFormat.getDateFormat(activity).format(Date(event.begin))
        }
        val startDate = DateFormat.getDateFormat(activity).format(Date(event.begin))
        val startTime = DateFormat.getTimeFormat(activity).format(Date(event.begin))
        val endTime = DateFormat.getTimeFormat(activity).format(Date(event.end))
        return "$startDate / $startTime - $endTime"
    }
}
