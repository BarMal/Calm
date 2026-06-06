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
import java.util.EnumMap

class AppQuickScrollController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val drawables: CalmDrawables,
    private val cardStackController: CardStackController,
    private val haptics: (View) -> Unit,
    private val index: AppQuickScrollIndex = AppQuickScrollIndex(),
) {
    private val visibleByScope = EnumMap<AppLibraryScope, Boolean>(AppLibraryScope::class.java)

    fun attach(
        stackHost: FrameLayout,
        stack: ScrollView,
        model: AppLibraryPageModel,
        refresh: () -> Unit,
    ) {
        val quickScroll = index.create(model.apps)
        if (quickScroll.targets.size < 2) return
        val visible = visibleByScope[model.scope] ?: true
        val toggle = toggle(visible) {
            visibleByScope[model.scope] = !visible
            refresh()
        }
        stackHost.addView(toggle, FrameLayout.LayoutParams(activity.dp(48), activity.dp(40), Gravity.END or Gravity.TOP).apply {
            topMargin = activity.dp(2)
            rightMargin = activity.dp(4)
        })
        if (!visible) return

        val popup = popup()
        val rail = rail(quickScroll, stack, popup)
        stackHost.addView(rail, FrameLayout.LayoutParams(activity.dp(36), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            topMargin = activity.dp(48)
            bottomMargin = activity.dp(8)
            rightMargin = activity.dp(6)
        })
        stackHost.addView(popup, FrameLayout.LayoutParams(activity.dp(88), activity.dp(88), Gravity.CENTER))
    }

    private fun toggle(
        visible: Boolean,
        onToggle: () -> Unit,
    ): TextView {
        return label("A-Z", 12, CalmTheme.INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = drawables.glass(if (visible) CalmTheme.GLASS else CalmTheme.QUIET_GLASS, activity.dp(14))
            contentDescription = if (visible) "Hide app quick-scroll" else "Show app quick-scroll"
            tooltipText = contentDescription
            setOnClickListener { onToggle() }
        }
    }

    private fun rail(
        quickScroll: AppQuickScrollModel,
        stack: ScrollView,
        popup: TextView,
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
            cardStackController.scrollToCard(stack, target.cardIndex, smooth)
            popup.text = target.label
            popup.animate().cancel()
            popup.alpha = 1f
            popup.visibility = View.VISIBLE
            mainHandler.removeCallbacks(dismissPopup)
            if (lastTarget[0] != target) {
                lastTarget[0] = target
                haptics(stack)
            }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipToPadding = false
            clipChildren = false
            setPadding(0, activity.dp(8), 0, activity.dp(8))
            quickScroll.targets.forEach { target ->
                addView(label(target.label, 11, CalmTheme.INK, Typeface.BOLD).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Jump to ${target.label}"
                    setOnClickListener { activate(target, smooth = true) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(22)))
            }
        }

        return ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(14))
            clipToPadding = false
            clipChildren = false
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnTouchListener { view, event ->
                val contentHeight = content.height
                val contentY = event.y + (view as ScrollView).scrollY
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        activate(index.targetAt(quickScroll, contentHeight, contentY), smooth = event.actionMasked == MotionEvent.ACTION_DOWN)
                        false
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        activate(index.targetAt(quickScroll, contentHeight, contentY), smooth = false)
                        mainHandler.removeCallbacks(dismissPopup)
                        mainHandler.postDelayed(dismissPopup, POPUP_DISMISS_DELAY_MS)
                        false
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
