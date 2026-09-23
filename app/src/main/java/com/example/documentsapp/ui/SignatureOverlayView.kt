package com.example.documentsapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2

data class PlacedSignature(
    val svgContent: String,
    val bitmap: Bitmap,
    var posX: Float,
    var posY: Float,
    var scale: Float,
    var rotation: Float,
    var isPlaced: Boolean
)

class SignatureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    val signatures = mutableListOf<PlacedSignature>()

    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val borderPaint = Paint().apply {
        color = 0xFF4CAF50.toInt() // Green
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val handlePaint = Paint().apply {
        color = 0xFF4CAF50.toInt()
        style = Paint.Style.FILL
    }
    
    private val handleSize = 20f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = -1

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val active = getActiveSignature() ?: return false
            if (active.isPlaced) return false
            active.scale *= detector.scaleFactor
            active.scale = active.scale.coerceIn(0.1f, 10.0f)
            invalidate()
            return true
        }
    })

    private var initialRotation = 0f
    private var isRotating = false

    fun getActiveSignature(): PlacedSignature? {
        return if (signatures.isNotEmpty() && !signatures.last().isPlaced) signatures.last() else null
    }

    fun addSignature(bitmap: Bitmap, svgContent: String, containerWidth: Int, containerHeight: Int) {
        val posX = (containerWidth - bitmap.width) / 2f
        val posY = (containerHeight - bitmap.height) / 2f
        val sig = PlacedSignature(svgContent, bitmap, posX, posY, 1.0f, 0f, false)
        signatures.add(sig)
        visibility = VISIBLE
        invalidate()
    }

    fun placeActiveSignature(): Boolean {
        val active = getActiveSignature() ?: return false
        active.isPlaced = true
        invalidate()
        return true
    }

    fun hasActiveUnplacedSignature(): Boolean {
        return getActiveSignature() != null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (sig in signatures) {
            val bitmap = sig.bitmap
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            
            matrix.reset()
            matrix.postTranslate(-bw / 2f, -bh / 2f)
            matrix.postScale(sig.scale, sig.scale)
            matrix.postRotate(sig.rotation)
            matrix.postTranslate(sig.posX + bw / 2f, sig.posY + bh / 2f)
            canvas.drawBitmap(bitmap, matrix, paint)
            
            if (!sig.isPlaced && sig == getActiveSignature()) {
                val corners = floatArrayOf(
                    0f, 0f,
                    bw, 0f,
                    bw, bh,
                    0f, bh
                )
                matrix.mapPoints(corners)
                
                canvas.drawLine(corners[0], corners[1], corners[2], corners[3], borderPaint)
                canvas.drawLine(corners[2], corners[3], corners[4], corners[5], borderPaint)
                canvas.drawLine(corners[4], corners[5], corners[6], corners[7], borderPaint)
                canvas.drawLine(corners[6], corners[7], corners[0], corners[1], borderPaint)
                
                for (i in 0 until 4) {
                    val cx = corners[i * 2]
                    val cy = corners[i * 2 + 1]
                    canvas.drawRect(cx - handleSize / 2, cy - handleSize / 2, cx + handleSize / 2, cy + handleSize / 2, handlePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val active = getActiveSignature() ?: return false
        if (active.isPlaced) return false

        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2) {
                    initialRotation = active.rotation - getAngle(event)
                    isRotating = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId != -1) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)

                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        active.posX += dx
                        active.posY += dy

                        lastTouchX = x
                        lastTouchY = y
                    }
                }
                
                if (pointerCount == 2 && isRotating) {
                    active.rotation = initialRotation + getAngle(event)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                isRotating = false
                performClick()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(newPointerIndex)
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                }
                if (pointerCount <= 2) {
                    isRotating = false
                }
            }
        }
        return true
    }

    private fun getAngle(event: MotionEvent): Float {
        val deltaX = (event.getX(0) - event.getX(1)).toDouble()
        val deltaY = (event.getY(0) - event.getY(1)).toDouble()
        val radians = atan2(deltaY, deltaX)
        return Math.toDegrees(radians).toFloat()
    }

    fun contains(x: Float, y: Float): Boolean {
        val active = getActiveSignature() ?: return false
        val bitmap = active.bitmap
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        
        val mat = Matrix()
        mat.postTranslate(-bw / 2f, -bh / 2f)
        mat.postScale(active.scale, active.scale)
        mat.postRotate(active.rotation)
        mat.postTranslate(active.posX + bw / 2f, active.posY + bh / 2f)
        
        val inverseMat = Matrix()
        if (mat.invert(inverseMat)) {
            val pts = floatArrayOf(x, y)
            inverseMat.mapPoints(pts)
            return pts[0] in 0f..bw && pts[1] in 0f..bh
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
