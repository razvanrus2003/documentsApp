package com.example.documentsapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SaturationValueMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
    }
    
    private var hue = 0f
    private var saturation = 0f
    private var value = 0f
    
    private var onSaturationValueChanged: ((Float, Float) -> Unit)? = null

    fun setOnSaturationValueChangedListener(listener: (Float, Float) -> Unit) {
        onSaturationValueChanged = listener
    }

    fun setHSV(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        value = v
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        val colors = intArrayOf(Color.WHITE, Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        val satGradient = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.CLAMP)
        
        val valColors = intArrayOf(Color.TRANSPARENT, Color.BLACK)
        val valGradient = LinearGradient(0f, 0f, 0f, h, valColors, null, Shader.TileMode.CLAMP)
        
        paint.shader = satGradient
        canvas.drawRect(0f, 0f, w, h, paint)
        
        paint.shader = valGradient
        canvas.drawRect(0f, 0f, w, h, paint)
        
        val x = saturation * w
        val y = (1f - value) * h
        canvas.drawCircle(x, y, 10f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                saturation = (event.x / width).coerceIn(0f, 1f)
                value = (1f - (event.y / height)).coerceIn(0f, 1f)
                onSaturationValueChanged?.invoke(saturation, value)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
