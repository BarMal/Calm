package dev.barna.calm

import android.content.Context
import android.content.SharedPreferences
import java.text.Collator

class LauncherSettings(private val context: Context) {
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun excludedPackages(): Set<String> {
        return HashSet(preferences.getStringSet(PREF_EXCLUDED_PACKAGES, emptySet()) ?: emptySet())
    }

    fun excludedSources(labelResolver: (String) -> String): List<ExcludedSource> {
        return excludedPackages()
            .map { packageName -> ExcludedSource(packageName, labelResolver(packageName)) }
            .sortedWith { left, right -> Collator.getInstance().compare(left.label, right.label) }
    }

    fun excludedLabel(packageName: String, fallback: () -> String): String {
        val savedLabel = preferences.getString(PREF_EXCLUDED_LABEL_PREFIX + packageName, null)
        if (!savedLabel.isNullOrBlank()) {
            return savedLabel
        }
        return fallback()
    }

    fun exclude(chapter: AppChapter) {
        val packages = excludedPackages().toMutableSet()
        packages.add(chapter.packageName)
        preferences.edit()
            .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
            .putString(PREF_EXCLUDED_LABEL_PREFIX + chapter.packageName, chapter.label)
            .apply()
    }

    fun restore(packageName: String) {
        val packages = excludedPackages().toMutableSet()
        packages.remove(packageName)
        preferences.edit()
            .putStringSet(PREF_EXCLUDED_PACKAGES, packages)
            .remove(PREF_EXCLUDED_LABEL_PREFIX + packageName)
            .apply()
    }

    fun useTintedNotificationCards(): Boolean {
        return preferences.getBoolean(PREF_TINT_NOTIFICATION_CARDS, false)
    }

    fun toggleNotificationSurface(): Boolean {
        val nextValue = !useTintedNotificationCards()
        preferences.edit().putBoolean(PREF_TINT_NOTIFICATION_CARDS, nextValue).apply()
        return nextValue
    }

    fun cardHapticsEnabled(): Boolean {
        return preferences.getBoolean(PREF_CARD_HAPTICS_ENABLED, true)
    }

    fun cardHapticStrength(): Int {
        return preferences.getInt(PREF_CARD_HAPTICS_STRENGTH, 2).coerceIn(1, 5)
    }

    fun setCardHapticStrength(strength: Int) {
        preferences.edit().putInt(PREF_CARD_HAPTICS_STRENGTH, strength.coerceIn(1, 5)).apply()
    }

    fun toggleCardHaptics(): Boolean {
        val nextValue = !cardHapticsEnabled()
        preferences.edit().putBoolean(PREF_CARD_HAPTICS_ENABLED, nextValue).apply()
        return nextValue
    }

    companion object {
        private const val PREFS_NAME = "calm_preferences"
        private const val PREF_EXCLUDED_PACKAGES = "excluded_notification_packages"
        private const val PREF_EXCLUDED_LABEL_PREFIX = "excluded_label_"
        private const val PREF_TINT_NOTIFICATION_CARDS = "tint_notification_cards"
        private const val PREF_CARD_HAPTICS_ENABLED = "card_haptics_enabled"
        private const val PREF_CARD_HAPTICS_STRENGTH = "card_haptics_strength"
    }
}
