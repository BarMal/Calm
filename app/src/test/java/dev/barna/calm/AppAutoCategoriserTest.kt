package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAutoCategoriserTest {
    private val categoriser = AppAutoCategoriser()

    @Test
    fun whatsAppIsClassifiedAsCommunications() {
        assertEquals(listOf("communications"), categoriser.categorise("com.whatsapp"))
    }

    @Test
    fun telegramVariantsAreClassifiedAsCommunications() {
        assertTrue("communications" in categoriser.categorise("org.telegram.messenger"))
        assertTrue("communications" in categoriser.categorise("org.telegram.plus"))
    }

    @Test
    fun spotifyIsClassifiedAsMedia() {
        assertEquals(listOf("media"), categoriser.categorise("com.spotify.music"))
    }

    @Test
    fun youtubeIsClassifiedAsMedia() {
        assertEquals(listOf("media"), categoriser.categorise("com.google.android.youtube"))
    }

    @Test
    fun googleDocsIsClassifiedAsProductivity() {
        assertEquals(listOf("productivity"), categoriser.categorise("com.google.android.apps.docs"))
    }

    @Test
    fun unknownPackageReturnsEmpty() {
        assertTrue(categoriser.categorise("com.unknown.randomapp").isEmpty())
    }

    @Test
    fun resultsAreDistinct() {
        val ids = categoriser.categorise("com.whatsapp")
        assertEquals(ids.distinct(), ids)
    }
}
