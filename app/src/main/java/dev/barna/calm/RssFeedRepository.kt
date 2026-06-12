package dev.barna.calm

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class RssFeedRepository(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun loadItems(feedUrls: List<String>, maxItems: Int = DEFAULT_MAX_ITEMS): List<RssFeedItem> {
        return feedUrls
            .mapNotNull(::normalizeUrl)
            .distinct()
            .flatMap { url -> runCatching { fetch(url) }.getOrElse { emptyList() } }
            .sortedByDescending { item -> item.publishedAt }
            .take(maxItems)
    }

    fun parse(input: InputStream, fallbackFeedTitle: String = "RSS"): List<RssFeedItem> {
        val document = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
        }.newDocumentBuilder().parse(input)
        document.documentElement.normalize()
        return when (document.documentElement.tagName.lowercase(Locale.ROOT)) {
            "feed" -> parseAtom(document.documentElement, fallbackFeedTitle)
            else -> parseRss(document.documentElement, fallbackFeedTitle)
        }
    }

    private fun fetch(url: String): List<RssFeedItem> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "CalmLauncher/1.0")
        return connection.inputStream.use { stream -> parse(stream, url) }
    }

    private fun parseRss(root: org.w3c.dom.Element, fallbackFeedTitle: String): List<RssFeedItem> {
        val channel = root.elements("channel").firstOrNull() ?: root
        val feedTitle = channel.childText("title").ifBlank { fallbackFeedTitle }
        return channel.elements("item").mapIndexed { index, item ->
            RssFeedItem(
                feedTitle = feedTitle,
                title = item.childText("title").ifBlank { "Untitled item" },
                summary = item.childText("description").cleanMarkup(),
                link = item.childText("link"),
                publishedAt = parseDate(item.childText("pubDate")) ?: parseDate(item.childText("dc:date")) ?: (now() - index),
            )
        }
    }

    private fun parseAtom(root: org.w3c.dom.Element, fallbackFeedTitle: String): List<RssFeedItem> {
        val feedTitle = root.childText("title").ifBlank { fallbackFeedTitle }
        return root.elements("entry").mapIndexed { index, entry ->
            RssFeedItem(
                feedTitle = feedTitle,
                title = entry.childText("title").ifBlank { "Untitled item" },
                summary = entry.childText("summary").ifBlank { entry.childText("content") }.cleanMarkup(),
                link = entry.atomLink(),
                publishedAt = parseDate(entry.childText("published")) ?: parseDate(entry.childText("updated")) ?: (now() - index),
            )
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        return when {
            clean.startsWith("http://", ignoreCase = true) -> clean
            clean.startsWith("https://", ignoreCase = true) -> clean
            else -> "https://$clean"
        }
    }

    private fun parseDate(value: String): Long? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        DATE_FORMATS.forEach { pattern ->
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(clean)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun org.w3c.dom.Element.elements(name: String): List<org.w3c.dom.Element> {
        val nodes = getElementsByTagName(name)
        return (0 until nodes.length).mapNotNull { index -> nodes.item(index) as? org.w3c.dom.Element }
    }

    private fun org.w3c.dom.Element.childText(name: String): String {
        val child = elements(name).firstOrNull() ?: return ""
        return child.textContent?.trim().orEmpty()
    }

    private fun org.w3c.dom.Element.atomLink(): String {
        val links = elements("link")
        return links.firstOrNull { link -> link.getAttribute("rel").isBlank() || link.getAttribute("rel") == "alternate" }
            ?.getAttribute("href")
            ?.trim()
            .orEmpty()
    }

    private fun String.cleanMarkup(): String {
        return replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val DEFAULT_MAX_ITEMS = 30
        private const val NETWORK_TIMEOUT_MS = 4500
        private val DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
    }
}
