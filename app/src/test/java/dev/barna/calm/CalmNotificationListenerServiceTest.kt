package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class CalmNotificationListenerServiceTest {
    @Test
    fun cappedMessagingKeysRemainStableWhenWindowAdvances() {
        val firstWindow = messageKeys((1..21).toList())
        val secondWindow = messageKeys((1..22).toList())

        val overlapFromFirstWindow = firstWindow.drop(1)
        val overlapFromSecondWindow = secondWindow.dropLast(1)

        assertEquals(overlapFromFirstWindow, overlapFromSecondWindow)
    }

    private fun messageKeys(messageNumbers: List<Int>): List<String> {
        return messageNumbers
            .mapIndexed { index, messageNumber -> IndexedValue(index, messageNumber) }
            .takeLast(20)
            .map { indexed ->
                CalmNotificationListenerService.messagingMessageKey(
                    statusKey = "status-key",
                    originalIndex = indexed.index,
                    timestamp = indexed.value.toLong(),
                )
            }
    }
}
