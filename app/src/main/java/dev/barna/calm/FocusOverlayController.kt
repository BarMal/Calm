package dev.barna.calm

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FocusOverlayController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val drawables: CalmDrawables,
    private val labelFactory: (String, Int, Int, Int) -> TextView,
    private val currentScreen: () -> View?,
    private val focusBlurRadius: () -> Int,
) {
    private var focusedCardOverlay: FrameLayout? = null
    private var focusedCardClone: View? = null
    private var focusedMenu: LinearLayout? = null
    private var focusedSourceCard: View? = null
    private var focusedStartBounds: CardBounds? = null
    private var focusedExpanded = false
    private val displacedFocusViews = ArrayList<FocusDisplacement>()

    fun show(sourceCard: TextView, actions: List<ContextAction>, focusedText: String = sourceCard.text.toString()) {
        dismiss(false)
        sourceCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        focusedExpanded = false
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val startBounds = sourceBoundsInContent(content, sourceCard)
        val targetBounds = CardBounds(
            left = ((content.width - sourceCard.width) / 2).coerceAtLeast(activity.dp(18)),
            top = ((content.height - sourceCard.height) / 2).coerceAtLeast(activity.dp(24)),
            width = sourceCard.width,
            height = sourceCard.height,
        )

        focusedSourceCard = sourceCard
        focusedStartBounds = startBounds
        animatePageElementsAway(sourceCard, true)
        applyBackdropFocus()

        val overlay = FrameLayout(activity).apply {
            alpha = 0f
            setBackgroundColor(Color.argb(104, 0, 0, 0))
            setOnClickListener { dismiss(true) }
        }
        focusedCardOverlay = overlay
        content.addView(overlay, matchParentParams())

        val focusedCard = cloneFocusedCard(sourceCard, focusedText).apply {
            alpha = 0.98f
            elevation = sourceCard.elevation + activity.dp(10)
            translationZ = sourceCard.translationZ + activity.dp(16)
            setOnClickListener { }
        }
        focusedCardClone = focusedCard
        focusedCard.x = startBounds.left.toFloat()
        focusedCard.y = startBounds.top.toFloat()
        overlay.addView(focusedCard, FrameLayout.LayoutParams(startBounds.width, startBounds.height))

        val menu = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            alpha = 0f
            translationY = -activity.dp(18).toFloat()
            setOnClickListener { }
        }
        focusedMenu = menu
        overlay.addView(menu, menuLayoutParams(targetBounds))
        actions.forEach { menu.addView(contextActionButton(it)) }

        overlay.animate().alpha(1f).setDuration(170).start()
        focusedCard.animate()
            .x(targetBounds.left.toFloat())
            .y(targetBounds.top.toFloat())
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { animateMenuIn(menu) }
            .start()
    }

    /**
     * Grows the [sourceCard] into a large themed card holding [content] (rich, card-type specific)
     * with the [actions] embedded as buttons. The card animates out from the source card's actual
     * on-screen bounds.
     */
    fun showExpandedCard(sourceCard: View, content: View, actions: List<ContextAction>) {
        dismiss(false)
        sourceCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val container = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        if (container.width <= 0 || container.height <= 0) return
        val startBounds = sourceBoundsInContent(container, sourceCard)

        focusedSourceCard = sourceCard
        focusedStartBounds = startBounds
        focusedExpanded = true
        animatePageElementsAway(sourceCard, true)
        applyBackdropFocus()

        val overlay = FrameLayout(activity).apply {
            alpha = 0f
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setOnClickListener { dismiss(true) }
        }
        focusedCardOverlay = overlay
        container.addView(overlay, matchParentParams())

        val horizontalPadding = activity.dp(22)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(horizontalPadding, activity.dp(22), horizontalPadding, activity.dp(18))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(actionsGrid(actions), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = activity.dp(18)
            })
        }
        // Scroll the body so tall content (e.g. media + long text + many actions) never clips the actions.
        val card = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            background = sourceCard.background?.constantState?.newDrawable()?.mutate()
                ?: drawables.glass(CalmTheme.GLASS, activity.dp(24))
            elevation = sourceCard.elevation + activity.dp(12)
            addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { }
        }
        focusedCardClone = card

        val margin = activity.dp(18)
        val targetWidth = (container.width - margin * 2).coerceAtLeast(startBounds.width)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.AT_MOST),
        )
        val maxHeight = (container.height - activity.dp(40)).coerceAtLeast(activity.dp(1))
        val targetHeight = card.measuredHeight.coerceIn(startBounds.height.coerceAtMost(maxHeight), maxHeight)
        val targetLeft = margin
        val targetTop = ((container.height - targetHeight) / 2).coerceAtLeast(activity.dp(24))
        overlay.addView(card, FrameLayout.LayoutParams(targetWidth, targetHeight).apply {
            leftMargin = targetLeft
            topMargin = targetTop
        })

        card.pivotX = 0f
        card.pivotY = 0f
        card.translationX = (startBounds.left - targetLeft).toFloat()
        card.translationY = (startBounds.top - targetTop).toFloat()
        card.scaleX = startBounds.width.toFloat() / targetWidth
        card.scaleY = startBounds.height.toFloat() / targetHeight
        card.alpha = 0.4f

        overlay.animate().alpha(1f).setDuration(170).start()
        card.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun isShowing(): Boolean = focusedCardOverlay != null

    fun dismiss(animate: Boolean) {
        dismissWithAction(animate, removeFocusedCard = false, afterDismiss = null)
    }

    private fun dismissWithAction(
        animate: Boolean,
        removeFocusedCard: Boolean,
        afterDismiss: Runnable?,
    ) {
        val overlay = focusedCardOverlay
        if (overlay == null) {
            resetBackdrop(animate, restoreSourceCard = true)
            afterDismiss?.run()
            return
        }

        val focusedCard = focusedCardClone
        val menu = focusedMenu
        val startBounds = focusedStartBounds
        val expanded = focusedExpanded
        focusedCardOverlay = null
        focusedCardClone = null
        focusedMenu = null
        focusedStartBounds = null
        focusedExpanded = false

        val cleanup = Runnable {
            releaseOverlayDrawables(overlay)
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            resetBackdrop(animate, restoreSourceCard = !removeFocusedCard)
            focusedSourceCard = null
            afterDismiss?.run()
        }

        if (!animate) {
            cleanup.run()
            return
        }

        animateMenuOut(menu)
        overlay.animate().alpha(0f).setDuration(190).setStartDelay(90).start()
        when {
            focusedCard != null && expanded -> {
                // Reverse the grow: shrink the expanded card back toward the source card's bounds.
                val targetWidth = focusedCard.width.coerceAtLeast(1)
                val targetHeight = focusedCard.height.coerceAtLeast(1)
                val animator = focusedCard.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction(cleanup)
                if (startBounds != null) {
                    animator
                        .translationX((startBounds.left - focusedCard.left).toFloat())
                        .translationY((startBounds.top - focusedCard.top).toFloat())
                        .scaleX(startBounds.width.toFloat() / targetWidth)
                        .scaleY(startBounds.height.toFloat() / targetHeight)
                }
                animator.start()
            }
            focusedCard != null && startBounds != null && !removeFocusedCard -> {
                focusedCard.animate()
                    .x(startBounds.left.toFloat())
                    .y(startBounds.top.toFloat())
                    .setStartDelay(55)
                    .setDuration(230)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction(cleanup)
                    .start()
            }
            focusedCard != null && removeFocusedCard -> {
                focusedCard.animate()
                    .alpha(0f)
                    .translationY(focusedCard.translationY - activity.dp(42))
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setStartDelay(55)
                    .setDuration(210)
                    .withEndAction(cleanup)
                    .start()
            }
            else -> mainHandler.postDelayed(cleanup, 230)
        }
    }

    private fun applyBackdropFocus() {
        currentScreen()?.let { screen ->
            val blur = focusBlurRadius().coerceIn(0, 24).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0f) {
                screen.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
            }
            screen.animate().alpha(0.72f).setDuration(180).start()
        }
    }

    private fun resetBackdrop(animate: Boolean, restoreSourceCard: Boolean) {
        currentScreen()?.let { screen ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) screen.setRenderEffect(null)
            if (animate) {
                screen.animate().alpha(1f).setDuration(150).start()
            } else {
                screen.alpha = 1f
            }
        }
        animatePageElementsAway(null, false, restoreSourceCard)
    }

    private fun animatePageElementsAway(sourceCard: View?, away: Boolean, restoreSourceCard: Boolean = true) {
        if (!away) {
            displacedFocusViews.forEach { displacement ->
                if (!restoreSourceCard && displacement.view === focusedSourceCard) return@forEach
                displacement.view.animate()
                    .translationY(displacement.translationY)
                    .alpha(displacement.alpha)
                    .setDuration(170)
                    .start()
            }
            displacedFocusViews.clear()
            return
        }
        if (sourceCard == null) return
        val page = findPageContainer(sourceCard) ?: return
        val sourceLocation = IntArray(2)
        sourceCard.getLocationOnScreen(sourceLocation)
        val sourceCenter = sourceLocation[1] + sourceCard.height / 2
        displacedFocusViews.clear()
        animateStackCardsAway(sourceCard, sourceCenter)

        for (index in 0 until page.childCount) {
            val child = page.getChildAt(index)
            if (containsView(child, sourceCard)) continue
            val childLocation = IntArray(2)
            child.getLocationOnScreen(childLocation)
            val childCenter = childLocation[1] + child.height / 2
            val direction = if (childCenter < sourceCenter) -1 else 1
            displacedFocusViews.add(FocusDisplacement(child))
            child.animate()
                .translationY((direction * activity.dp(34)).toFloat())
                .alpha(0.48f)
                .setDuration(210)
                .start()
        }
    }

    private fun animateStackCardsAway(sourceCard: View, sourceCenter: Int) {
        val stack = findCardStackContent(sourceCard)
        if (stack == null) {
            displacedFocusViews.add(FocusDisplacement(sourceCard))
            sourceCard.alpha = 0f
            return
        }
        for (index in 0 until stack.childCount) {
            val card = stack.getChildAt(index)
            displacedFocusViews.add(FocusDisplacement(card))
            if (card === sourceCard) {
                card.alpha = 0f
                continue
            }
            val cardLocation = IntArray(2)
            card.getLocationOnScreen(cardLocation)
            val cardCenter = cardLocation[1] + card.height / 2
            val direction = if (cardCenter < sourceCenter) -1 else 1
            card.animate()
                .translationY(card.translationY + direction * activity.dp(72))
                .alpha(minOf(card.alpha, 0.18f))
                .setDuration(190)
                .start()
        }
    }

    private fun cloneFocusedCard(sourceCard: TextView, focusedText: String): TextView {
        return TextView(activity).apply {
            text = focusedText
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sourceCard.textSize)
            setTextColor(sourceCard.currentTextColor)
            typeface = sourceCard.typeface
            gravity = sourceCard.gravity
            includeFontPadding = sourceCard.includeFontPadding
            setLineSpacing(sourceCard.lineSpacingExtra, sourceCard.lineSpacingMultiplier)
            setPadding(sourceCard.paddingLeft, sourceCard.paddingTop, sourceCard.paddingRight, sourceCard.paddingBottom)
            maxLines = sourceCard.maxLines
            ellipsize = sourceCard.ellipsize ?: TextUtils.TruncateAt.END
            background = sourceCard.background?.constantState?.newDrawable()?.mutate()
                ?: drawables.glass(CalmTheme.GLASS, activity.dp(20))
            compoundDrawablePadding = sourceCard.compoundDrawablePadding
            val relativeDrawables = sourceCard.compoundDrawablesRelative
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                relativeDrawables[0],
                relativeDrawables[1],
                relativeDrawables[2],
                relativeDrawables[3],
            )
        }
    }

    private fun releaseOverlayDrawables(view: View) {
        view.animate().cancel()
        view.background = null
        if (view is TextView) {
            view.setCompoundDrawables(null, null, null, null)
            view.setCompoundDrawablesRelative(null, null, null, null)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                releaseOverlayDrawables(view.getChildAt(index))
            }
        }
    }

    private fun animateMenuIn(menu: LinearLayout) {
        menu.alpha = 1f
        menu.animate().translationY(0f).setDuration(170).start()
        for (index in 0 until menu.childCount) {
            val child = menu.getChildAt(index)
            child.alpha = 0f
            child.translationY = -activity.dp(16).toFloat()
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 36L)
                .setDuration(180)
                .start()
        }
    }

    private fun animateMenuOut(menu: LinearLayout?) {
        if (menu == null) return
        for (index in menu.childCount - 1 downTo 0) {
            val child = menu.getChildAt(index)
            val order = menu.childCount - 1 - index
            child.animate()
                .alpha(0f)
                .translationY(-activity.dp(14).toFloat())
                .setStartDelay(order * 26L)
                .setDuration(130)
                .start()
        }
        menu.animate().alpha(0f).translationY(-activity.dp(18).toFloat()).setDuration(160).start()
    }

    private fun findPageContainer(source: View): LinearLayout? {
        var cursor: View? = source
        while (cursor != null) {
            val parent = cursor.parent
            if (cursor is LinearLayout && parent is FrameLayout) return cursor
            cursor = parent as? View
        }
        return null
    }

    private fun findCardStackContent(source: View): LinearLayout? {
        var cursor: View? = source
        while (cursor != null) {
            val parent = cursor.parent
            if (cursor is LinearLayout && parent is ScrollView) return cursor
            cursor = parent as? View
        }
        return null
    }

    private fun containsView(container: View, target: View): Boolean {
        if (container === target) return true
        if (container !is ViewGroup) return false
        for (index in 0 until container.childCount) {
            if (containsView(container.getChildAt(index), target)) return true
        }
        return false
    }

    /** Lays the expanded-card actions out in a two-column grid (a lone trailing action spans full width). */
    private fun actionsGrid(actions: List<ContextAction>): LinearLayout {
        val gap = activity.dp(4)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            actions.chunked(2).forEach { rowActions ->
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        clipChildren = false
                        clipToPadding = false
                        rowActions.forEachIndexed { index, action ->
                            val isFirst = index == 0
                            val isLast = index == rowActions.lastIndex
                            addView(
                                contextActionButton(action).apply {
                                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                        setMargins(if (isFirst) 0 else gap, gap, if (isLast) 0 else gap, gap)
                                    }
                                },
                            )
                        }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            }
        }
    }

    private fun contextActionButton(action: ContextAction): TextView {
        return labelFactory(action.label, 14, CalmTheme.INK, Typeface.BOLD).apply {
            contentDescription = action.label
            gravity = android.view.Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismissWithAction(
                    animate = true,
                    removeFocusedCard = action.closeBehavior == ContextActionCloseBehavior.REMOVE_CARD,
                    afterDismiss = action.action,
                )
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, activity.dp(4), 0, activity.dp(4))
            }
        }
    }

    private fun sourceBoundsInContent(content: View, sourceCard: View): CardBounds {
        val contentLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        content.getLocationOnScreen(contentLocation)
        sourceCard.getLocationOnScreen(sourceLocation)
        return CardBounds(
            left = sourceLocation[0] - contentLocation[0],
            top = sourceLocation[1] - contentLocation[1],
            width = sourceCard.width,
            height = sourceCard.height,
        )
    }

    private fun menuLayoutParams(targetBounds: CardBounds): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(targetBounds.width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = targetBounds.left
            topMargin = targetBounds.top + targetBounds.height + activity.dp(14)
        }
    }

    private data class CardBounds(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )
}
