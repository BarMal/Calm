package dev.barna.calm

import java.util.Locale

/**
 * Stable user-facing app category definition.
 *
 * This is the model foundation for category app-library pages. Categories are encoded as
 * compact strings so they can be persisted in LauncherSettings before the full editor UI exists.
 */
data class AppCategory(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Category id must not be blank" }
        require(title.isNotBlank()) { "Category title must not be blank" }
        require(id == normalizeId(id)) { "Category id must be normalized" }
    }

    fun encode(): String = listOf(escape(id), escape(title), enabled.toString()).joinToString(FIELD_SEPARATOR.toString())

    companion object {
        private const val FIELD_SEPARATOR = '|'
        private const val ESCAPE = '\\'

        val DEFAULTS: List<AppCategory> = listOf(
            AppCategory("communications", "Communications"),
            AppCategory("finance", "Finance"),
            AppCategory("shopping", "Shopping"),
            AppCategory("media", "Media"),
            AppCategory("productivity", "Productivity"),
            AppCategory("system", "System"),
            AppCategory("customisation", "Customisation"),
            AppCategory("games", "Games"),
        )

        fun custom(title: String, enabled: Boolean = true): AppCategory {
            val cleanTitle = title.trim()
            require(cleanTitle.isNotBlank()) { "Category title must not be blank" }
            return AppCategory(normalizeId(cleanTitle), cleanTitle, enabled)
        }

        fun decode(encoded: String): AppCategory? {
            val fields = splitEscaped(encoded)
            if (fields.size != 3) return null
            val id = unescape(fields[0]).trim()
            val title = unescape(fields[1]).trim()
            val enabled = fields[2].toBooleanStrictOrNull() ?: return null
            if (id.isBlank() || title.isBlank() || id != normalizeId(id)) return null
            return AppCategory(id, title, enabled)
        }

        fun encodeList(categories: List<AppCategory>): String {
            return categories
                .distinctBy { it.id }
                .joinToString("\n") { it.encode() }
        }

        fun decodeList(encoded: String?): List<AppCategory> {
            if (encoded.isNullOrBlank()) return DEFAULTS
            val decoded = encoded
                .lineSequence()
                .mapNotNull { decode(it) }
                .distinctBy { it.id }
                .toList()
            return decoded.ifEmpty { DEFAULTS }
        }

        fun normalizeId(value: String): String {
            return value
                .trim()
                .lowercase(Locale.ROOT)
                .replace("&", "and")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }

        private fun escape(value: String): String {
            return buildString {
                value.forEach { char ->
                    if (char == FIELD_SEPARATOR || char == ESCAPE) append(ESCAPE)
                    append(char)
                }
            }
        }

        private fun unescape(value: String): String {
            val out = StringBuilder()
            var escaped = false
            value.forEach { char ->
                if (escaped) {
                    out.append(char)
                    escaped = false
                } else if (char == ESCAPE) {
                    escaped = true
                } else {
                    out.append(char)
                }
            }
            if (escaped) out.append(ESCAPE)
            return out.toString()
        }

        private fun splitEscaped(value: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            value.forEach { char ->
                if (escaped) {
                    current.append(ESCAPE).append(char)
                    escaped = false
                } else if (char == ESCAPE) {
                    escaped = true
                } else if (char == FIELD_SEPARATOR) {
                    fields.add(current.toString())
                    current.clear()
                } else {
                    current.append(char)
                }
            }
            if (escaped) current.append(ESCAPE)
            fields.add(current.toString())
            return fields
        }
    }
}
