package dev.barna.calm

import android.graphics.Typeface
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2

class LauncherAppLibraryController(
    private val activity: MainActivity,
    private val cardRenderer: CardRenderer,
    private val cardStackController: CardStackController,
    private val appQuickScrollController: AppQuickScrollController,
    private val appCardDisplayCache: AppCardDisplayCache,
    private val contextActionFactory: LauncherContextActionFactory,
    private val focusOverlay: FocusOverlayController,
    private val mainHandler: Handler,
    private val activePreferences: () -> LauncherUiPreferences,
    private val currentPager: () -> ViewPager2?,
    private val pinnedKeys: () -> Set<String>,
    private val openAppEntry: (AppEntry) -> Unit,
) {
    private val appStackRenderPlanner = AppStackRenderPlanner()

    fun refreshAppStack(
        stackHost: FrameLayout,
        model: AppLibraryPageModel,
        cardCache: MutableMap<String, TextView>? = null,
    ) {
        cardCache?.values?.forEach { card ->
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        stackHost.removeAllViews()
        if (model.apps.isEmpty()) {
            stackHost.addView(appSearchEmptyStack(model.emptyMessage, appLibraryStackKey(model)), matchParentParams())
        } else {
            val plan = appStackRenderPlanner.plan(model.apps, activePreferences().cardStackTuning)
            val cards = plan.initialApps.map { app -> appCardFromCache(app, cardCache) }.toMutableList()
            val stack = appStack(cards, appLibraryStackKey(model))
            stackHost.addView(stack, matchParentParams())
            appendDeferredAppCards(stackHost, stack, cards, plan.deferredApps, cardCache, model)
        }
    }

    fun appStack(
        apps: List<AppEntry>,
        cardCache: MutableMap<String, TextView>? = null,
        stackKey: String = CardStackStateKey.appEntries("apps", apps),
    ): ScrollView {
        return appStack(apps.map { app -> appCardFromCache(app, cardCache) }.toMutableList(), stackKey)
    }

    fun appStack(cards: MutableList<TextView>, stackKey: String): ScrollView {
        val prefs = activePreferences()
        return cardStackController.cardStack(
            cards,
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            prefs.cardStackTuning,
            stackKey,
        )
    }

    private fun appendDeferredAppCards(
        stackHost: FrameLayout,
        stack: ScrollView,
        renderedCards: MutableList<TextView>,
        deferredApps: List<AppEntry>,
        cardCache: MutableMap<String, TextView>?,
        model: AppLibraryPageModel,
    ) {
        var nextDeferredIndex = 0
        fun appendNextBatch(): Boolean {
            if (nextDeferredIndex >= deferredApps.size) return false
            val end = (nextDeferredIndex + APP_STACK_DEFERRED_BATCH_SIZE).coerceAtMost(deferredApps.size)
            val batch = deferredApps.subList(nextDeferredIndex, end)
            nextDeferredIndex = end
            val newCards = batch.map { app -> appCardFromCache(app, cardCache) }
            val prefs = activePreferences()
            cardStackController.appendCards(stack, renderedCards, newCards, cardRenderer.cardHeight(), cardRenderer.cardStep(), prefs.cardStackTuning)
            return true
        }
        fun ensureRendered(cardIndex: Int) {
            while (renderedCards.size <= cardIndex && appendNextBatch()) {
            }
        }
        fun scheduleNextBatch(delayMs: Long) {
            mainHandler.postDelayed({
                if (stack.parent == null) return@postDelayed
                if (currentPager()?.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                    scheduleNextBatch(APP_STACK_DEFERRED_BATCH_DELAY_MS)
                    return@postDelayed
                }
                if (appendNextBatch()) {
                    scheduleNextBatch(nextDeferredBatchDelay(stack))
                }
            }, delayMs)
        }

        fun startDeferredBatches() {
            scheduleNextBatch(APP_STACK_DEFERRED_INITIAL_DELAY_MS)
        }

        appQuickScrollController.attach(stackHost, stack, model, activePreferences().cardStackTuning, ::ensureRendered)
        if (deferredApps.isNotEmpty()) {
            if (stackHost.isAttachedToWindow) {
                startDeferredBatches()
            } else {
                stackHost.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        if (stack.parent != null) {
                            startDeferredBatches()
                        }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }
    }

    private fun nextDeferredBatchDelay(stack: ScrollView): Long {
        return if (cardStackController.hasPendingRestore(stack)) {
            APP_STACK_PENDING_RESTORE_BATCH_DELAY_MS
        } else {
            APP_STACK_DEFERRED_BATCH_DELAY_MS
        }
    }

    private fun appCardFromCache(app: AppEntry, cardCache: MutableMap<String, TextView>? = null): TextView {
        return cardCache?.getOrPut(app.identityKey) { appCard(app) } ?: appCard(app)
    }

    private fun appSearchEmptyStack(message: String, stackKey: String): View {
        val card = cardRenderer.stackCard(
            "Search\n$message",
            CalmTheme.ACCENT,
            true,
            cardRenderer.cardSideIcon(R.drawable.ic_search_card),
            sideImageRenderKey = "res:${R.drawable.ic_search_card}",
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            maxLines = 3
            isEnabled = false
        }
        return cardStackController.cardStack(
            listOf(card),
            cardRenderer.cardHeight(),
            cardRenderer.cardStep(),
            activePreferences().cardStackTuning,
            stackKey,
        )
    }

    private fun appLibraryStackKey(model: AppLibraryPageModel): String {
        return CardStackStateKey.appLibrary(model.key, model.scope, model.query)
    }

    private fun appCard(app: AppEntry): TextView {
        val data = appCardDisplayCache.getCachedOrCreateLightweight(app, pinnedKeys())
        return cardRenderer.stackCard(
            data.text,
            data.hueColor,
            true,
            data.icon,
            sideImageRenderKey = data.iconRenderKey,
            precomputedIconRenderData = data.iconRenderData,
        ).apply {
            maxLines = 4
            setOnClickListener { openAppEntry(app) }
            setOnLongClickListener {
                val actions = contextActionFactory.appActions(data.app, data.isPinned)
                if (activePreferences().expandedCardsEnabled) {
                    focusOverlay.showExpandedCard(this, expandedAppContent(data), actions)
                } else {
                    focusOverlay.show(this, actions, data.app.label)
                }
                true
            }
        }
    }

    private fun expandedAppContent(data: AppCardDisplayData): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        data.icon?.let { icon ->
            row.addView(
                ImageView(activity).apply {
                    setImageBitmap(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(activity.dp(64), activity.dp(64)).apply { rightMargin = activity.dp(18) },
            )
        }
        row.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(expandedText(data.app.label, 22, CalmTheme.INK, Typeface.BOLD))
                if (data.app.profileLabel.isNotBlank()) {
                    addView(expandedText(data.app.profileLabel, 13, CalmTheme.MUTED_INK, Typeface.NORMAL).apply {
                        setPadding(0, activity.dp(4), 0, 0)
                    })
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return row
    }

    private fun expandedText(text: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(activity).apply {
            this.text = text
            setTextColor(color)
            textSize = sp.toFloat()
            setTypeface(Typeface.DEFAULT, style)
        }
    }

    private fun matchParentParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    companion object {
        const val APP_STACK_DEFERRED_BATCH_SIZE = 16
        const val APP_STACK_DEFERRED_INITIAL_DELAY_MS = 48L
        const val APP_STACK_PENDING_RESTORE_BATCH_DELAY_MS = 0L
        const val APP_STACK_DEFERRED_BATCH_DELAY_MS = 32L
    }
}
