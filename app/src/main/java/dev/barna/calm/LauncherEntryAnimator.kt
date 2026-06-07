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
        var cardCount = 0
        for (index in 0 until content.childCount) {
            val card = content.getChildAt(index)
            if (card.tag != CalmAnimationTags.CARD) continue
            cardCount++
            if (card.alpha > 0f) return false
        }
        return cardCount > 0
    }

    private fun animateCardIntoView(card: View, index: Int) {
        val targetAlpha = card.alpha
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
    }
}
