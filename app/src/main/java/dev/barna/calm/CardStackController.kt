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
    fun cardStack(cards: List<TextView>, cardHeight: Int, cardStep: Int, tuning: CardStackTuning): ScrollView {
        val scroller = ScrollView(activity).apply {
            tag = CalmAnimationTags.CARD_STACK
            isFillViewport = true
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isClickable = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipToPadding = false
            clipChildren = false
        }
        val stack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
        }
        val tunedStep = tunedCardStep(cardStep, tuning)
        val minimumTopPadding = activity.dp(6)
        val minimumBottomPadding = activity.dp(32)
        stack.setPadding(0, minimumTopPadding, 0, minimumBottomPadding)
        scroller.addView(stack, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val stackOverlap = cardHeight - tunedStep
        cards.forEachIndexed { index, card ->
            card.tag = CalmAnimationTags.CARD
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight)
            params.topMargin = if (index == 0) 0 else -stackOverlap
            stack.addView(card, params)
        }

        val lastHapticIndex = intArrayOf(-1)
        val magneticSnap = Runnable { magnetize(scroller, cards, tunedStep) }
        scroller.setOnScrollChangeListener { _, _, _, _, _ ->
            style(scroller, cards, tunedStep, tuning, true, lastHapticIndex)
            mainHandler.removeCallbacks(magneticSnap)
            mainHandler.postDelayed(magneticSnap, 90)
        }
        scroller.post {
            val viewportLocation = IntArray(2)
            scroller.getLocationOnScreen(viewportLocation)
            val targetTopOnScreen = (activity.window.decorView.height - cardHeight) / 2
            val stackTopPadding = CardStackLayout.activeTopPadding(
                viewportHeight = scroller.height,
                cardHeight = cardHeight,
                minimumTopPadding = minimumTopPadding,
                viewportTopOnScreen = viewportLocation[1],
                targetTopOnScreen = targetTopOnScreen,
            )
            val trailingPadding = CardStackLayout.trailingPadding(
                viewportHeight = scroller.height,
                activeTopPadding = stackTopPadding,
                cardHeight = cardHeight,
                minimumBottomPadding = minimumBottomPadding,
            )
            stack.setPadding(0, stackTopPadding, 0, trailingPadding)
            stack.post {
                val maxScroll = maxOf(0, (cards.lastOrNull()?.top ?: stackTopPadding) - stackTopPadding)
                stack.minimumHeight = scroller.height + maxScroll
                style(scroller, cards, tunedStep, tuning, false, lastHapticIndex)
            }
        }
        return scroller
    }

    private fun tunedCardStep(baseStep: Int, tuning: CardStackTuning): Int {
        val minStep = activity.dp(34)
        val maxStep = activity.dp(88)
        val fromBase = (baseStep * (0.72f + (tuning.verticalSpacing / 100f) * 0.72f)).toInt()
        return fromBase.coerceIn(minStep, maxStep)
    }

    private fun style(
        scroller: ScrollView,
        cards: List<TextView>,
        cardStep: Int,
        tuning: CardStackTuning,
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
            val focusDistance = kotlin.math.abs(visualDepth)
            val scale = scale(visualDepth, tuning)
            card.pivotY = 0f
            card.translationZ = 120f - focusDistance
            card.translationX = horizontalOffset(visualDepth, tuning)
            card.rotation = horizontalRotation(visualDepth, tuning)
            card.scaleX = scale
            card.scaleY = scale
            card.alpha = alpha(visualDepth, tuning)
            card.setTextColor(textColor(visualDepth))
            card.isEnabled = visualDepth >= -0.05f && visualDepth <= tuning.visibleCards - 0.95f
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

    private fun scale(visualDepth: Float, tuning: CardStackTuning): Float {
        val curve = tuning.curveFactor
        val topScale = 1.02f
        val firstDepthScale = CalmColor.lerp(1.0f, 0.93f, curve)
        val tailScale = CalmColor.lerp(0.97f, 0.84f, curve)
        if (visualDepth < 0f) return CalmColor.lerp(topScale, tailScale, CalmColor.clamp01(-visualDepth / tuning.outgoingVisibleRange))
        if (visualDepth <= 1f) return CalmColor.lerp(topScale, firstDepthScale, visualDepth)
        val tailDepth = CalmColor.clamp01((visualDepth - 1f) / maxOf(1f, tuning.visibleCards - 1f))
        return CalmColor.lerp(firstDepthScale, tailScale, tailDepth)
    }

    private fun alpha(visualDepth: Float, tuning: CardStackTuning): Float {
        if (visualDepth < 0f) {
            return CalmColor.lerp(1f, 0f, CalmColor.clamp01(-visualDepth / tuning.outgoingVisibleRange))
        }
        val visibleTail = maxOf(1f, tuning.visibleCards - 1f)
        val dimAmount = CalmColor.lerp(0.72f, 0.44f, tuning.curveFactor)
        if (visualDepth <= tuning.visibleCards - 1f) {
            return CalmColor.lerp(1f, dimAmount, visualDepth / visibleTail)
        }
        if (visualDepth <= tuning.visibleCards - 0.65f) {
            return CalmColor.lerp(dimAmount, 0f, (visualDepth - (tuning.visibleCards - 1f)) / 0.35f)
        }
        return 0f
    }

    private fun horizontalOffset(visualDepth: Float, tuning: CardStackTuning): Float {
        if (tuning.horizontalCurve == 0) return 0f
        return activity.dp(132) * tuning.horizontalCurveFactor * tuning.horizontalPathProgress(visualDepth)
    }

    private fun horizontalRotation(visualDepth: Float, tuning: CardStackTuning): Float {
        if (tuning.horizontalCurve == 0 || tuning.rotation == 0) return 0f
        return 9f *
            tuning.horizontalCurveFactor *
            tuning.rotationFactor *
            tuning.rotationProgress(visualDepth) *
            tuning.rotationDirection(visualDepth)
    }

    private fun textColor(visualDepth: Float): Int {
        val depth = CalmColor.clamp01(maxOf(0f, visualDepth) / 2f)
        return CalmColor.blend(CalmTheme.INK, android.graphics.Color.rgb(128, 124, 116), depth)
    }
}
