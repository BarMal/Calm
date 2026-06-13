package dev.barna.calm

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.viewpager2.widget.ViewPager2

class LauncherEntryAnimator(private val activity: MainActivity) {
    fun animateCurrentPage(pager: ViewPager2, direction: Int = 0) {
        val recycler = pager.recyclerViewOrNull() ?: return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            if (recycler.getChildAdapterPosition(child) == pager.currentItem) {
                child.post { animatePageEntryWhenReady(child, 0, direction) }
                return
            }
        }
    }

    private fun animatePageEntryWhenReady(page: View, attempt: Int, direction: Int) {
        if (!page.isAttachedToWindow) return
        val panelViews = ArrayList<View>()
        val chromeViews = ArrayList<View>()
        val cardStacks = ArrayList<View>()
        collectAnimatedViews(page, panelViews, chromeViews, cardStacks)
        val waitingStacks = cardStacks.filter(::isCardStackWaitingForFirstStyle)
        if (waitingStacks.isNotEmpty() && attempt < MAX_ENTRY_READY_RETRIES) {
            page.postDelayed({ animatePageEntryWhenReady(page, attempt + 1, direction) }, ENTRY_READY_RETRY_DELAY_MS)
            return
        }
        animatePageEntry(panelViews, chromeViews, cardStacks - waitingStacks.toSet(), direction)
    }

    fun animatePageExit(pager: ViewPager2, pageIndex: Int) {
        val recycler = pager.recyclerViewOrNull() ?: return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            if (recycler.getChildAdapterPosition(child) == pageIndex) {
                animateCardsOut(child)
                return
            }
        }
    }

    private fun animateCardsOut(page: View) {
        val visibleCards = ArrayList<View>()
        collectVisibleCards(page, visibleCards)
        if (visibleCards.isEmpty()) return
        val count = visibleCards.size
        val perCardDelay = (CARD_EXIT_WINDOW_MS / count).coerceAtMost(CARD_EXIT_MAX_DELAY_MS)
        // Reverse: bottom card (highest index = "first in") exits first, top card exits last
        visibleCards.reversed().forEachIndexed { exitOrder, card ->
            card.animate().cancel()
            card.animate()
                .alpha(0f)
                .translationY(card.translationY - activity.dp(CARD_EXIT_TRANSLATE_DP))
                .setStartDelay(exitOrder * perCardDelay)
                .setDuration(CARD_EXIT_DURATION_MS)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
    }

    private fun collectVisibleCards(root: View, result: MutableList<View>) {
        if (root.tag == CalmAnimationTags.CARD_STACK) {
            val content = (root as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return
            for (index in 0 until content.childCount) {
                val card = content.getChildAt(index)
                if (card.tag == CalmAnimationTags.CARD && card.alpha > 0.01f) {
                    result.add(card)
                }
            }
            return
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                collectVisibleCards(root.getChildAt(index), result)
            }
        }
    }

    fun animateCurrentPageRemoval(pager: ViewPager2, afterRemoval: () -> Unit) {
        val recycler = pager.recyclerViewOrNull()
        if (recycler == null) {
            afterRemoval()
            return
        }
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            if (recycler.getChildAdapterPosition(child) == pager.currentItem) {
                animatePageRemoval(child, afterRemoval)
                return
            }
        }
        afterRemoval()
    }

    private fun animatePageEntry(panelViews: List<View>, chromeViews: List<View>, cardStacks: List<View>, direction: Int) {
        panelViews.forEach { animatePanelIntoView(it) }
        chromeViews.forEachIndexed { index, view -> animateChromeIntoView(view, index) }
        cardStacks.forEach { stack -> animateCardStackIntoView(stack, direction) }
    }

    private fun collectAnimatedViews(root: View, panelViews: MutableList<View>, chromeViews: MutableList<View>, cardStacks: MutableList<View>) {
        when (root.tag) {
            CalmAnimationTags.PAGE_PANEL -> panelViews.add(root)
            CalmAnimationTags.CHROME -> chromeViews.add(root)
            CalmAnimationTags.CARD_STACK -> cardStacks.add(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                collectAnimatedViews(root.getChildAt(index), panelViews, chromeViews, cardStacks)
            }
        }
    }

    private fun animatePanelIntoView(view: View) {
        val bg = view.background ?: return
        bg.alpha = 0
        ValueAnimator.ofInt(0, 255).apply {
            duration = 220L
            startDelay = 60L
            interpolator = DecelerateInterpolator()
            addUpdateListener { bg.alpha = animatedValue as Int }
            start()
        }
    }

    private fun animateChromeIntoView(view: View, index: Int) {
        val targetAlpha = view.alpha.takeIf { it > 0f } ?: 1f
        val targetY = view.translationY
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = targetY + activity.dp(8)
        view.animate()
            .alpha(targetAlpha)
            .translationY(targetY)
            .setStartDelay((index * 18L).coerceAtMost(90L))
            .setDuration(170L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animateCardStackIntoView(stackView: View, direction: Int) {
        val content = (stackView as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return

        data class CardAnimParams(
            val card: View,
            val animIndex: Int,
            val startTranslationX: Float,
            val startTranslationY: Float,
            val targetAlpha: Float,
            val targetTranslationX: Float,
            val targetTranslationY: Float,
        )

        val params = mutableListOf<CardAnimParams>()
        var animatedCount = 0
        for (index in 0 until content.childCount) {
            val card = content.getChildAt(index)
            if (card.tag != CalmAnimationTags.CARD) continue
            if (animatedCount >= MAX_ENTRY_ANIMATED_CARDS) break

            val currentTranslationY = card.translationY
            // Capture the styled horizontal-curve X that style() just set. The entry animation must
            // land cards on this X (not 0), otherwise the curve is flattened until the first scroll
            // re-runs style().
            val styledTranslationX = card.translationX
            val styledTranslationY = card.getTag(CalmAnimationTags.STYLED_TRANSLATION_Y_TAG_KEY) as? Float
            val targetAlpha = CardEntryAnimationLogic.entryTargetAlpha(card.alpha, currentTranslationY, styledTranslationY)
            val targetY = CardEntryAnimationLogic.entryTargetTranslationY(
                currentTranslationY = currentTranslationY,
                exitTranslateOffset = activity.dp(CARD_EXIT_TRANSLATE_DP).toFloat(),
                styledTranslationY = styledTranslationY,
            )

            if (direction < 0) {
                // Backward: slide in from the left into the styled (curved) X with alpha fade
                params.add(CardAnimParams(
                    card = card,
                    animIndex = animatedCount,
                    startTranslationX = styledTranslationX - activity.dp(CARD_SIDE_ENTRY_TRANSLATE_DP).toFloat(),
                    startTranslationY = targetY,
                    targetAlpha = targetAlpha,
                    targetTranslationX = styledTranslationX,
                    targetTranslationY = targetY,
                ))
            } else {
                // Forward or initial: fly in from below into the styled (curved) X
                params.add(CardAnimParams(
                    card = card,
                    animIndex = animatedCount,
                    startTranslationX = styledTranslationX,
                    startTranslationY = targetY + activity.dp(132 + (animatedCount * 18)),
                    targetAlpha = targetAlpha,
                    targetTranslationX = styledTranslationX,
                    targetTranslationY = targetY,
                ))
            }
            animatedCount++
        }

        // Atomically set all cards to their start positions before any animation begins,
        // preventing a flash where style()-visible cards briefly show then disappear.
        params.forEach { p ->
            p.card.animate().cancel()
            p.card.alpha = 0f
            p.card.translationX = p.startTranslationX
            p.card.translationY = p.startTranslationY
        }

        val duration = if (direction < 0) CARD_SIDE_ENTRY_DURATION_MS else CARD_ENTRY_DURATION_MS
        params.forEach { p ->
            p.card.animate()
                .alpha(p.targetAlpha)
                .translationX(p.targetTranslationX)
                .translationY(p.targetTranslationY)
                .setStartDelay(80L + (p.animIndex * 46L))
                .setDuration(duration)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun isCardStackWaitingForFirstStyle(stackView: View): Boolean {
        val content = (stackView as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return false
        val cardStates = (0 until content.childCount).map { index ->
            val card = content.getChildAt(index)
            CardEntryAnimationLogic.CardState(
                isCard = card.tag == CalmAnimationTags.CARD,
                alpha = card.alpha,
                translationZ = card.translationZ,
            )
        }
        return CardEntryAnimationLogic.isStackWaitingForFirstStyle(cardStates)
    }

    private fun animatePageRemoval(page: View, afterRemoval: () -> Unit) {
        val targetY = page.translationY
        page.animate().cancel()
        page.animate()
            .alpha(0f)
            .translationY(targetY - activity.dp(96))
            .setDuration(180L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                page.alpha = 1f
                page.translationY = targetY
                afterRemoval()
            }
            .start()
    }

    private companion object {
        const val MAX_ENTRY_ANIMATED_CARDS = 8
        const val MAX_ENTRY_READY_RETRIES = 6
        const val ENTRY_READY_RETRY_DELAY_MS = 32L
        const val CARD_EXIT_WINDOW_MS = 180L
        const val CARD_EXIT_MAX_DELAY_MS = 56L
        const val CARD_EXIT_TRANSLATE_DP = 80
        const val CARD_EXIT_DURATION_MS = 150L
        const val CARD_ENTRY_DURATION_MS = 390L
        const val CARD_SIDE_ENTRY_TRANSLATE_DP = 48
        const val CARD_SIDE_ENTRY_DURATION_MS = 300L
    }
}
