package dev.barna.calm

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class RssFeedRepositoryTest {
    private val repository = RssFeedRepository(now = { 1_700_000_000_000L })

    @Test
    fun parsesRssItems() {
        val xml = """
            <rss version="2.0">
                <channel>
                    <title>Calm News</title>
                    <item>
                        <title>First item</title>
                        <description><![CDATA[<p>Hello world</p>]]></description>
                        <link>https://example.com/first</link>
                        <pubDate>Fri, 12 Jun 2026 10:30:00 +0000</pubDate>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val items = repository.parse(xml.byteInputStream())

        assertEquals(1, items.size)
        assertEquals("Calm News", items.first().feedTitle)
        assertEquals("First item", items.first().title)
        assertEquals("Hello world", items.first().summary)
        assertEquals("https://example.com/first", items.first().link)
    }

    @Test
    fun parsesAtomEntries() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Atom Feed</title>
                <entry>
                    <title>Atom item</title>
                    <summary>Short summary</summary>
                    <link rel="alternate" href="https://example.com/atom" />
                    <updated>2026-06-12T10:30:00Z</updated>
                </entry>
            </feed>
        """.trimIndent()

        val items = repository.parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(1, items.size)
        assertEquals("Atom Feed", items.first().feedTitle)
        assertEquals("Atom item", items.first().title)
        assertEquals("Short summary", items.first().summary)
        assertEquals("https://example.com/atom", items.first().link)
    }
}
