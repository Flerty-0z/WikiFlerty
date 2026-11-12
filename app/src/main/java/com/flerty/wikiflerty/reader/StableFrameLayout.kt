package com.flerty.wikiflerty.reader

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class StableFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private var stableWidth = 0
    private var stableHeight = 0

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val ime = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val covered = !hasWindowFocus() && stableHeight - height > resources.displayMetrics.density * 120
        val keep = (ime || covered) && width == stableWidth && height < stableHeight && stableHeight > 0
        if (!keep) {
            stableWidth = width
            stableHeight = height
        }
        if (keep && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(stableHeight, MeasureSpec.EXACTLY))
            setMeasuredDimension(measuredWidth, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
