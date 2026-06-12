package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Renders the persistent dock: a cross-page surface for favourite app entries. */
class DockController(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val openNotificationPage: (AppChapter) -> Unit,
) {
    private val notificationResolver = DockNotificationResolver()

    fun buildDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        return when (config.style) {
            DockStyle.CLASSIC -> dockShell(classicDockRow(apps, config, chapters), config)
            DockStyle.CARD -> cardDock(apps, chapters)
            DockStyle.HYBRID -> hybridDock(apps, config, chapters)
        }
    }

    private fun classicDockRow(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            apps.take(config.itemCount).forEach { app ->
                val width = DockConfig.itemWidthDp(config.itemSpan)
                val target = notificationResolver.targetFor(app, chapters)
                addView(
                    dockItem(app, config, target),
                    LinearLayout.LayoutParams(activity.dp(width), activity.dp(56)).apply {
                        marginStart = activity.dp(6)
                        marginEnd = activity.dp(6)
                    },
                )
            }
        }
    }

    private fun cardDock(apps: List<AppEntry>, chapters: List<AppChapter>): View {
        val app = apps.firstOrNull()
        return if (app == null) {
            FrameLayout(activity).apply { tag = CalmAnimationTags.CHROME }
        } else {
            featuredDockSurface(app, notificationResolver.targetFor(app, chapters), activity.dp(72))
        }
    }

    private fun hybridDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        val featuredApp = apps.firstOrNull()
        val featuredTarget = featuredApp?.let { notificationResolver.targetFor(it, chapters) }
        return FrameLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(22))
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(10))
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    clipChildren = false
                    clipToPadding = false
                    featuredApp?.let { app ->
                        addView(
                            featuredDockContent(app, featuredTarget),
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(56)).apply {
                                bottomMargin = activity.dp(8)
                            },
                        )
                    }
                    addView(
                        classicDockRow(apps, config, chapters),
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
            )
            addNotificationBadge(featuredTarget)
            installNotificationLongPress(featuredTarget)
        }
    }

    private fun featuredDockSurface(app: AppEntry, target: DockNotificationTarget?, minimumHeight: Int): View {
        return FrameLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(22))
            setPadding(activity.dp(12), activity.dp(8), activity.dp(14), activity.dp(8))
            this.minimumHeight = minimumHeight
            addView(
                featuredDockContent(app, target),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
            )
            addNotificationBadge(target)
            setOnClickListener { openAppEntry(app) }
            installNotificationLongPress(target)
        }
    }

    private fun featuredDockContent(app: AppEntry, target: DockNotificationTarget?): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = dockDescription(app, target)
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(14).toFloat()))
                    },
                    LinearLayout.LayoutParams(activity.dp(50), activity.dp(50)).apply {
                        marginEnd = activity.dp(12)
                    },
                )
            }
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(dockText(app.label, 15, Typeface.BOLD, CalmTheme.INK, 1))
                    addView(dockText(notificationDetail(target) ?: "Tap to open", 12, Typeface.NORMAL, CalmTheme.MUTED_INK, 1).apply {
                        setPadding(0, activity.dp(4), 0, 0)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            setOnClickListener { openAppEntry(app) }
            installNotificationLongPress(target)
        }
    }

    private fun dockShell(content: View, config: DockConfig): View {
        return HorizontalScrollView(activity).apply {
            tag = CalmAnimationTags.CHROME
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.GLASS, activity.dp(28))
            val horizontal = activity.dp(config.horizontalPaddingDp)
            val vertical = activity.dp(config.verticalPaddingDp)
            setPadding(horizontal, vertical, horizontal, vertical)
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dockItem(app: AppEntry, config: DockConfig, target: DockNotificationTarget?): View {
        return if (DockConfig.showsItemLabels(config.itemSpan)) {
            dockCard(app, target)
        } else {
            dockIcon(app, target)
        }
    }

    private fun dockIcon(app: AppEntry, target: DockNotificationTarget?): View {
        val button = ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
            contentDescription = dockDescription(app, target)
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(14).toFloat()))
            }
            setOnClickListener { openAppEntry(app) }
            installNotificationLongPress(target)
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(button, FrameLayout.LayoutParams(activity.dp(56), activity.dp(56)))
            addNotificationBadge(target)
            installNotificationLongPress(target)
        }
    }

    private fun dockCard(app: AppEntry, target: DockNotificationTarget?): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(8), activity.dp(6), activity.dp(10), activity.dp(6))
            contentDescription = dockDescription(app, target)
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(12).toFloat()))
                    },
                    LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)).apply {
                        marginEnd = activity.dp(7)
                    },
                )
            }
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(dockText(app.label, 13, Typeface.BOLD, CalmTheme.INK, 1))
                    notificationDetail(target)?.let { detail ->
                        addView(dockText(detail, 11, Typeface.NORMAL, CalmTheme.MUTED_INK, 1).apply {
                            setPadding(0, activity.dp(2), 0, 0)
                        })
                    }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            setOnClickListener { openAppEntry(app) }
            installNotificationLongPress(target)
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addNotificationBadge(target)
            installNotificationLongPress(target)
        }
    }

    private fun notificationDetail(target: DockNotificationTarget?): String? {
        val summary = target?.summary ?: return null
        return summary.latestText.ifBlank { summary.latestTitle }.takeIf { it.isNotBlank() }
    }

    private fun dockText(textValue: String, sp: Int, style: Int, color: Int, maxLineCount: Int): TextView {
        return TextView(activity).apply {
            text = textValue
            setTextColor(color)
            textSize = sp.toFloat()
            typeface = Typeface.DEFAULT
            setTypeface(typeface, style)
            maxLines = maxLineCount
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
    }

    private fun View.installNotificationLongPress(target: DockNotificationTarget?) {
        if (target == null) return
        setOnLongClickListener {
            openNotificationPage(target.chapter)
            true
        }
    }

    private fun FrameLayout.addNotificationBadge(target: DockNotificationTarget?) {
        val count = target?.summary?.count ?: return
        addView(
            TextView(activity).apply {
                text = count.coerceAtMost(99).toString()
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = activity.dp(999).toFloat()
                    setColor(Color.rgb(196, 57, 72))
                }
            },
            FrameLayout.LayoutParams(activity.dp(20), activity.dp(20), Gravity.TOP or Gravity.END).apply {
                topMargin = -activity.dp(2)
                marginEnd = -activity.dp(2)
            },
        )
    }

    private fun dockDescription(app: AppEntry, target: DockNotificationTarget?): String {
        val count = target?.summary?.count ?: return app.label
        return "${app.label}, $count ${if (count == 1) "notification" else "notifications"}"
    }
}
