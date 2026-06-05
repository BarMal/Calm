package dev.barna.calm

data class AppIdentity(
    val packageName: String,
    val className: String = "",
    val userSerial: Long = LEGACY_USER_SERIAL,
) {
    val key: String
        get() = if (userSerial == LEGACY_USER_SERIAL && className.isBlank()) {
            packageName
        } else {
            listOf(userSerial.toString(), packageName, className).joinToString(SEPARATOR)
        }

    val notificationSourceKey: String
        get() = notificationKey(packageName, userSerial)

    companion object {
        const val LEGACY_USER_SERIAL = -1L
        private const val SEPARATOR = "\u001e"

        fun launcherKey(packageName: String, className: String, userSerial: Long): String {
            return AppIdentity(packageName, className, userSerial).key
        }

        fun notificationKey(packageName: String, userSerial: Long): String {
            return if (userSerial == LEGACY_USER_SERIAL) {
                packageName
            } else {
                listOf(userSerial.toString(), packageName, "").joinToString(SEPARATOR)
            }
        }

        fun packageOnly(packageName: String): AppIdentity {
            return AppIdentity(packageName)
        }

        fun decode(key: String): AppIdentity {
            val parts = key.split(SEPARATOR, limit = 3)
            if (parts.size < 2) return packageOnly(key)
            return AppIdentity(
                packageName = parts[1],
                className = parts.getOrNull(2).orEmpty(),
                userSerial = parts[0].toLongOrNull() ?: LEGACY_USER_SERIAL,
            )
        }
    }
}
