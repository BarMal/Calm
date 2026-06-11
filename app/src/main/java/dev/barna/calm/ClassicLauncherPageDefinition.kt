package dev.barna.calm

import org.json.JSONArray
import org.json.JSONObject

data class ClassicLauncherPageDefinition(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
) {
    val key: String
        get() = "$KEY_PREFIX$id"

    fun encode(): JSONObject {
        return JSONObject()
            .put(FIELD_ID, id)
            .put(FIELD_TITLE, title)
            .put(FIELD_ENABLED, enabled)
    }

    companion object {
        const val KEY_PREFIX = "classic:"

        private const val FIELD_ID = "id"
        private const val FIELD_TITLE = "title"
        private const val FIELD_ENABLED = "enabled"

        fun default(index: Int = 1): ClassicLauncherPageDefinition {
            return ClassicLauncherPageDefinition(id = "classic-$index", title = "Classic")
        }

        fun decode(json: JSONObject): ClassicLauncherPageDefinition? {
            val id = json.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return null
            val title = json.optString(FIELD_TITLE).takeIf { it.isNotBlank() } ?: "Classic"
            return ClassicLauncherPageDefinition(
                id = id,
                title = title,
                enabled = json.optBoolean(FIELD_ENABLED, true),
            )
        }

        fun encodeList(pages: List<ClassicLauncherPageDefinition>): String {
            val array = JSONArray()
            pages.forEach { page -> array.put(page.encode()) }
            return array.toString()
        }

        fun decodeList(raw: String?): List<ClassicLauncherPageDefinition> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val page = decode(array.optJSONObject(index) ?: continue) ?: continue
                        add(page)
                    }
                }.distinctBy { it.id }
            }.getOrDefault(emptyList())
        }
    }
}
