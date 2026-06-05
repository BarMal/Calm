package dev.barna.calm

import android.view.View

class FocusDisplacement(view: View) {
    @JvmField val view: View = view
    @JvmField val alpha: Float = view.alpha
    @JvmField val translationY: Float = view.translationY
}
