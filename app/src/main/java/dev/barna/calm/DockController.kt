package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/** Renders the persistent dock: a cross-page surface for favourite app entries. */
class DockController(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
    private val openNotificationPage: (AppChapter) -> Unit,
) {
    private val notificationResolver = DockNotificationResolver()
    private var featuredDockIdentityKey: String? = null

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
        return if (apps.isEmpty()) {
            FrameLayout(activity).apply { tag = CalmAnimationTags.CHROME }
        } else {
            val surface = FrameLayout(activity)
            var selectedIndex = selectedFeaturedIndex(apps)
            fun bind(index: Int) {
                selectedIndex = normalizedIndex(index, apps.size)
                rememberFeaturedDockApp(apps[selectedIndex])
                bindFeaturedDockSurface(surface, apps[selectedIndex], targetFor(apps[selectedIndex], chapters), activity.dp(72))
            }
            bind(selectedIndex)
            surface.installFeaturedDockGestures(
                apps = apps,
                chapters = chapters,
                currentIndex = { selectedIndex },
                selectIndex = { bind(it) },
            )
            surface
        }
    }

    private fun hybridDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        var selectedIndex = selectedFeaturedIndex(apps)
        val surface = FrameLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(22))
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(10))
        }
        fun bind(index: Int) {
            selectedIndex = normalizedIndex(index, apps.size)
            val featuredApp = apps.getOrNull(selectedIndex)
            val featuredTarget = featuredApp?.let { targetFor(it, chapters) }
            surface.removeAllViews()
            surface.addView(
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
            featuredApp?.let { app ->
                rememberFeaturedDockApp(app)
                surface.contentDescription = dockDescription(app, featuredTarget)
                surface.tooltipText = app.label
                surface.setOnClickListener { openAppEntry(app) }
            } ?: run {
                surface.contentDescription = null
                surface.tooltipText = null
                surface.setOnClickListener(null)
            }
            surface.addNotificationBadge(featuredTarget)
            surface.installNotificationLongPress(featuredTarget)
        }
        bind(selectedIndex)
        surface.installFeaturedDockGestures(
            apps = apps,
            chapters = chapters,
            currentIndex = { selectedIndex },
            selectIndex = { bind(it) },
        )
        return surface
    }

    private fun bindFeaturedDockSurface(surface: FrameLayout, app: AppEntry, target: DockNotificationTarget?, minimumHeight: Int) {
        surface.removeAllViews()
        surface.tag = CalmAnimationTags.CHROME
        surface.clipChildren = false
        surface.clipToPadding = false
        surface.background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(22))
        surface.setPadding(activity.dp(12), activity.dp(8), activity.dp(14), activity.dp(8))
        surface.minimumHeight = minimumHeight
        surface.contentDescription = dockDescription(app, target)
        surface.tooltipText = app.label
        surface.addView(
            featuredDockContent(app, target),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        surface.addNotificationBadge(target)
        surface.setOnClickListener { openAppEntry(app) }
        surface.installNotificationLongPress(target)
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
                    addView(
                        dockText(
                            notificationDetail(target) ?: activity.getString(R.string.dock_card_tap_to_open),
                            12,
                            Typeface.NORMAL,
                            CalmTheme.MUTED_INK,
                            1,
                        ).apply {
                            setPadding(0, activity.dp(4), 0, 0)
                        },
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
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

    private fun targetFor(app: AppEntry, chapters: List<AppChapter>): DockNotificationTarget? {
        return notificationResolver.targetFor(app, chapters)
    }

    private fun selectedFeaturedIndex(apps: List<AppEntry>): Int {
        if (apps.isEmpty()) return 0
        val remembered = featuredDockIdentityKey
        val rememberedIndex = apps.indexOfFirst { app -> app.identityKey == remembered }
        if (rememberedIndex >= 0) return rememberedIndex
        return 0
    }

    private fun rememberFeaturedDockApp(app: AppEntry) {
        featuredDockIdentityKey = app.identityKey
    }

    private fun normalizedIndex(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return ((index % size) + size) % size
    }

    private fun View.installFeaturedDockGestures(
        apps: List<AppEntry>,
        chapters: List<AppChapter>,
        currentIndex: () -> Int,
        selectIndex: (Int) -> Unit,
    ) {
        if (apps.isEmpty()) return
        val swipeThreshold = max(activity.dp(28), ViewConfiguration.get(activity).scaledTouchSlop * 2)
        val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                this@installFeaturedDockGestures.performClick()
                return true
            }

            override fun onLongPress(event: MotionEvent) {
                apps.getOrNull(currentIndex())
                    ?.let { app -> targetFor(app, chapters) }
                    ?.let { target -> openNotificationPage(target.chapter) }
            }

            override fun onFling(
                start: MotionEvent?,
                end: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val first = start ?: return false
                val dx = end.x - first.x
                val dy = end.y - first.y
                if (max(abs(dx), abs(dy)) < swipeThreshold) return false
                return if (abs(dy) > abs(dx)) {
                    val direction = if (dy < 0) 1 else -1
                    selectIndex(currentIndex() + direction)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    true
                } else {
                    val direction = if (dx < 0) 1 else -1
                    val navigated = navigateDockNotification(apps, chapters, currentIndex(), direction, selectIndex)
                    if (navigated) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    navigated
                }
            }
        })
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            detector.onTouchEvent(event)
            true
        }
    }

    private fun navigateDockNotification(
        apps: List<AppEntry>,
        chapters: List<AppChapter>,
        startIndex: Int,
        direction: Int,
        selectIndex: (Int) -> Unit,
    ): Boolean {
        if (apps.isEmpty()) return false
        for (offset in 1..apps.size) {
            val index = normalizedIndex(startIndex + (direction * offset), apps.size)
            val target = targetFor(apps[index], chapters) ?: continue
            selectIndex(index)
            openNotificationPage(target.chapter)
            return true
        }
        return false
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
        if (target == null) {
            setOnLongClickListener(null)
            isLongClickable = false
            return
        }
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
        return activity.resources.getQuantityString(
            R.plurals.dock_notification_count_description,
            count,
            app.label,
            count,
        )
    }
}
