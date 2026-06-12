package dev.barna.calm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.provider.AlarmClock
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.Date

class AlarmsPageBuilder(
    private val activity: MainActivity,
    private val cardRenderer: CardRenderer,
    private val activePreferences: () -> LauncherUiPreferences,
    private val barePagePanel: (Int) -> LinearLayout,
) {
    private val timeFormat by lazy { DateFormat.getTimeFormat(activity) }
    private val dateFormat by lazy { DateFormat.getMediumDateFormat(activity) }

    fun buildPage(): LinearLayout {
        val nextAlarm = nextAlarmClock()
        return barePagePanel(activity.dp(20)).apply {
            addView(alarmsHeader(nextAlarm))
            addView(alarmsContent(nextAlarm), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun alarmsHeader(nextAlarm: AlarmManager.AlarmClockInfo?): View {
        val subtitle = nextAlarm?.let { "Next alarm ${alarmTime(it)}" } ?: "No upcoming alarm"
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.VERTICAL
            setPadding(0, activity.dp(8), 0, activity.dp(18))
            addView(label("Alarms", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(label(subtitle, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(6), 0, 0)
            })
        }
    }

    private fun alarmsContent(nextAlarm: AlarmManager.AlarmClockInfo?): View {
        return LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            addView(
                if (nextAlarm == null) emptyAlarmCard() else nextAlarmCard(nextAlarm),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardRenderer.cardHeight()),
            )
        }
    }

    private fun nextAlarmCard(nextAlarm: AlarmManager.AlarmClockInfo): TextView {
        return alarmCard(
            "Next alarm\n${alarmTime(nextAlarm)}\n${alarmDay(nextAlarm)}",
        ).apply {
            setOnClickListener { openNextAlarm(nextAlarm) }
            setOnLongClickListener {
                openAlarmApp()
                true
            }
        }
    }

    private fun emptyAlarmCard(): TextView {
        return alarmCard("All clear\nNo upcoming alarm is scheduled.\nTap to open alarms.").apply {
            setOnClickListener { openAlarmApp() }
        }
    }

    private fun alarmCard(text: String): TextView {
        return cardRenderer.stackCard(
            text,
            CalmTheme.ACCENT,
            activePreferences().useTintedNotificationCards,
            cardRenderer.cardSideIcon(R.drawable.ic_alarm_card),
            sideImageRenderKey = "res:${R.drawable.ic_alarm_card}",
        ).apply {
            maxLines = 4
        }
    }

    private fun nextAlarmClock(): AlarmManager.AlarmClockInfo? {
        val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.nextAlarmClock?.takeIf { it.triggerTime > System.currentTimeMillis() }
    }

    private fun alarmTime(nextAlarm: AlarmManager.AlarmClockInfo): String {
        return timeFormat.format(Date(nextAlarm.triggerTime))
    }

    private fun alarmDay(nextAlarm: AlarmManager.AlarmClockInfo): String {
        val trigger = Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTime }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        return when {
            sameDay(trigger, today) -> "Today"
            sameDay(trigger, tomorrow) -> "Tomorrow"
            else -> dateFormat.format(Date(nextAlarm.triggerTime))
        }
    }

    private fun sameDay(left: Calendar, right: Calendar): Boolean {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
            left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
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
        openAlarmApp()
    }

    private fun openAlarmApp() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
            .recoverCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
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
}
