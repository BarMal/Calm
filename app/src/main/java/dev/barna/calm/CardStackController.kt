package dev.barna.calm

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.WeakHashMap

class CardStackController(
    private val context: Context,
    private val mainHandler: Handler,
    private val haptics: (android.view.View) -> Unit,
) {
    private val scrollMemory = CardStackScrollMemory(MAX_REMEMBERED_STACKS)
    private val layoutCache = CardStackLayoutCache(MAX_REMEMBERED_STACKS)
    private val activeStackKeys = WeakHashMap<ScrollView, String>()
    private val stackRuntimeStates = WeakHashMap<ScrollView, CardStackRuntimeState>()
    private val suppressedRestoreScrolls = WeakHashMap<ScrollView, Int>()

    fun cardStack(
        cards: List<TextView>,
        cardHeight: Int,
        cardStep: Int,
        tuning: CardStackTuning,
        stackKey: String = stackKey(cards),
    ): ScrollView {
        val scroller = ScrollView(context).apply {
            tag = CalmAnimationTags.CARD_STACK
            isFillViewport = true
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isClickable = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipToPadding = false
            clipChildren = false
        }
        activeStackKeys[scroller] = stackKey
        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
        }
        val tunedStep = tunedCardStep(cardStep, tuning)
        val minimumTopPadding = context.dp(6)
        val minimumBottomPadding = context.dp(32)
        val initialTopPadding = layoutCache.rememberedTopPadding(stackKey) ?: minimumTopPadding
        stack.setPadding(0, initialTopPadding, 0, minimumBottomPadding)
        scroller.addView(stack, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        appendCardsToStack(stack, 0, cards, cardHeight, tunedStep)

        val runtimeState = CardStackRuntimeState(
            tunedStep = tunedStep,
            tuning = tuning,
            lastHapticIndex = intArrayOf(-1),
            styledRange = intArrayOf(-1, -1),
        )
        runtimeState.magneticSnap = Runnable { magnetize(scroller, cards, tunedStep, tuning, stackKey) }
        stackRuntimeStates[scroller] = runtimeState
        scroller.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val suppressedRestore = suppressedRestoreScrolls.remove(scroller)
            if (suppressedRestore == null || suppressedRestore != scrollY) {
                scrollMemory.remember(stackKey, scrollY)
            }
            style(scroller, cards, tunedStep, tuning, true, runtimeState.lastHapticIndex, runtimeState.styledRange)
            runtimeState.magneticSnap?.let { magneticSnap ->
                mainHandler.removeCallbacks(magneticSnap)
                mainHandler.postDelayed(magneticSnap, tuning.magnetDelayMillis)
            }
        }
        var lastAppliedViewportHeight = -1
        val applyLayout: () -> Unit = {
            if (scroller.height > 0) {
                lastAppliedViewportHeight = scroller.height
                val stackTopPadding = CardStackLayout.activeTopPadding(
                    viewportHeight = scroller.height,
                    cardHeight = cardHeight,
                    minimumTopPadding = minimumTopPadding,
                    peakFraction = tuning.stackPeakFraction,
                )
                val trailingPadding = CardStackLayout.trailingPadding(
                    viewportHeight = scroller.height,
                    activeTopPadding = stackTopPadding,
                    cardHeight = cardHeight,
                    minimumBottomPadding = minimumBottomPadding,
                )
                layoutCache.remember(stackKey, stackTopPadding)
                stack.setPadding(0, stackTopPadding, 0, trailingPadding)
                // Apply the first style on the next frame via a one-shot pre-draw listener rather than a
                // layout-change listener: setPadding only triggers a layout pass (and onLayoutChange) when
                // the bounds actually change, so on page revisits where the cached top padding already
                // matches the computed value the listener would never fire — leaving every card stuck at
                // alpha 0 (and the entry animator dropping the stack) until the first manual scroll.
                // OnPreDrawListener runs after measure+layout regardless, so card positions are final and
                // style() is guaranteed to run promptly. invalidate() guarantees a draw traversal even when
                // setPadding was a no-op.
                stack.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        stack.viewTreeObserver.removeOnPreDrawListener(this)
                        val maxScroll = maxOf(0, (cards.lastOrNull()?.top ?: stackTopPadding) - stackTopPadding)
                        stack.minimumHeight = scroller.height + maxScroll
                        val restore = scrollMemory.initialRestore(stackKey, maxScroll)
                        if (restore.pendingTarget != null) suppressedRestoreScrolls[scroller] = restore.scrollY
                        if (restore.scrollY != scroller.scrollY) scroller.scrollTo(0, restore.scrollY)
                        style(scroller, cards, tunedStep, tuning, false, runtimeState.lastHapticIndex, runtimeState.styledRange)
                        scroller.post {
                            if (scroller.parent != null) {
                                style(scroller, cards, tunedStep, tuning, false, runtimeState.lastHapticIndex, runtimeState.styledRange)
                            }
                        }
                        return true
                    }
                })
                stack.invalidate()
            }
        }
        val viewportLayoutListener = object : android.view.View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: android.view.View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                val nextHeight = bottom - top
                val oldHeight = oldBottom - oldTop
                if (nextHeight > 0 && nextHeight != oldHeight && nextHeight != lastAppliedViewportHeight) {
                    applyLayout()
                    v.removeOnLayoutChangeListener(this)
                }
            }
        }
        scroller.addOnLayoutChangeListener(viewportLayoutListener)
        scroller.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit

            override fun onViewDetachedFromWindow(v: android.view.View) {
                v.removeOnLayoutChangeListener(viewportLayoutListener)
                v.removeOnAttachStateChangeListener(this)
            }
        })
        scroller.post {
            if (scroller.height > 0) {
                applyLayout()
            } else {
                scroller.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: android.view.View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                        if (v.height > 0) {
                            v.removeOnLayoutChangeListener(this)
                            applyLayout()
                        }
                    }
                })
            }
        }
        return scroller
    }

    fun appendCards(
        scroller: ScrollView,
        cards: MutableList<TextView>,
        newCards: List<TextView>,
        cardHeight: Int,
        cardStep: Int,
        tuning: CardStackTuning,
    ) {
        if (newCards.isEmpty()) return
        val stack = scroller.getChildAt(0) as? LinearLayout ?: return
        val startIndex = cards.size
        cards.addAll(newCards)
        appendCardsToStack(stack, startIndex, newCards, cardHeight, tunedCardStep(cardStep, tuning))
        updateStackHeight(scroller, stack, cards)
    }

    fun scrollToCard(scroller: ScrollView, cardIndex: Int, smooth: Boolean) {
        val stack = scroller.getChildAt(0) as? LinearLayout ?: return
        if (stack.childCount == 0) return
        val targetCard = stack.getChildAt(cardIndex.coerceIn(0, stack.childCount - 1)) ?: return
        val firstCard = stack.getChildAt(0) ?: return
        val lastCard = stack.getChildAt(stack.childCount - 1) ?: return
        val cards = (0 until stack.childCount).mapNotNull { index -> stack.getChildAt(index) as? TextView }
        val target = (targetCard.top - firstCard.top).coerceIn(0, maxOf(0, lastCard.top - firstCard.top))
        scrollMemory.remember(activeStackKeys[scroller] ?: stackKey(cards), target)
        if (smooth) {
            scroller.smoothScrollTo(0, target)
        } else {
            scroller.scrollTo(0, target)
        }
    }

    fun stopScroll(scroller: ScrollView) {
        stackRuntimeStates[scroller]?.magneticSnap?.let(mainHandler::removeCallbacks)
        scroller.fling(0)
        scroller.smoothScrollTo(0, scroller.scrollY)
    }

    fun hasPendingRestore(scroller: ScrollView): Boolean {
        val stackKey = activeStackKeys[scroller] ?: return false
        return scrollMemory.pendingRestoreTarget(stackKey) != null
    }

    fun restoreInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.toCardStackScrollSnapshot()?.let(scrollMemory::restore)
    }

    fun saveInstanceState(outState: Bundle) {
        activeStackKeys.forEach { (scroller, stackKey) ->
            scrollMemory.remember(stackKey, scroller.scrollY)
        }
        outState.putCardStackScrollSnapshot(scrollMemory.snapshot())
    }

    private fun tunedCardStep(baseStep: Int, tuning: CardStackTuning): Int {
        val minStep = context.dp(34)
        val maxStep = context.dp(88)
        val fromBase = (baseStep * (0.72f + (tuning.verticalSpacing / 100f) * 0.72f)).toInt()
        return fromBase.coerceIn(minStep, maxStep)
    }

    private fun appendCardsToStack(
        stack: LinearLayout,
        startIndex: Int,
        newCards: List<TextView>,
        cardHeight: Int,
        tunedStep: Int,
    ) {
        val stackOverlap = cardHeight - tunedStep
        newCards.forEachIndexed { offset, card ->
            val index = startIndex + offset
            card.tag = CalmAnimationTags.CARD
            card.alpha = 0f
            card.translationX = 0f
            card.translationY = 0f
            card.translationZ = 0f
            card.rotation = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardHeight)
            params.topMargin = if (index == 0) 0 else -stackOverlap
            stack.addView(card, params)
        }
    }

    private fun updateStackHeight(scroller: ScrollView, stack: LinearLayout, cards: List<TextView>) {
        stack.post {
            val firstCardTop = cards.firstOrNull()?.top ?: 0
            val maxScroll = maxOf(0, (cards.lastOrNull()?.top ?: firstCardTop) - firstCardTop)
            stack.minimumHeight = scroller.height + maxScroll
            val stackKey = activeStackKeys[scroller]
            val restored = stackKey?.let { key -> scrollMemory.restoreAfterBoundsExpanded(key, maxScroll) }
            if (restored != null) {
                suppressedRestoreScrolls[scroller] = restored
                if (restored != scroller.scrollY) scroller.scrollTo(0, restored)
            }
            stackRuntimeStates[scroller]?.let { state ->
                style(scroller, cards, state.tunedStep, state.tuning, false, state.lastHapticIndex, state.styledRange)
            }
        }
    }

    private fun style(
        scroller: ScrollView,
        cards: List<TextView>,
        cardStep: Int,
        tuning: CardStackTuning,
        allowHaptic: Boolean,
        lastHapticIndex: IntArray,
        styledRange: IntArray,
    ) {
        if (cards.isEmpty()) return
        val readingAnchor = cards.first().top
        val scrollY = clampedScroll(scroller, cards, readingAnchor)
        val threshold = scrollY + readingAnchor
        val activeIndex = ((threshold - readingAnchor) / cardStep.toFloat())
            .toInt()
            .coerceIn(0, cards.lastIndex)
        val visibleStart = (activeIndex - tuning.outgoingVisibleRange.toInt() - 2).coerceAtLeast(0)
        val visibleEnd = (activeIndex + tuning.visibleCards + 2).coerceAtMost(cards.lastIndex)

        if (styledRange[0] >= 0 && styledRange[1] >= styledRange[0]) {
            for (index in styledRange[0]..styledRange[1]) {
                if (index !in visibleStart..visibleEnd) {
                    hideInactive(cards[index])
                }
            }
        }
        styledRange[0] = visibleStart
        styledRange[1] = visibleEnd

        for (index in visibleStart..visibleEnd) {
            val card = cards[index]
            val visualDepth = (card.top - threshold) / cardStep.toFloat()
            val focusDistance = kotlin.math.abs(visualDepth)
            val scale = scale(visualDepth, tuning)
            card.pivotY = 0f
            card.translationZ = 120f - focusDistance
            card.translationX = horizontalOffset(visualDepth, tuning)
            val styledTranslationY = focusedCardGap(visualDepth, tuning)
            card.translationY = styledTranslationY
            card.setTag(CalmAnimationTags.STYLED_TRANSLATION_Y_TAG_KEY, styledTranslationY)
            card.rotation = horizontalRotation(visualDepth, tuning)
            card.scaleX = scale
            card.scaleY = scale
            card.alpha = alpha(visualDepth, tuning)
            card.setTextColor(textColor(visualDepth))
            card.isEnabled = card.alpha > 0.08f
        }

        if (lastHapticIndex[0] == -1) {
            lastHapticIndex[0] = activeIndex
        } else if (allowHaptic && lastHapticIndex[0] != activeIndex) {
            lastHapticIndex[0] = activeIndex
            haptics(scroller)
        }
    }

    private fun hideInactive(card: TextView) {
        card.alpha = 0f
        card.isEnabled = false
    }

    private fun clampedScroll(scroller: ScrollView, cards: List<TextView>, readingAnchor: Int): Int {
        val scrollY = scroller.scrollY
        val maxScroll = maxOf(0, cards.last().top - readingAnchor)
        val clamped = scrollY.coerceIn(0, maxScroll)
        if (clamped != scrollY) scroller.scrollTo(0, clamped)
        return clamped
    }

    private fun magnetize(
        scroller: ScrollView,
        cards: List<TextView>,
        cardStep: Int,
        tuning: CardStackTuning,
        stackKey: String,
    ) {
        if (cards.isEmpty() || cardStep <= 0) return
        val readingAnchor = cards.first().top
        val scrollY = clampedScroll(scroller, cards, readingAnchor)
        val maxScroll = maxOf(0, cards.last().top - readingAnchor)
        val target = (Math.round(scrollY / cardStep.toFloat()) * cardStep).coerceIn(0, maxScroll)
        val distance = kotlin.math.abs(target - scrollY)
        if (distance > magnetSnapThreshold(tuning) || distance < context.dp(1)) return
        scrollMemory.remember(stackKey, target)
        scroller.smoothScrollTo(0, target)
    }

    private fun stackKey(cards: List<TextView>): String {
        if (cards.isEmpty()) return "empty"
        return buildString {
            append(cards.size)
            append(':')
            cards.take(12).forEach { card ->
                append(card.text?.toString()?.lineSequence()?.firstOrNull().orEmpty())
                append('|')
            }
        }
    }

    private fun scale(visualDepth: Float, tuning: CardStackTuning): Float {
        val curve = tuning.curveFactor
        val topScale = tuning.focusedCardScaleFactor
        val firstDepthScale = CalmColor.lerp(1.0f, 0.93f, curve)
        val tailScale = CalmColor.lerp(0.97f, 0.84f, curve)
        if (visualDepth < 0f) return CalmColor.lerp(topScale, tailScale, CalmColor.clamp01(-visualDepth / tuning.outgoingVisibleRange))
        if (visualDepth <= 1f) return CalmColor.lerp(topScale, firstDepthScale, visualDepth)
        val tailDepth = CalmColor.clamp01((visualDepth - 1f) / maxOf(1f, tuning.visibleCards - 1f))
        return CalmColor.lerp(firstDepthScale, tailScale, tailDepth)
    }

    private fun focusedCardGap(visualDepth: Float, tuning: CardStackTuning): Float {
        if (visualDepth <= 0f || tuning.focusedCardGap == 0) return 0f
        val maxGap = context.dp(56) * tuning.focusedCardGapFactor
        return maxGap * CalmColor.clamp01(visualDepth)
    }

    private fun alpha(visualDepth: Float, tuning: CardStackTuning): Float {
        if (visualDepth < 0f) {
            return CalmColor.lerp(1f, 0f, CalmColor.clamp01(-visualDepth / tuning.outgoingVisibleRange))
        }
        val visibleTail = maxOf(1f, tuning.visibleCards - 1f)
        // The top card stays fully opaque; non-top cards fade toward dimAmount, scaled by the
        // configurable non-top opacity so the focused card can be emphasised more strongly.
        val dimAmount = CalmColor.lerp(0.72f, 0.44f, tuning.curveFactor) * tuning.nonTopCardOpacityFactor
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
        return context.dp(132) * tuning.horizontalCurveFactor * tuning.horizontalPathProgress(visualDepth)
    }

    private fun horizontalRotation(visualDepth: Float, tuning: CardStackTuning): Float {
        if (tuning.horizontalCurve == 0 || tuning.rotation == 0) return 0f
        return 9f *
            tuning.horizontalCurveFactor *
            tuning.rotationFactor *
            tuning.rotationProgress(visualDepth) *
            tuning.rotationDirection(visualDepth)
    }

    private fun magnetSnapThreshold(tuning: CardStackTuning): Int {
        return context.dp(CalmColor.lerp(32f, 96f, tuning.magnetStrengthFactor).toInt())
    }

    private fun textColor(visualDepth: Float): Int {
        val depth = CalmColor.clamp01(maxOf(0f, visualDepth) / 2f)
        return CalmColor.blend(CalmTheme.INK, android.graphics.Color.rgb(128, 124, 116), depth)
    }

    private companion object {
        const val MAX_REMEMBERED_STACKS = 48
        const val STATE_SCROLL_KEYS = "calm.cardStack.scroll.keys"
        const val STATE_SCROLL_VALUES = "calm.cardStack.scroll.values"
        const val STATE_PENDING_KEYS = "calm.cardStack.pending.keys"
        const val STATE_PENDING_VALUES = "calm.cardStack.pending.values"
    }

    private fun Bundle.toCardStackScrollSnapshot(): CardStackScrollSnapshot? {
        val remembered = mapFromState(STATE_SCROLL_KEYS, STATE_SCROLL_VALUES)
        val pending = mapFromState(STATE_PENDING_KEYS, STATE_PENDING_VALUES)
        if (remembered.isEmpty() && pending.isEmpty()) return null
        return CardStackScrollSnapshot(remembered, pending)
    }

    private fun Bundle.putCardStackScrollSnapshot(snapshot: CardStackScrollSnapshot) {
        putMapState(STATE_SCROLL_KEYS, STATE_SCROLL_VALUES, snapshot.rememberedScrollPositions)
        putMapState(STATE_PENDING_KEYS, STATE_PENDING_VALUES, snapshot.pendingRestoreTargets)
    }

    private fun Bundle.mapFromState(keysKey: String, valuesKey: String): LinkedHashMap<String, Int> {
        val keys = getStringArrayList(keysKey).orEmpty()
        val values = getIntArray(valuesKey) ?: IntArray(0)
        return linkedMapOf<String, Int>().apply {
            keys.take(values.size).forEachIndexed { index, key ->
                put(key, values[index])
            }
        }
    }

    private fun Bundle.putMapState(keysKey: String, valuesKey: String, values: LinkedHashMap<String, Int>) {
        putStringArrayList(keysKey, ArrayList(values.keys))
        putIntArray(valuesKey, values.values.toIntArray())
    }
}

private class CardStackRuntimeState(
    val tunedStep: Int,
    val tuning: CardStackTuning,
    val lastHapticIndex: IntArray,
    val styledRange: IntArray,
) {
    var magneticSnap: Runnable? = null
}
