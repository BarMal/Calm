package dev.barna.calm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

object SafeActivityLauncher {
    fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    fun startOrToast(context: Context, intent: Intent, failureMessage: String): Boolean {
        val launched = start(context, intent)
        if (!launched) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
        return launched
    }

    fun startAnyOrToast(context: Context, intents: List<Intent>, failureMessage: String): Boolean {
        val launched = intents.any { intent -> start(context, intent) }
        if (!launched) {
            Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
        }
        return launched
    }
}
