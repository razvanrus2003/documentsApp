package com.example.documentsapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorHueSliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
    }
    
    private var hue = 0f
    private var onHueChanged: ((Float) -> Unit)? = null

    fun setOnHueChangedListener(listener: (Float) -> Unit) {
        onHueChanged = listener
    }

    fun setHue(h: Float) {
        hue = h
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val colors = IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
        val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        paint.shader = gradient
        
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        val y = hue / 360f * height
        canvas.drawRect(0f, y - 5, width.toFloat(), y + 5, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                hue = (event.y / height * 360f).coerceIn(0f, 360f)
                onHueChanged?.invoke(hue)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
