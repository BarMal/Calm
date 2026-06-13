package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Date
import java.util.Locale

class ChapterPageBuilder(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val settings: LauncherSettings,
    private val notificationCardDisplayCache: NotificationCardDisplayCache,
    private val notificationRepository: NotificationChapterRepository,
    private val cardStackController: CardStackController,
    private val cardRenderer: CardRenderer,
    private val notificationActionController: NotificationActionController,
    private val contextActionFactory: LauncherContextActionFactory,
    private val focusOverlay: FocusOverlayController,
    private val activePreferences: () -> LauncherUiPreferences,
    private val createPagePanel: (Bitmap?, Int) -> LinearLayout,
    private val createBarePagePanel: (Int) -> LinearLayout,
    private val openPackage: (AppChapter) -> Unit,
    private val toggleNotificationGrouping: (AppChapter) -> Unit,
) {
    fun buildPage(chapter: AppChapter): LinearLayout {
        val preferences = activePreferences()
        val tintCards = preferences.useTintedNotificationCards
        val fullScreen = preferences.fullScreenModeEnabled
        val page = if (tintCards) {
            createBarePagePanel(activity.dp(20))
        } else {
            createPagePanel(notificationRepository.resolveChapterBackground(chapter), chapter.hueColor)
        }
        if (!fullScreen) {
            page.addView(chapterHeader(chapter))
        }
        page.addView(
            notificationArea(chapter, tintCards),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, if (fullScreen) 1f else 2.25f),
        )
        return page
    }

    private fun chapterHeader(chapter: AppChapter): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            clipChildren = false
            setPadding(0, 0, 0, activity.dp(24))
            addView(
                chapterLaunchButton(chapter),
                LinearLayout.LayoutParams(activity.dp(58), activity.dp(58)).apply {
                    rightMargin = activity.dp(14)
                },
            )
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(chapter.label, 30, CalmTheme.INK, Typeface.NORMAL).apply {
                        setSingleLine(true)
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, activity.dp(8), 0, 0)
                    })
                    addView(label(notificationSummary(chapter), 15, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                        setPadding(0, activity.dp(6), 0, 0)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun chapterLaunchButton(chapter: AppChapter): ImageButton {
        return ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = drawables.notificationCard(activity.dp(18), chapter.hueColor, true)
            contentDescription = "Open ${chapter.label}"
            tooltipText = "Open ${chapter.label}"
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            notificationCardDisplayCache.chapterMaskedIcon(chapter)?.let { icon ->
                setImageDrawable(icon.toSizedDrawable(activity, activity.dp(42)))
            }
            alpha = if (chapter.launchable) 0.96f else 0.36f
            isEnabled = chapter.launchable
            if (chapter.launchable) {
                setOnClickListener { openPackage(chapter) }
            }
        }
    }

    private fun notificationArea(chapter: AppChapter, tintCards: Boolean): View {
        val mediaControls = MediaNotificationControls.from(chapter.notifications)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            addView(
                stackToolbar(groupingIconButton(chapter)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)),
            )
            addView(
                notificationStack(chapter, tintCards),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            if (mediaControls.hasAnyAction) {
                addView(
                    mediaControlsRow(mediaControls),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = activity.dp(12)
                    },
                )
            }
        }
    }

    private fun notificationStack(chapter: AppChapter, tintCards: Boolean): View {
        if (chapter.notifications.isEmpty()) {
            return emptyPinnedChapterStack(chapter, tintCards)
        }
        val cards = NotificationCardGrouper.cards(
            chapter.notifications,
            settings.groupNotifications(chapter.identityKey),
        )
        return cardStackController.cardStack(
            cards.map { notificationCard(it, chapter, tintCards) },
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            CardStackStateKey.notifications(chapter),
        )
    }

    private fun emptyPinnedChapterStack(chapter: AppChapter, tintCards: Boolean): View {
        val cards = ArrayList<TextView>()
        cards.add(emptyPinnedChapterCard(chapter, tintCards))
        if (chapter.launchable) {
            cards.add(chapterAffordanceCard("Open ${chapter.label}", chapter, tintCards) {
                openPackage(chapter)
            })
        }
        notificationRepository.getAppShortcuts(chapter).take(MAX_EMPTY_CHAPTER_SHORTCUTS).forEach { shortcut ->
            cards.add(chapterAffordanceCard(shortcut.label, chapter, tintCards) {
                if (!notificationRepository.launchShortcut(shortcut)) {
                    Toast.makeText(activity, activity.getString(R.string.toast_shortcut_unavailable), Toast.LENGTH_SHORT).show()
                }
            })
        }
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            CardStackStateKey.notifications(chapter),
        )
    }

    private fun emptyPinnedChapterCard(chapter: AppChapter, tintCards: Boolean): TextView {
        return cardRenderer.stackCard(
            "${chapter.label}\nNo active notifications",
            chapter.hueColor,
            tintCards,
        ).apply {
            maxLines = 3
        }
    }

    private fun chapterAffordanceCard(
        title: String,
        chapter: AppChapter,
        tintCards: Boolean,
        action: () -> Unit,
    ): TextView {
        return cardRenderer.stackCard(
            title,
            chapter.hueColor,
            tintCards,
        ).apply {
            maxLines = 2
            setOnClickListener { action() }
        }
    }

    private fun notificationCard(
        item: NotificationCardItem,
        chapter: AppChapter,
        tintCards: Boolean,
    ): TextView {
        val data = notificationCardDisplayCache.getOrCreate(item, chapter, ::formatNotificationTime)
        return cardRenderer.stackCard(
            data.text,
            chapter.hueColor,
            tintCards,
            data.sideImage,
            data.sideImageAlpha,
            data.sideImageRenderKey,
        ).apply {
            maxLines = 4
            setOnClickListener {
                notificationActionController.openNotification(item.primary)
            }
            setOnLongClickListener {
                if (activePreferences().expandedCardsEnabled) {
                    focusOverlay.showExpandedCard(this, expandedNotificationContent(item), expandedNotificationActions(item, chapter))
                } else {
                    notificationActionController.showNotificationHideOptions(item, chapter)
                }
                true
            }
        }
    }

    private fun expandedNotificationActions(item: NotificationCardItem, chapter: AppChapter): List<ContextAction> {
        // Keep the long-press hide-options reachable now that long-press opens the expanded card.
        return contextActionFactory.notificationActions(item, chapter) +
            ContextAction(activity.getString(R.string.action_hide), Runnable { notificationActionController.showNotificationHideOptions(item, chapter) })
    }

    private fun expandedNotificationContent(item: NotificationCardItem): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(label(item.title(), 20, CalmTheme.INK, Typeface.BOLD).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(label(formatNotificationTime(item.primary.postTime), 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                setPadding(0, activity.dp(2), 0, 0)
            })
            item.notifications.firstNotNullOfOrNull { it.backgroundImage }?.let { media ->
                addView(expandedMediaImage(media), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(184)).apply {
                    topMargin = activity.dp(14)
                })
            }
            val body = item.fullText()
            if (body.isNotBlank()) {
                addView(label(body, 15, CalmTheme.INK, Typeface.NORMAL).apply {
                    maxLines = 10
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, activity.dp(14), 0, 0)
                })
            }
            val media = MediaNotificationControls.from(item.notifications)
            if (media.hasAnyAction) {
                addView(mediaControlsRow(media), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = activity.dp(16)
                })
            }
        }
    }

    private fun expandedMediaImage(media: Bitmap): ImageView {
        val radius = activity.dp(16).toFloat()
        return ImageView(activity).apply {
            setImageBitmap(media)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
    }

    private fun stackToolbar(action: View? = null): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            gravity = Gravity.END
            clipChildren = false
            clipToPadding = false
            action?.let {
                addView(it, LinearLayout.LayoutParams(activity.dp(38), activity.dp(30)))
            }
        }
    }

    private fun groupingIconButton(chapter: AppChapter): ImageButton {
        val grouped = settings.groupNotifications(chapter.identityKey)
        val description = activity.getString(
            if (grouped) R.string.notification_grouped_description else R.string.notification_split_description,
        )
        return ImageButton(activity).apply {
            setImageResource(if (grouped) R.drawable.ic_grouped_notifications else R.drawable.ic_split_notifications)
            setColorFilter(CalmTheme.INK)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            tooltipText = description
            alpha = 0.84f
            background = null
            setPadding(activity.dp(7), activity.dp(4), activity.dp(7), activity.dp(4))
            setOnClickListener { toggleNotificationGrouping(chapter) }
        }
    }

    private fun mediaControlsRow(controls: MediaNotificationControls): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipToPadding = false
            clipChildren = false
            addView(mediaControlButton(R.drawable.ic_media_previous, activity.getString(R.string.media_previous), controls.previous), LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply {
                rightMargin = activity.dp(8)
            })
            addView(mediaControlButton(playPauseIcon(controls), controls.playPauseLabel, controls.playPause), LinearLayout.LayoutParams(0, activity.dp(46), 1.35f).apply {
                leftMargin = activity.dp(4)
                rightMargin = activity.dp(4)
            })
            addView(mediaControlButton(R.drawable.ic_media_next, activity.getString(R.string.media_next), controls.next), LinearLayout.LayoutParams(0, activity.dp(46), 1f).apply {
                leftMargin = activity.dp(8)
            })
        }
    }

    private fun mediaControlButton(iconRes: Int, description: String, action: NotificationAction?): ImageButton {
        return ImageButton(activity).apply {
            setImageResource(iconRes)
            setColorFilter(if (action == null) CalmTheme.MUTED_INK else CalmTheme.INK)
            scaleType = ImageView.ScaleType.CENTER
            alpha = if (action == null) 0.34f else 0.92f
            isEnabled = action != null
            contentDescription = description
            tooltipText = description
            setPadding(activity.dp(11), activity.dp(11), activity.dp(11), activity.dp(11))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            if (action != null) {
                setOnClickListener { notificationActionController.performNotificationAction(action) }
            }
        }
    }

    private fun playPauseIcon(controls: MediaNotificationControls): Int {
        val label = controls.playPauseLabel.lowercase(Locale.ROOT)
        return if (label.contains("pause")) R.drawable.ic_media_pause else R.drawable.ic_media_play
    }

    private fun notificationSummary(chapter: AppChapter): String {
        val count = chapter.notifications.size
        return activity.resources.getQuantityString(R.plurals.notification_active_notes, count, count)
    }

    private fun formatNotificationTime(postTime: Long): String {
        return DateFormat.getTimeFormat(activity).format(Date(postTime))
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

    private companion object {
        const val MAX_EMPTY_CHAPTER_SHORTCUTS = 3
    }
}
