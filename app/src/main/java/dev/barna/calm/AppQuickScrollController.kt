package dev.barna.calm

import android.graphics.Typeface
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AppQuickScrollController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val drawables: CalmDrawables,
    private val cardStackController: CardStackController,
    private val haptics: (View) -> Unit,
    private val index: AppQuickScrollIndex = AppQuickScrollIndex(),
) {
    fun attach(
        stackHost: FrameLayout,
        stack: ScrollView,
        model: AppLibraryPageModel,
        tuning: CardStackTuning,
        ensureCardRendered: (Int) -> Unit = {},
    ) {
        val quickScroll = index.create(model.apps)
        if (quickScroll.targets.size < 2) return

        val popup = popup()
        val rail = rail(quickScroll, stack, popup, tuning, ensureCardRendered)
        stackHost.addView(rail, FrameLayout.LayoutParams(activity.dp(36), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            topMargin = activity.dp(8)
            bottomMargin = activity.dp(8)
            rightMargin = activity.dp(6)
        })
        stackHost.addView(popup, FrameLayout.LayoutParams(activity.dp(88), activity.dp(88), Gravity.CENTER))
    }

    private fun rail(
        quickScroll: AppQuickScrollModel,
        stack: ScrollView,
        popup: TextView,
        tuning: CardStackTuning,
        ensureCardRendered: (Int) -> Unit,
    ): View {
        val dismissPopup = Runnable {
            popup.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction { popup.visibility = View.GONE }
                .start()
        }
        val lastTarget = arrayOf<AppQuickScrollTarget?>(null)
        fun activate(target: AppQuickScrollTarget?, smooth: Boolean) {
            if (target == null) return
            if (lastTarget[0] == target) return
            lastTarget[0] = target
            cardStackController.stopScroll(stack)
            ensureCardRendered(target.cardIndex)
            cardStackController.scrollToCard(stack, target.cardIndex, smooth)
            popup.text = target.label
            popup.animate().cancel()
            popup.alpha = 1f
            popup.visibility = View.VISIBLE
            mainHandler.removeCallbacks(dismissPopup)
            haptics(stack)
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            clipToPadding = false
            clipChildren = false
            val lastIndex = quickScroll.targets.lastIndex.coerceAtLeast(1)
            quickScroll.targets.forEachIndexed { targetIndex, target ->
                val visualDepth = (targetIndex / lastIndex.toFloat()) * maxOf(1, tuning.visibleCards - 1)
                addView(label(target.label, 11, CalmTheme.INK, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Jump to ${target.label}"
                    translationX = -activity.dp(20) * tuning.horizontalCurveFactor * tuning.horizontalPathProgress(visualDepth)
                    rotation = -6f * tuning.rotationFactor * tuning.rotationProgress(visualDepth)
                    isClickable = false
                    isFocusable = false
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        activate(index.targetAt(quickScroll, view.height, event.y), smooth = false)
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        activate(index.targetAt(quickScroll, view.height, event.y), smooth = false)
                        mainHandler.removeCallbacks(dismissPopup)
                        mainHandler.postDelayed(dismissPopup, POPUP_DISMISS_DELAY_MS)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun popup(): TextView {
        return label("", 34, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = drawables.glass(CalmTheme.GLASS, activity.dp(28))
            alpha = 0f
            visibility = View.GONE
            elevation = activity.dp(6).toFloat()
        }
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
        const val POPUP_DISMISS_DELAY_MS = 520L
    }
}
