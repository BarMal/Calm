package dev.barna.calm

import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView

/** Renders the persistent dock — a row of favourite apps shown beneath every page. */
class DockController(
    private val activity: MainActivity,
    private val drawables: CalmDrawables,
    private val resolveIcon: (AppEntry) -> Bitmap?,
    private val openAppEntry: (AppEntry) -> Unit,
) {
    fun buildDock(apps: List<AppEntry>, config: DockConfig): View {
        val row = LinearLayout(activity).apply {
            tag = CalmAnimationTags.CHROME
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            apps.take(config.itemCount).forEach { app ->
                val width = DockConfig.itemWidthDp(config.itemSpan)
                addView(
                    dockItem(app, config),
                    LinearLayout.LayoutParams(activity.dp(width), activity.dp(56)).apply {
                        marginStart = activity.dp(6)
                        marginEnd = activity.dp(6)
                    },
                )
            }
        }
        return HorizontalScrollView(activity).apply {
            tag = CalmAnimationTags.CHROME
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            background = drawables.glass(CalmTheme.GLASS, activity.dp(28))
            val horizontal = activity.dp(config.horizontalPaddingDp)
            val vertical = activity.dp(config.verticalPaddingDp)
            setPadding(horizontal, vertical, horizontal, vertical)
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dockItem(app: AppEntry, config: DockConfig): View {
        return if (DockConfig.showsItemLabels(config.itemSpan)) {
            dockCard(app)
        } else {
            dockIcon(app)
        }
    }

    private fun dockIcon(app: AppEntry): View {
        return ImageButton(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6))
            contentDescription = app.label
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(14).toFloat()))
            }
            layoutParams = ViewGroup.LayoutParams(activity.dp(56), activity.dp(56))
            setOnClickListener { openAppEntry(app) }
        }
    }

    private fun dockCard(app: AppEntry): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = drawables.glass(CalmTheme.QUIET_GLASS, activity.dp(18))
            setPadding(activity.dp(8), activity.dp(6), activity.dp(10), activity.dp(6))
            contentDescription = app.label
            tooltipText = app.label
            resolveIcon(app)?.let { icon ->
                addView(
                    ImageView(activity).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageDrawable(RoundedBitmapDrawable(icon, activity.dp(12).toFloat()))
                    },
                    LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)).apply {
                        marginEnd = activity.dp(8)
                    },
                )
            }
            addView(
                TextView(activity).apply {
                    text = app.label
                    setTextColor(CalmTheme.INK)
                    textSize = 13f
                    typeface = Typeface.DEFAULT
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            setOnClickListener { openAppEntry(app) }
        }
    }
}
