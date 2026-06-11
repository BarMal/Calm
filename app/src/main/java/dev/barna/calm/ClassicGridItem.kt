package dev.barna.calm

import org.json.JSONObject
import java.security.MessageDigest

enum class ClassicGridItemType {
    APP,
    WIDGET,
}

data class ClassicGridItem(
    val id: String,
    val type: ClassicGridItemType,
    val target: String,
    val x: Int,
    val y: Int,
    val width: Int = 1,
    val height: Int = 1,
) {
    fun encode(): JSONObject {
        return JSONObject()
            .put(FIELD_ID, id)
            .put(FIELD_TYPE, type.name)
            .put(FIELD_TARGET, target)
            .put(FIELD_X, x)
            .put(FIELD_Y, y)
            .put(FIELD_WIDTH, width)
            .put(FIELD_HEIGHT, height)
    }

    companion object {
        const val GRID_COLUMNS = 4
        const val DEFAULT_GRID_ROWS = 6

        private const val FIELD_ID = "id"
        private const val FIELD_TYPE = "type"
        private const val FIELD_TARGET = "target"
        private const val FIELD_X = "x"
        private const val FIELD_Y = "y"
        private const val FIELD_WIDTH = "width"
        private const val FIELD_HEIGHT = "height"

        fun app(identityKey: String, x: Int, y: Int): ClassicGridItem {
            return ClassicGridItem(
                id = "app:${sha256(identityKey)}",
                type = ClassicGridItemType.APP,
                target = identityKey,
                x = x,
                y = y,
            )
        }

        fun decode(json: JSONObject): ClassicGridItem? {
            val id = json.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return null
            val target = json.optString(FIELD_TARGET).takeIf { it.isNotBlank() } ?: return null
            val type = runCatching {
                ClassicGridItemType.valueOf(json.optString(FIELD_TYPE, ClassicGridItemType.APP.name))
            }.getOrDefault(ClassicGridItemType.APP)
            val x = json.optInt(FIELD_X, 0).coerceIn(0, GRID_COLUMNS - 1)
            val y = json.optInt(FIELD_Y, 0).coerceIn(0, DEFAULT_GRID_ROWS - 1)
            return ClassicGridItem(
                id = id,
                type = type,
                target = target,
                x = x,
                y = y,
                width = json.optInt(FIELD_WIDTH, 1).coerceIn(1, GRID_COLUMNS - x),
                height = json.optInt(FIELD_HEIGHT, 1).coerceIn(1, DEFAULT_GRID_ROWS - y),
            )
        }

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
