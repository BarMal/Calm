package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactChannelsTest {
    @Test
    fun mapsKnownMessagingMimeTypes() {
        assertEquals(ContactAppChannelKind.WHATSAPP, ContactChannels.appChannelFor(ContactChannels.MIME_WHATSAPP))
        assertEquals(ContactAppChannelKind.SIGNAL, ContactChannels.appChannelFor(ContactChannels.MIME_SIGNAL))
        assertEquals(ContactAppChannelKind.TELEGRAM, ContactChannels.appChannelFor(ContactChannels.MIME_TELEGRAM))
    }

    @Test
    fun unknownOrNullMimeTypeHasNoChannel() {
        assertNull(ContactChannels.appChannelFor("vnd.android.cursor.item/phone_v2"))
        assertNull(ContactChannels.appChannelFor(null))
    }

    @Test
    fun queriedMimeTypesCoverEveryKnownChannel() {
        ContactChannels.appChannelMimeTypes.forEach { mime ->
            assertTrue("Expected $mime to resolve to a channel", ContactChannels.appChannelFor(mime) != null)
        }
        assertEquals(ContactAppChannelKind.values().size, ContactChannels.appChannelMimeTypes.size)
    }
}
