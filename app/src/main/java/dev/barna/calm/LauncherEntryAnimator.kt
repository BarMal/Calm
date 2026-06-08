package dev.barna.calm

import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class LauncherEntryAnimator(private val activity: MainActivity) {
    fun animateCurrentPage(pager: ViewPager2) {
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        for (index in 0 until recycler.childCount) {
            val child = recycler.getChildAt(index)
            if (recycler.getChildAdapterPosition(child) == pager.currentItem) {
                child.postDelayed({ animatePageEntryWhenReady(child, 0) }, 35L)
                return
            }
        }
    }

    private fun animatePageEntryWhenReady(page: View, attempt: Int) {
        if (!page.isAttachedToWindow) return
        val chromeViews = ArrayList<View>()
        val cardStacks = ArrayList<View>()
        collectAnimatedViews(page, chromeViews, cardStacks)
        val waitingStacks = cardStacks.filter(::isCardStackWaitingForFirstStyle)
        if (waitingStacks.isNotEmpty() && attempt < MAX_ENTRY_READY_RETRIES) {
            page.postDelayed({ animatePageEntryWhenReady(page, attempt + 1) }, ENTRY_READY_RETRY_DELAY_MS)
            return
        }
        animatePageEntry(chromeViews, cardStacks - waitingStacks.toSet())
    }

    fun animatePageExit(pager: ViewPager2, pageIndex: Int) {
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
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
        val recycler = pager.getChildAt(0) as? RecyclerView
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

    private fun animatePageEntry(page: View) {
        val chromeViews = ArrayList<View>()
        val cardStacks = ArrayList<View>()
        collectAnimatedViews(page, chromeViews, cardStacks)
        animatePageEntry(chromeViews, cardStacks)
    }

    private fun animatePageEntry(chromeViews: List<View>, cardStacks: List<View>) {
        chromeViews.forEachIndexed { index, view -> animateChromeIntoView(view, index) }
        cardStacks.forEach { stack -> animateCardStackIntoView(stack) }
    }

    private fun collectAnimatedViews(root: View, chromeViews: MutableList<View>, cardStacks: MutableList<View>) {
        when (root.tag) {
            CalmAnimationTags.CHROME -> chromeViews.add(root)
            CalmAnimationTags.CARD_STACK -> cardStacks.add(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                collectAnimatedViews(root.getChildAt(index), chromeViews, cardStacks)
            }
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

    private fun animateCardStackIntoView(stackView: View) {
        val content = (stackView as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return
        var animatedCount = 0
        for (index in 0 until content.childCount) {
            val card = content.getChildAt(index)
            if (card.tag != CalmAnimationTags.CARD) continue
            if (animatedCount >= MAX_ENTRY_ANIMATED_CARDS) break
            animateCardIntoView(card, animatedCount)
            animatedCount++
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

    private fun animateCardIntoView(card: View, index: Int) {
        val targetAlpha = CardEntryAnimationLogic.entryTargetAlpha(card.alpha)
        val targetY = card.translationY
        card.animate().cancel()
        card.alpha = 0f
        card.translationY = targetY + activity.dp(132 + (index * 18))
        card.animate()
            .alpha(targetAlpha)
            .translationY(targetY)
            .setStartDelay(80L + (index * 46L))
            .setDuration(390L)
            .setInterpolator(DecelerateInterpolator())
            .start()
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
    }
}
