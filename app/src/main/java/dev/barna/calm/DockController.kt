package dev.barna.calm

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    private val openNotification: (CalmNotificationListenerService.CalmNotification) -> Unit,
    private val openNotificationPage: (AppChapter) -> Unit,
) {
    private val notificationResolver = DockNotificationResolver()
    private var featuredDockIdentityKey: String? = null
    private val featuredNotificationIndexes = HashMap<String, Int>()

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
            fun bind(index: Int, transition: DockTransition? = null) {
                selectedIndex = normalizedIndex(index, apps.size)
                rememberFeaturedDockApp(apps[selectedIndex])
                val rebind = { bindFeaturedDockStack(surface, apps, chapters, selectedIndex, includeClassicRow = false) }
                if (transition == null) {
                    rebind()
                } else {
                    animateDockStack(surface, transition, rebind)
                }
            }
            bind(selectedIndex)
            surface.installFeaturedDockGestures(
                apps = apps,
                chapters = chapters,
                currentIndex = { selectedIndex },
                selectAppIndex = { index, transition -> bind(index, transition) },
                cycleNotification = { transition ->
                    if (cycleFeaturedNotification(apps, chapters, selectedIndex, transition.direction)) {
                        bind(selectedIndex, transition)
                        true
                    } else {
                        false
                    }
                },
            )
            surface
        }
    }

    private fun hybridDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        var selectedIndex = selectedFeaturedIndex(apps)
        val surface = FrameLayout(activity)
        fun bind(index: Int, transition: DockTransition? = null) {
            selectedIndex = normalizedIndex(index, apps.size)
            rememberFeaturedDockApp(apps[selectedIndex])
            val rebind = {
                bindFeaturedDockStack(surface, apps, chapters, selectedIndex, includeClassicRow = true, config = config)
            }
            if (transition == null) {
                rebind()
            } else {
                animateDockStack(surface, transition, rebind)
            }
        }
        bind(selectedIndex)
        surface.installFeaturedDockGestures(
            apps = apps,
            chapters = chapters,
            currentIndex = { selectedIndex },
            selectAppIndex = { index, transition -> bind(index, transition) },
            cycleNotification = { transition ->
                if (cycleFeaturedNotification(apps, chapters, selectedIndex, transition.direction)) {
                    bind(selectedIndex, transition)
                    true
                } else {
                    false
                }
            },
        )
        return surface
    }

    private fun bindFeaturedDockStack(
        surface: FrameLayout,
        apps: List<AppEntry>,
        chapters: List<AppChapter>,
        selectedIndex: Int,
        includeClassicRow: Boolean,
        config: DockConfig? = null,
    ) {
        if (apps.isEmpty()) return
        surface.removeAllViews()
        surface.tag = CalmAnimationTags.CHROME
        surface.clipChildren = false
        surface.clipToPadding = false
        surface.background = null
        surface.setPadding(0, 0, 0, 0)
        surface.minimumHeight = activity.dp(if (includeClassicRow) 142 else 90)
        val selectedApp = apps[normalizedIndex(selectedIndex, apps.size)]
        val selectedTarget = targetFor(selectedApp, chapters)
        val stack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        stack.addView(
            tightDockStack(apps, chapters, selectedIndex),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(90)),
        )
        if (includeClassicRow && config != null) {
            stack.addView(
                classicDockRow(apps, config, chapters),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = activity.dp(8)
                },
            )
        }
        surface.addView(
            stack,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        surface.contentDescription = dockDescription(selectedApp, selectedTarget)
        surface.tooltipText = selectedApp.label
        surface.setOnClickListener { openFeaturedDockItem(selectedApp, selectedTarget) }
        surface.installNotificationLongPress(selectedTarget)
    }

    private fun tightDockStack(apps: List<AppEntry>, chapters: List<AppChapter>, selectedIndex: Int): FrameLayout {
        val stack = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }
        val visibleCount = minOf(DOCK_STACK_VISIBLE_CARDS, apps.size)
        for (layer in (visibleCount - 1) downTo 0) {
            val app = apps[normalizedIndex(selectedIndex + layer, apps.size)]
            val target = targetFor(app, chapters)
            val card = featuredDockCard(app, target, front = layer == 0).apply {
                translationY = activity.dp(DOCK_STACK_OFFSET_DP * layer).toFloat()
                scaleX = 1f - (0.035f * layer)
                scaleY = 1f - (0.035f * layer)
                alpha = 1f - (0.17f * layer)
            }
            stack.addView(
                card,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(72), Gravity.TOP),
            )
        }
        return stack
    }

    private fun featuredDockCard(app: AppEntry, target: DockNotificationTarget?, front: Boolean): View {
        val selectedNotification = selectedNotification(app, target)
        val icon = resolveIcon(app)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawables.cardWithSideImage(
                activity.dp(22),
                target?.chapter?.hueColor ?: app.hueColor,
                true,
                icon,
            )
            setPadding(activity.dp(12), activity.dp(8), activity.dp(14), activity.dp(8))
            contentDescription = dockDescription(app, target)
            tooltipText = app.label
            icon?.let { bitmap ->
                addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(RoundedBitmapDrawable(bitmap, activity.dp(14).toFloat()))
                    },
                    LinearLayout.LayoutParams(activity.dp(50), activity.dp(50)).apply {
                        marginEnd = activity.dp(12)
                    },
                )
            }
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(dockText(app.label, if (front) 15 else 13, Typeface.BOLD, CalmTheme.INK, 1))
                    addView(
                        dockText(
                            selectedNotification?.let { notificationPreview(it) }
                                ?: notificationDetail(target)
                                ?: activity.getString(R.string.dock_card_tap_to_open),
                            12,
                            Typeface.NORMAL,
                            CalmTheme.MUTED_INK,
                            if (front) 2 else 1,
                        ).apply {
                            setPadding(0, activity.dp(4), 0, 0)
                        },
                    )
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            target?.summary?.count?.takeIf { it > 1 }?.let { count ->
                addView(notificationCountChip(count), LinearLayout.LayoutParams(activity.dp(28), activity.dp(24)))
            }
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

    private fun selectedNotification(
        app: AppEntry,
        target: DockNotificationTarget?,
    ): CalmNotificationListenerService.CalmNotification? {
        val notifications = target?.chapter?.notifications.orEmpty()
            .sortedByDescending { notification -> notification.postTime }
        if (notifications.isEmpty()) return null
        val index = normalizedIndex(featuredNotificationIndexes[app.identityKey] ?: 0, notifications.size)
        featuredNotificationIndexes[app.identityKey] = index
        return notifications[index]
    }

    private fun notificationPreview(notification: CalmNotificationListenerService.CalmNotification): String {
        return notification.bodyText()
            .ifBlank { notification.subText }
            .ifBlank { notification.title }
            .ifBlank { activity.getString(R.string.dock_card_tap_to_open) }
    }

    private fun openFeaturedDockItem(app: AppEntry, target: DockNotificationTarget?) {
        val notification = selectedNotification(app, target)
        if (notification != null) {
            openNotification(notification)
        } else {
            openAppEntry(app)
        }
    }

    private fun notificationCountChip(count: Int): TextView {
        return TextView(activity).apply {
            text = count.coerceAtMost(99).toString()
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = activity.dp(999).toFloat()
                setColor(Color.rgb(196, 57, 72))
            }
        }
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
        selectAppIndex: (Int, DockTransition) -> Unit,
        cycleNotification: (DockTransition) -> Boolean,
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
                    selectAppIndex(currentIndex() + direction, DockTransition.Vertical(direction))
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    true
                } else {
                    val direction = if (dx < 0) 1 else -1
                    val cycled = cycleNotification(DockTransition.Horizontal(direction))
                    if (cycled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    cycled
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

    private fun cycleFeaturedNotification(
        apps: List<AppEntry>,
        chapters: List<AppChapter>,
        selectedIndex: Int,
        direction: Int,
    ): Boolean {
        val app = apps.getOrNull(selectedIndex) ?: return false
        val target = targetFor(app, chapters) ?: return false
        val count = target.chapter.notifications.size
        if (count <= 1) return false
        val current = featuredNotificationIndexes[app.identityKey] ?: 0
        featuredNotificationIndexes[app.identityKey] = normalizedIndex(current + direction, count)
        return true
    }

    private fun animateDockStack(surface: FrameLayout, transition: DockTransition, rebind: () -> Unit) {
        if (surface.childCount == 0 || surface.width == 0) {
            rebind()
            return
        }
        val stack = surface.getChildAt(0)
        val distance = if (transition is DockTransition.Horizontal) surface.width * 0.42f else activity.dp(48).toFloat()
        stack.animate()
            .alpha(0f)
            .translationX(if (transition is DockTransition.Horizontal) -transition.direction * distance else 0f)
            .translationY(if (transition is DockTransition.Vertical) -transition.direction * distance else 0f)
            .setDuration(DOCK_STACK_ANIMATION_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    stack.animate().setListener(null)
                    rebind()
                    val next = surface.getChildAt(0) ?: return
                    next.alpha = 0f
                    next.translationX = if (transition is DockTransition.Horizontal) transition.direction * activity.dp(34).toFloat() else 0f
                    next.translationY = if (transition is DockTransition.Vertical) transition.direction * activity.dp(28).toFloat() else 0f
                    next.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(DOCK_STACK_ANIMATION_MS)
                        .setListener(null)
                        .start()
                }
            })
            .start()
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

    private companion object {
        const val DOCK_STACK_VISIBLE_CARDS = 3
        const val DOCK_STACK_OFFSET_DP = 6
        const val DOCK_STACK_ANIMATION_MS = 160L
    }
}

private sealed class DockTransition(val direction: Int) {
    class Vertical(direction: Int) : DockTransition(direction)
    class Horizontal(direction: Int) : DockTransition(direction)
}
