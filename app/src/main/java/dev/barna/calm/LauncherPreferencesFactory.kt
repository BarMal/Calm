@file:Suppress("DEPRECATION")

package dev.barna.calm

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal object LauncherPreferencesFactory {
    private const val PLAIN_PREFS_NAME = "calm_preferences"
    private const val SECURE_PREFS_NAME = "calm_preferences_secure"

    fun create(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val plaintextPreferences = appContext.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
        val securePreferences = runCatching {
            encryptedSharedPreferences(appContext)
        }.getOrElse {
            plaintextPreferences
        }

        if (securePreferences !== plaintextPreferences) {
            migratePlaintextPreferences(plaintextPreferences, securePreferences)
        }
        return securePreferences
    }

    private fun encryptedSharedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    internal fun migratePlaintextPreferences(
        plaintextPreferences: SharedPreferences,
        securePreferences: SharedPreferences,
    ): Boolean {
        val values = plaintextPreferences.all
        if (values.isEmpty()) return false

        val editor = securePreferences.edit()
        for ((key, value) in values) {
            if (securePreferences.contains(key)) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }

        return editor.commit().also { migrated ->
            if (migrated) {
                plaintextPreferences.edit().clear().apply()
            }
        }
    }
}
