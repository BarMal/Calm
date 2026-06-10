package dev.barna.calm

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/** Renders the persistent dock — a row of favourite apps shown beneath every page. */
class DockController(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
) {
    fun buildDock(apps: List<AppEntry>, config: DockConfig): View {
        return LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.GLASS, activity.dp(28))
            val horizontal = activity.dp(config.horizontalPaddingDp)
            val vertical = activity.dp(config.verticalPaddingDp)
            setPadding(horizontal, vertical, horizontal, vertical)
            apps.take(config.itemCount).forEach { app ->
                addView(
                    dockIcon(app),
                    LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)).apply {
                        marginStart = activity.dp(6)
                        marginEnd = activity.dp(6)
                    },
                )
            }
        }
    }

    private fun dockIcon(app: AppEntry): View {
        return ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
            contentDescription = app.label
            tooltipText = app.label
            resolveIcon(app)?.let { setImageBitmap(it) }
            layoutParams = ViewGroup.LayoutParams(activity.dp(56), activity.dp(56))
            setOnClickListener { openAppEntry(app) }
        }
    }
}
