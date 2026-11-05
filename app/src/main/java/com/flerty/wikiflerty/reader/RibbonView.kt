package com.flerty.wikiflerty.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class RibbonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var top = 0xFFB0303A.toInt()
    private var bottom = 0xFFE0505A.toInt()

    fun setColors(top: Int, bottom: Int) {
        if (top == this.top && bottom == this.bottom) return
        this.top = top
        this.bottom = bottom
        updateShader()
        invalidate()
    }

    private fun updateShader() {
        if (height == 0) return
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(w.toFloat(), 0f)
        path.lineTo(w.toFloat(), h.toFloat())
        path.lineTo(w / 2f, h * 0.74f)
        path.lineTo(0f, h.toFloat())
        path.close()
        updateShader()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}
