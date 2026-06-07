package dev.barna.calm

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

class NotificationCardDisplayCache(
    private val assetResolver: NotificationCardAssetResolver,
) {
    private val cards = ConcurrentHashMap<String, NotificationCardDisplayData>()
    private val chapterIcons = ConcurrentHashMap<String, Bitmap>()
    private val preloadKeys = ConcurrentHashMap.newKeySet<String>()

    fun preload(
        chapters: List<AppChapter>,
        groupingEnabled: (AppChapter) -> Boolean,
        timeFormatter: (Long) -> String,
        executor: Executor,
    ) {
        chapters.forEach { chapter ->
            val preloadKey = preloadKey(chapter)
            if (!preloadKeys.add(preloadKey)) return@forEach
            executor.execute {
                try {
                    chapterMaskedIcon(chapter)
                    NotificationCardGrouper.cards(chapter.notifications, groupingEnabled(chapter))
                        .forEach { item -> getOrCreate(item, chapter, timeFormatter) }
                } finally {
                    preloadKeys.remove(preloadKey)
                }
            }
        }
    }

    fun getOrCreate(
        item: NotificationCardItem,
        chapter: AppChapter,
        timeFormatter: (Long) -> String,
    ): NotificationCardDisplayData {
        val key = key(item, chapter)
        cards[key]?.let { return it }
        val data = create(item, chapter, timeFormatter)
        return cards.putIfAbsent(key, data) ?: data
    }

    fun chapterMaskedIcon(chapter: AppChapter): Bitmap? {
        chapterIcons[chapter.launcherIdentityKey]?.let { return it }
        val bitmap = assetResolver.resolveMaskedAppIconBitmap(chapter) ?: return null
        return chapterIcons.putIfAbsent(chapter.launcherIdentityKey, bitmap) ?: bitmap
    }

    fun clear() {
        cards.clear()
        chapterIcons.clear()
        preloadKeys.clear()
    }

    private fun create(
        item: NotificationCardItem,
        chapter: AppChapter,
        timeFormatter: (Long) -> String,
    ): NotificationCardDisplayData {
        val artwork = item.notifications.firstNotNullOfOrNull { it.backgroundImage }
        val isMedia = MediaNotificationControls.from(item.notifications).hasAnyAction
        val sideImage = when {
            isMedia -> null
            artwork != null -> artwork.toRectangularCardArtwork()
            else -> assetResolver.resolveAppIconBitmap(chapter)
        }
        val sideImageKey = when {
            isMedia -> null
            artwork != null -> artworkKey(item, artwork)
            sideImage != null -> chapter.launcherIdentityKey
            else -> null
        }
        return NotificationCardDisplayData(
            text = "${item.title()}\n${item.previewText()}\n${timeFormatter(item.primary.postTime)}",
            fullText = item.fullText(),
            sideImage = sideImage,
            sideImageRenderKey = sideImageKey,
            sideImageAlpha = if (artwork != null && !isMedia) 156 else 64,
            mediaBackgroundImage = artwork.takeIf { isMedia },
        )
    }

    private fun key(item: NotificationCardItem, chapter: AppChapter): String {
        return buildString {
            append(chapter.identityKey)
            append('|')
            append(item.notifications.size)
            item.notifications.forEach { notification ->
                append('|')
                append(notification.key)
                append(':')
                append(notification.postTime)
                append(':')
                append(notification.conversationTitle.hashCode())
                append(':')
                append(notification.title.hashCode())
                append(':')
                append(notification.bodyText().hashCode())
                append(':')
                append(notification.backgroundImage?.hashCode() ?: 0)
                append(':')
                append(notification.actions.joinToString { action -> action.label }.hashCode())
            }
        }
    }

    private fun preloadKey(chapter: AppChapter): String {
        return buildString {
            append(chapter.identityKey)
            append('|')
            append(chapter.notifications.size)
            chapter.notifications.forEach { notification ->
                append('|')
                append(notification.key)
                append(':')
                append(notification.postTime)
            }
        }
    }

    private fun artworkKey(item: NotificationCardItem, artwork: Bitmap): String {
        return buildString {
            append("artwork")
            item.notifications.forEach { notification ->
                append('|')
                append(notification.key)
            }
            append('|')
            append(artwork.width)
            append('x')
            append(artwork.height)
            append('|')
            append(artwork.generationId)
        }
    }
}

data class NotificationCardDisplayData(
    val text: String,
    val fullText: String,
    val sideImage: Bitmap?,
    val sideImageRenderKey: String?,
    val sideImageAlpha: Int,
    val mediaBackgroundImage: Bitmap?,
)

interface NotificationCardAssetResolver {
    fun resolveAppIconBitmap(chapter: AppChapter): Bitmap?
    fun resolveMaskedAppIconBitmap(chapter: AppChapter): Bitmap?
}
