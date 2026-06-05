package dev.barna.calm

class CalendarEvent(
    @JvmField val title: String,
    @JvmField val begin: Long,
    @JvmField val end: Long,
    @JvmField val location: String,
    @JvmField val allDay: Boolean,
)
