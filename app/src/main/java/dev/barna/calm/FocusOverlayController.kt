package dev.barna.calm

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.util.TypedValue
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
    private var focusedCardClone: TextView? = null
    private var focusedMenu: LinearLayout? = null
    private var focusedSourceCard: View? = null
    private var focusedStartBounds: CardBounds? = null
    private val displacedFocusViews = ArrayList<FocusDisplacement>()

    fun show(sourceCard: TextView, actions: List<ContextAction>, focusedText: String = sourceCard.text.toString()) {
        dismiss(false)
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
        focusedCardOverlay = null
        focusedCardClone = null
        focusedMenu = null
        focusedStartBounds = null

        val cleanup = Runnable {
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

    private fun contextActionButton(action: ContextAction): TextView {
        return labelFactory(action.label, 14, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = android.view.Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            setOnClickListener {
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
