package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
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
            val size = activity.dp(config.iconSizeDp)
            val spacing = activity.dp(config.iconSpacingDp)
            val corner = activity.dp(config.iconCornerRadiusDp).toFloat()
            apps.take(config.itemCount).forEach { app ->
                addView(
                    dockIcon(app, corner),
                    LinearLayout.LayoutParams(size, size).apply {
                        marginStart = spacing
                        marginEnd = spacing
                    },
                )
            }
        }
    }

    private fun dockIcon(app: AppEntry, cornerRadius: Float): View {
        return ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null
            contentDescription = app.label
            tooltipText = app.label
            resolveIcon(app)?.let { setImageBitmap(it) }
            if (cornerRadius > 0f) {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                    }
                }
            }
            setOnClickListener { openAppEntry(app) }
        }
    }
}
