package com.example.documentsapp.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class ZoomableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 5.0f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (scaleFactor > 1.0f) {
                translateX -= distanceX
                translateY -= distanceY
                
                val maxTranslateX = (width * scaleFactor - width) / 2f
                val maxTranslateY = (height * scaleFactor - height) / 2f
                
                translateX = translateX.coerceIn(-maxTranslateX, maxTranslateX)
                translateY = translateY.coerceIn(-maxTranslateY, maxTranslateY)
                
                invalidate()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleFactor > 1.0f) {
                scaleFactor = 1.0f
                translateX = 0f
                translateY = 0f
            } else {
                scaleFactor = 2.0f
            }
            invalidate()
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        
        // Crucial: Let children (RecyclerView) handle their touches, 
        // but tell parent we are interested if we are zoomed in.
        val handled = super.dispatchTouchEvent(ev)
        return handled || scaleFactor > 1.0f
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
