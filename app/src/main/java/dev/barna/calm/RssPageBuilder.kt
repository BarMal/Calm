package dev.barna.calm

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class RssPageBuilder(
    private val activity: MainActivity,
    private val cardRenderer: CardRenderer,
    private val cardStackController: CardStackController,
    private val activePreferences: () -> LauncherUiPreferences,
    private val barePagePanel: (Int) -> LinearLayout,
    private val label: (String, Int, Int, Int) -> TextView,
) {
    fun buildPage(state: LauncherRenderModel): View {
        return barePagePanel(activity.dp(14)).apply {
            addView(header(state.rssFeedUrls))
            addView(content(state.rssItems, state.rssFeedUrls), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun header(feedUrls: List<String>): View {
        val subtitle = when (feedUrls.size) {
            0 -> "No feeds configured"
            1 -> "1 feed"
            else -> "${feedUrls.size} feeds"
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(4), activity.dp(8), activity.dp(4), activity.dp(18))
            addView(label("RSS", 30, CalmTheme.INK, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(label(subtitle, 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, activity.dp(6), 0, 0)
            })
        }
    }

    private fun content(items: List<RssFeedItem>, feedUrls: List<String>): View {
        val cards = when {
            feedUrls.isEmpty() -> listOf(emptyCard("RSS\nNo feeds yet.\nAdd feed URLs in Settings."))
            items.isEmpty() -> listOf(emptyCard("RSS\nNo recent items.\nCheck feed URLs in Settings."))
            else -> items.map(::itemCard)
        }
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            CardStackStateKey.RSS,
        )
    }

    private fun itemCard(item: RssFeedItem): TextView {
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.publishedAt))
        val summary = item.summary.take(180)
        val body = buildString {
            append(item.feedTitle)
            append('\n')
            append(item.title)
            if (summary.isNotBlank()) {
                append("\n")
                append(summary)
            }
            append("\n")
            append(time)
        }
        return card(body).apply {
            setOnClickListener {
                if (item.link.isNotBlank()) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    private fun emptyCard(text: String): TextView = card(text)

    private fun card(text: String): TextView {
        return cardRenderer.stackCard(
            text,
            CalmTheme.ACCENT,
            activePreferences().useTintedNotificationCards,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
    }
}
