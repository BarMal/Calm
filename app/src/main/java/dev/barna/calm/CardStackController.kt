package dev.barna.calm

import android.os.Handler
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CardStackController(
    private val activity: MainActivity,
    private val mainHandler: Handler,
    private val haptics: (android.view.View) -> Unit,
) {
    fun cardStack(cards: List<TextView>, cardHeight: Int, cardStep: Int): ScrollView {
        val scroller = ScrollView(activity).apply {
            isFillViewport = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipToPadding = false
            clipChildren = false
        }
        val stack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
        }
        val stackTopPadding = activity.dp(6)
        val minimumBottomPadding = activity.dp(32)
        stack.setPadding(0, stackTopPadding, 0, minimumBottomPadding)
        scroller.addView(stack, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stackOverlap = cardHeight - cardStep
        cards.forEachIndexed { index, card ->
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight)
            params.topMargin = if (index == 0) 0 else -stackOverlap
            stack.addView(card, params)
        }

        val lastHapticIndex = intArrayOf(-1)
        val magneticSnap = Runnable { magnetize(scroller, cards, cardStep) }
        scroller.setOnScrollChangeListener { _, _, _, _, _ ->
            style(scroller, cards, cardStep, true, lastHapticIndex)
            mainHandler.removeCallbacks(magneticSnap)
            mainHandler.postDelayed(magneticSnap, 90)
        }
        scroller.post {
            val trailingPadding = maxOf(minimumBottomPadding, scroller.height - cardHeight + activity.dp(36))
            stack.setPadding(0, stackTopPadding, 0, trailingPadding)
            style(scroller, cards, cardStep, false, lastHapticIndex)
        }
        return scroller
    }

    private fun style(
        scroller: ScrollView,
        cards: List<TextView>,
        cardStep: Int,
        allowHaptic: Boolean,
        lastHapticIndex: IntArray,
    ) {
        if (cards.isEmpty()) return
        val readingAnchor = cards.first().top
        val scrollY = clampedScroll(scroller, cards, readingAnchor)
        var activeIndex = 0
        val threshold = scrollY + readingAnchor
        cards.forEachIndexed { index, card ->
            if (card.top <= threshold) activeIndex = index
        }

        cards.forEach { card ->
            val visualDepth = (card.top - threshold) / cardStep.toFloat()
            val scale = scale(visualDepth)
            card.pivotY = 0f
            card.translationZ = if (visualDepth < 0f) 0f else 100f - visualDepth
            card.scaleX = scale
            card.scaleY = scale
            card.alpha = alpha(visualDepth)
            card.setTextColor(textColor(visualDepth))
            card.isEnabled = visualDepth >= -0.05f && visualDepth <= 2.05f
        }

        if (lastHapticIndex[0] == -1) {
            lastHapticIndex[0] = activeIndex
        } else if (allowHaptic && lastHapticIndex[0] != activeIndex) {
            lastHapticIndex[0] = activeIndex
            haptics(scroller)
        }
    }

    private fun clampedScroll(scroller: ScrollView, cards: List<TextView>, readingAnchor: Int): Int {
        val scrollY = scroller.scrollY
        val maxScroll = maxOf(0, cards.last().top - readingAnchor)
        val clamped = scrollY.coerceIn(0, maxScroll)
        if (clamped != scrollY) scroller.scrollTo(0, clamped)
        return clamped
    }

    private fun magnetize(scroller: ScrollView, cards: List<TextView>, cardStep: Int) {
        if (cards.isEmpty() || cardStep <= 0) return
        val readingAnchor = cards.first().top
        val scrollY = clampedScroll(scroller, cards, readingAnchor)
        val maxScroll = maxOf(0, cards.last().top - readingAnchor)
        val target = (Math.round(scrollY / cardStep.toFloat()) * cardStep).coerceIn(0, maxScroll)
        val distance = kotlin.math.abs(target - scrollY)
        if (distance > activity.dp(42) || distance < activity.dp(1)) return
        scroller.smoothScrollTo(0, target)
    }

    private fun scale(visualDepth: Float): Float {
        if (visualDepth < 0f) return CalmColor.lerp(1.02f, 0.96f, CalmColor.clamp01(-visualDepth))
        if (visualDepth <= 1f) return CalmColor.lerp(1.02f, 0.96f, visualDepth)
        if (visualDepth <= 2f) return CalmColor.lerp(0.96f, 0.90f, visualDepth - 1f)
        return 0.88f
    }

    private fun alpha(visualDepth: Float): Float {
        if (visualDepth < 0f) return CalmColor.clamp01(1f + visualDepth)
        if (visualDepth <= 2f) return CalmColor.lerp(1f, 0.56f, visualDepth / 2f)
        if (visualDepth <= 2.35f) return CalmColor.lerp(0.56f, 0f, (visualDepth - 2f) / 0.35f)
        return 0f
    }

    private fun textColor(visualDepth: Float): Int {
        val depth = CalmColor.clamp01(maxOf(0f, visualDepth) / 2f)
        return CalmColor.blend(CalmTheme.INK, android.graphics.Color.rgb(128, 124, 116), depth)
    }
}
