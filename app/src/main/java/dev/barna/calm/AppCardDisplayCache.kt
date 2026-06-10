package dev.barna.calm

import android.graphics.Bitmap
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class AppCardDisplayCache(
    private val notificationRepository: NotificationChapterRepository,
    private val modelFactory: AppCardModelFactory,
) {
    private val cards = ConcurrentHashMap<String, AppCardDisplayData>()
    private val pendingKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun preload(apps: List<AppEntry>, pinnedKeys: Set<String>, executor: Executor) {
        apps.forEach { app ->
            val key = key(app, pinnedKeys)
            if (cards.containsKey(key) || !pendingKeys.add(key)) return@forEach
            executor.execute {
                try {
                    cards.putIfAbsent(key, create(app, pinnedKeys))
                } finally {
                    pendingKeys.remove(key)
                }
            }
        }
        trim()
    }

    fun preloadNow(apps: List<AppEntry>, pinnedKeys: Set<String>) {
        apps.forEach { app ->
            val key = key(app, pinnedKeys)
            cards.putIfAbsent(key, create(app, pinnedKeys))
        }
        trim()
    }

    fun getOrCreate(app: AppEntry, pinnedKeys: Set<String>): AppCardDisplayData {
        val key = key(app, pinnedKeys)
        cards[key]?.let { return it }
        val data = create(app, pinnedKeys)
        return cards.putIfAbsent(key, data) ?: data
    }

    fun getCachedOrCreateLightweight(app: AppEntry, pinnedKeys: Set<String>): AppCardDisplayData {
        val key = key(app, pinnedKeys)
        cards[key]?.let { return it }
        val model = modelFactory.create(app, pinnedKeys)
        return AppCardDisplayData(
            app = model.app,
            text = model.text,
            hueColor = model.hueColor,
            isPinned = model.isPinned,
            icon = null,
            iconRenderKey = app.identityKey,
        )
    }

    fun clear() {
        cards.clear()
        pendingKeys.clear()
    }

    private fun create(app: AppEntry, pinnedKeys: Set<String>): AppCardDisplayData {
        val model = modelFactory.create(app, pinnedKeys)
        val icon = notificationRepository.resolveAppIconBitmap(app)
        return AppCardDisplayData(
            app = model.app,
            text = model.text,
            hueColor = model.hueColor,
            isPinned = model.isPinned,
            icon = icon,
            iconRenderKey = app.identityKey,
            // Sample the icon's render colours here, on the preload executor, so the card can be
            // bound on the main thread without any per-icon sampling.
            iconRenderData = icon?.let { AppIconCardRenderData.from(it, CardRenderer.DEFAULT_ICON_BACKGROUND_ALPHA) },
        )
    }

    private fun trim() {
        if (cards.size <= MAX_CARDS) return
        clear()
    }

    private fun key(app: AppEntry, pinnedKeys: Set<String>): String {
        return buildString {
            append(app.identityKey)
            append('|')
            append(app.label)
            append('|')
            append(app.profileLabel)
            append('|')
            append(app.hueColor)
            append('|')
            append(app.identityKey in pinnedKeys || app.packageName in pinnedKeys)
        }
    }

    private companion object {
        const val MAX_CARDS = 2048
    }
}

data class AppCardDisplayData(
    val app: AppEntry,
    val text: String,
    val hueColor: Int,
    val isPinned: Boolean,
    val icon: Bitmap?,
    val iconRenderKey: String,
    val iconRenderData: AppIconCardRenderData? = null,
)
