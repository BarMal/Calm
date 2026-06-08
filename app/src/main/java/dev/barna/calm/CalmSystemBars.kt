package dev.barna.calm

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

object CalmSystemBars {
    fun applyTransparentWallpaper(activity: ComponentActivity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
    }

    fun applySettingsWindow(activity: ComponentActivity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        activity.window.setBackgroundDrawable(ColorDrawable(CalmTheme.SURFACE))
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(CalmTheme.SURFACE),
        )
    }
}
