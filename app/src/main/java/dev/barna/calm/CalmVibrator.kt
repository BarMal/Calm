package dev.barna.calm

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

object CalmVibrator {
    fun defaultVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            legacyVibrator(context)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyVibrator(context: Context): Vibrator? {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
