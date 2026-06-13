package dev.barna.calm

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
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
    private val showContextMenu: (View, AppEntry, DockNotificationTarget?, Pair<Int, Int>) -> Unit,
    private val describeDock: (AppEntry, DockNotificationTarget?) -> String = { app, target ->
        defaultDockDescription(activity, app, target)
    },
    private val tapToOpenText: () -> String = { activity.getString(R.string.dock_card_tap_to_open) },
) {
    private val notificationResolver = DockNotificationResolver()
    private var featuredDockIdentityKey: String? = null
    private val featuredNotificationIndexes = HashMap<String, Int>()

    fun buildDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        return when (config.style) {
            DockStyle.CLASSIC -> dockShell(classicDockRow(apps, config, chapters), config)
            DockStyle.CARD -> cardDock(apps, config, chapters)
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
                val spacing = DockConfig.itemSpacingDp(config.horizontalPaddingDp)
                val target = notificationResolver.targetFor(app, chapters)
                addView(
                    dockItem(app, config, target),
                    LinearLayout.LayoutParams(activity.dp(width), activity.dp(DockConfig.CLASSIC_ITEM_HEIGHT_DP)).apply {
                        marginStart = activity.dp(spacing)
                        marginEnd = activity.dp(spacing)
                    },
                )
            }
        }
    }

    private fun cardDock(apps: List<AppEntry>, config: DockConfig, chapters: List<AppChapter>): View {
        return if (apps.isEmpty()) {
            FrameLayout(activity).apply { tag = CalmAnimationTags.CHROME }
        } else {
            val surface = FeaturedDockSurface(activity)
            var selectedIndex = selectedFeaturedIndex(apps)
            fun bind(index: Int, transition: DockTransition? = null) {
                selectedIndex = normalizedIndex(index, apps.size)
                rememberFeaturedDockApp(apps[selectedIndex])
                val rebind = { bindFeaturedDockStack(surface, apps, chapters, selectedIndex, includeClassicRow = false, config = config) }
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
                config = config,
                selectAppIndex = { index, transition ->
                    val newIndex = normalizedIndex(index, apps.size)
                    if (transition is DockTransition.Horizontal) {
                        resetFeaturedNotificationIndex(apps[newIndex], chapters, transition.direction)
                    }
                    bind(index, transition)
                },
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
        val surface = FeaturedDockSurface(activity)
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
            config = config,
            selectAppIndex = { index, transition ->
                val newIndex = normalizedIndex(index, apps.size)
                if (transition is DockTransition.Horizontal) {
                    resetFeaturedNotificationIndex(apps[newIndex], chapters, transition.direction)
                }
                bind(index, transition)
            },
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
        config: DockConfig,
    ) {
        if (apps.isEmpty()) return
        surface.removeAllViews()
        surface.tag = CalmAnimationTags.CHROME
        surface.clipChildren = false
        surface.clipToPadding = false
        surface.background = null
        val horizontalPadding = activity.dp(config.horizontalPaddingDp)
        val verticalPadding = activity.dp(config.verticalPaddingDp)
        surface.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        surface.minimumHeight = activity.dp(
            DockConfig.featuredDockHeightDp(includeClassicRow, config.verticalPaddingDp),
        )
        val selectedApp = apps[normalizedIndex(selectedIndex, apps.size)]
        val selectedTarget = targetFor(selectedApp, chapters)
        val stack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        stack.addView(
            tightDockStack(apps, chapters, selectedIndex),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(DockConfig.FEATURED_DOCK_CONTENT_HEIGHT_DP)),
        )
        if (includeClassicRow) {
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
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        surface.contentDescription = describeDock(selectedApp, selectedTarget)
        surface.tooltipText = selectedApp.label
        surface.installDockInteractions(selectedApp, selectedTarget, config)
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
            contentDescription = describeDock(app, target)
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
                                ?: tapToOpenText(),
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
            dockCard(app, target, config)
        } else {
            dockIcon(app, target, config)
        }
    }

    private fun dockIcon(app: AppEntry, target: DockNotificationTarget?, config: DockConfig): View {
        val button = ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
            contentDescription = describeDock(app, target)
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(14).toFloat()))
            }
            installDockInteractions(app, target, config)
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(button, FrameLayout.LayoutParams(activity.dp(56), activity.dp(56)))
            addNotificationBadge(target)
            installDockInteractions(app, target, config)
        }
    }

    private fun dockCard(app: AppEntry, target: DockNotificationTarget?, config: DockConfig): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(8), activity.dp(6), activity.dp(10), activity.dp(6))
            contentDescription = describeDock(app, target)
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
            installDockInteractions(app, target, config)
        }
        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addNotificationBadge(target)
            installDockInteractions(app, target, config)
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
            .ifBlank { tapToOpenText() }
    }

    private fun performDockAction(
        source: View,
        app: AppEntry,
        target: DockNotificationTarget?,
        action: DockInteractionAction,
        anchor: Pair<Int, Int> = source.centerOnScreen(),
    ) {
        when (action) {
            DockInteractionAction.OPEN_APP -> openAppEntry(app)
            DockInteractionAction.OPEN_NOTIFICATION -> openBestDockNotification(app, target)
            DockInteractionAction.OPEN_CONTEXT_MENU -> showContextMenu(source, app, target, anchor)
            DockInteractionAction.EXPAND -> {
                if (target != null) {
                    openNotificationPage(target.chapter)
                } else {
                    openAppEntry(app)
                }
            }
        }
    }

    private fun openBestDockNotification(app: AppEntry, target: DockNotificationTarget?) {
        val notification = selectedNotification(app, target)
        if (notification != null) {
            openNotification(notification)
        } else {
            openAppEntry(app)
        }
    }

    private fun View.installDockInteractions(app: AppEntry, target: DockNotificationTarget?, config: DockConfig) {
        setOnClickListener {
            performDockAction(this, app, target, config.tapAction)
        }
        setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            performDockAction(this, app, target, config.longPressAction)
            true
        }
        isLongClickable = true
    }

    private fun View.anchorOnScreen(event: MotionEvent): Pair<Int, Int> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return (location[0] + event.x.toInt()) to (location[1] + event.y.toInt())
    }

    private fun View.centerOnScreen(): Pair<Int, Int> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return (location[0] + width / 2) to (location[1] + height / 2)
    }

    private fun notificationCountChip(count: Int): TextView {
        return TextView(activity).apply {
            text = count.coerceAtMost(99).toString()
            setTextColor(CalmThemeColor.onNotificationBadge(activity))
            textSize = 11f
            typeface = Typeface.DEFAULT
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = activity.dp(999).toFloat()
                setColor(CalmThemeColor.notificationBadge(activity))
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
        config: DockConfig,
        selectAppIndex: (Int, DockTransition) -> Unit,
        cycleNotification: (DockTransition) -> Boolean,
    ) {
        if (apps.isEmpty()) return
        val swipeThreshold = max(activity.dp(28), ViewConfiguration.get(activity).scaledTouchSlop * 2)
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        var downX = 0f
        var downY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var gestureMoved = false
        var longPressed = false
        val longPressRunnable = Runnable {
            val app = apps.getOrNull(currentIndex()) ?: return@Runnable
            val target = targetFor(app, chapters)
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            performDockAction(
                this@installFeaturedDockGestures,
                app,
                target,
                config.longPressAction,
                downRawX.toInt() to downRawY.toInt(),
            )
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    gestureMoved = false
                    longPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.postDelayed(longPressRunnable, longPressTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (max(abs(dx), abs(dy)) >= swipeThreshold) {
                        gestureMoved = true
                        view.removeCallbacks(longPressRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (longPressed) return@setOnTouchListener true
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (max(abs(dx), abs(dy)) >= swipeThreshold) {
                        if (abs(dy) > abs(dx)) {
                            val direction = if (dy < 0) 1 else -1
                            val cycled = cycleNotification(DockTransition.Vertical(direction))
                            if (!cycled) {
                                selectAppIndex(
                                    DockGesturePolicy.nextAppIndex(currentIndex(), apps.size, direction),
                                    DockTransition.Vertical(direction),
                                )
                            }
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } else {
                            val direction = if (dx < 0) 1 else -1
                            selectAppIndex(
                                DockGesturePolicy.nextAppIndex(currentIndex(), apps.size, direction),
                                DockTransition.Horizontal(direction),
                            )
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } else if (!gestureMoved) {
                        val app = apps.getOrNull(currentIndex()) ?: return@setOnTouchListener true
                        performDockAction(
                            view,
                            app,
                            targetFor(app, chapters),
                            config.tapAction,
                            view.anchorOnScreen(event),
                        )
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
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
        val next = current + direction
        // Don't wrap — returning false at the boundary lets the caller navigate to the next app.
        if (next < 0 || next >= count) return false
        featuredNotificationIndexes[app.identityKey] = next
        return true
    }

    private fun resetFeaturedNotificationIndex(app: AppEntry, chapters: List<AppChapter>, direction: Int) {
        val target = targetFor(app, chapters) ?: return
        val count = target.chapter.notifications.size
        if (count <= 1) return
        // When arriving at an app via horizontal swipe, start at the near edge:
        // swiping right → show first (newest) notification; swiping left → show last.
        featuredNotificationIndexes[app.identityKey] = if (direction > 0) 0 else count - 1
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
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
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

    private fun FrameLayout.addNotificationBadge(target: DockNotificationTarget?) {
        val count = target?.summary?.count ?: return
        addView(
            TextView(activity).apply {
                text = count.coerceAtMost(99).toString()
                contentDescription = AccessibilityCopy.notificationBadgeDescription(count)
                setTextColor(CalmThemeColor.onNotificationBadge(activity))
                textSize = 10f
                typeface = Typeface.DEFAULT
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = activity.dp(999).toFloat()
                    setColor(CalmThemeColor.notificationBadge(activity))
                }
            },
            FrameLayout.LayoutParams(activity.dp(20), activity.dp(20), Gravity.TOP or Gravity.END).apply {
                topMargin = -activity.dp(2)
                marginEnd = -activity.dp(2)
            },
        )
    }

    private companion object {
        const val DOCK_STACK_VISIBLE_CARDS = 3
        const val DOCK_STACK_OFFSET_DP = 6
        const val DOCK_STACK_ANIMATION_MS = 160L

        fun defaultDockDescription(
            context: Context,
            app: AppEntry,
            target: DockNotificationTarget?,
        ): String {
            val count = target?.summary?.count ?: return app.label
            return context.resources.getQuantityString(
                R.plurals.dock_notification_count_description,
                count,
                app.label,
                count,
            )
        }
    }
}

private sealed class DockTransition(val direction: Int) {
    class Vertical(direction: Int) : DockTransition(direction)
    class Horizontal(direction: Int) : DockTransition(direction)
}

private class FeaturedDockSurface(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return true
    }
}
