package dev.barna.calm

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FocusOverlayController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val drawables: CalmDrawables,
    private val labelFactory: (String, Int, Int, Int) -> TextView,
    private val currentScreen: () -> View?,
) {
    private var focusedCardOverlay: FrameLayout? = null
    private val displacedFocusViews = ArrayList<FocusDisplacement>()

    fun show(sourceCard: TextView, actions: List<ContextAction>) {
        dismiss(false)
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        animatePageElementsAway(sourceCard, true)
        currentScreen()?.let { screen ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                screen.setRenderEffect(RenderEffect.createBlurEffect(activity.dp(7).toFloat(), activity.dp(7).toFloat(), Shader.TileMode.CLAMP))
            }
            screen.animate().alpha(0.72f).setDuration(180).start()
        }

        val overlay = FrameLayout(activity).apply {
            alpha = 0f
            setBackgroundColor(Color.argb(104, 0, 0, 0))
            setOnClickListener { dismiss(true) }
        }
        focusedCardOverlay = overlay
        content.addView(overlay, matchParentParams())

        val focusColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(18), 0, activity.dp(18), 0)
            translationY = activity.dp(26).toFloat()
            setOnClickListener { }
        }
        overlay.addView(
            focusColumn,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )

        val focusedCard = labelFactory(sourceCard.text.toString(), 17, CalmTheme.INK, Typeface.NORMAL).apply {
            setLineSpacing(activity.dp(3).toFloat(), 1.0f)
            setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(18))
            maxLines = 8
            ellipsize = TextUtils.TruncateAt.END
            val state = sourceCard.background?.constantState
            background = state?.newDrawable()?.mutate() ?: drawables.glass(CalmTheme.GLASS, activity.dp(20))
            elevation = activity.dp(10).toFloat()
        }
        focusColumn.addView(focusedCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val menu = GridLayout(activity).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        val menuParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = activity.dp(14)
        }
        focusColumn.addView(menu, menuParams)
        actions.forEach { menu.addView(contextActionButton(it)) }

        overlay.animate().alpha(1f).setDuration(170).start()
        focusColumn.animate().translationY(0f).setDuration(220).start()
    }

    fun dismiss(animate: Boolean) {
        val overlay = focusedCardOverlay
        if (overlay == null) {
            resetBackdrop(animate)
            return
        }
        focusedCardOverlay = null
        val cleanup = Runnable {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            resetBackdrop(animate)
        }
        if (animate) {
            overlay.animate().alpha(0f).setDuration(150).withEndAction(cleanup).start()
        } else {
            cleanup.run()
        }
    }

    private fun resetBackdrop(animate: Boolean) {
        currentScreen()?.let { screen ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) screen.setRenderEffect(null)
            if (animate) {
                screen.animate().alpha(1f).setDuration(150).start()
            } else {
                screen.alpha = 1f
            }
        }
        animatePageElementsAway(null, false)
    }

    private fun animatePageElementsAway(sourceCard: View?, away: Boolean) {
        if (!away) {
            displacedFocusViews.forEach { displacement ->
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
            sourceCard.animate().alpha(0f).setDuration(120).start()
            return
        }
        for (index in 0 until stack.childCount) {
            val card = stack.getChildAt(index)
            displacedFocusViews.add(FocusDisplacement(card))
            if (card === sourceCard) {
                card.animate().alpha(0f).setDuration(120).start()
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
            gravity = Gravity.CENTER
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(999))
            setOnClickListener {
                dismiss(true)
                mainHandler.postDelayed(action.action, 170)
            }
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
            }
        }
    }
}
