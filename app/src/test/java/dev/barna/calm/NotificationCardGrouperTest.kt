package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCardGrouperTest {
    @Test
    fun cardsPrunesCountSummaryWhenRealMessagesExist() {
        val cards = NotificationCardGrouper.cards(
            listOf(
                notification("summary", title = "2 new messages", text = ""),
                notification("one", title = "Alice", text = "First"),
                notification("two", title = "Bob", text = "Second"),
            ),
            groupingEnabled = false,
        )

        assertEquals(listOf("one", "two"), cards.map { it.primary.key })
    }

    @Test
    fun cardsGroupsNotificationsByConversationTitle() {
        val cards = NotificationCardGrouper.cards(
            listOf(
                notification("one", title = "WhatsApp", text = "First", conversation = "Alice"),
                notification("two", title = "WhatsApp", text = "Second", conversation = "Alice"),
                notification("three", title = "WhatsApp", text = "Other", conversation = "Bob"),
            ),
            groupingEnabled = true,
        )

        assertEquals(2, cards.size)
        assertEquals(listOf("two", "one"), cards.first { it.isGroup }.notifications.map { it.key })
    }

    @Test
    fun groupedCardUsesConversationTitleAndCombinedPreview() {
        val card = NotificationCardGrouper.cards(
            listOf(
                notification("one", title = "Alice", text = "First", conversation = "Alice", postTime = 1L),
                notification("two", title = "Alice", text = "Second", conversation = "Alice", postTime = 2L),
            ),
            groupingEnabled = true,
        ).single()

        assertEquals("Alice (2)", card.title())
        assertEquals("Second\nFirst", card.previewText())
    }

    @Test
    fun groupingKeyKeepsSameConversationSeparateAcrossPlatforms() {
        val cards = NotificationCardGrouper.cards(
            listOf(
                notification("signal", packageName = "org.signal", title = "Alice", text = "Signal", conversation = "Alice"),
                notification("whatsapp", packageName = "com.whatsapp", title = "Alice", text = "WhatsApp", conversation = "Alice"),
            ),
            groupingEnabled = true,
        )

        assertEquals(2, cards.size)
    }

    @Test
    fun cardsKeepsNotificationsSplitWhenGroupingDisabled() {
        val cards = NotificationCardGrouper.cards(
            listOf(
                notification("one", title = "Alice", text = "First"),
                notification("two", title = "Alice", text = "Second"),
            ),
            groupingEnabled = false,
        )

        assertEquals(2, cards.size)
        assertEquals(false, cards.any { it.isGroup })
    }

    private fun notification(
        key: String,
        packageName: String = "com.example.chat",
        title: String,
        text: String,
        conversation: String = "",
        postTime: Long = key.hashCode().toLong(),
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            subText = "",
            conversationTitle = conversation,
            postTime = postTime,
            contentIntent = null,
            backgroundImage = null,
            actions = emptyList(),
        )
    }
}
