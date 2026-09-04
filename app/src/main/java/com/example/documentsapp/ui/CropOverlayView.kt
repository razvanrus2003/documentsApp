package com.example.documentsapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = mutableListOf<PointF>()
    private var selectedPointIndex = -1
    private val touchThreshold = 60f
    
    private var backgroundBitmap: Bitmap? = null
    private val magnifierSize = 350f
    private val zoomFactor = 4f
    
    private var bitmapScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }
    
    private val path = Path()
    private val magnifierPath = Path()
    private val magnifierBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val magnifierMatrix = Matrix()
    private val crosshairPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
    }

    fun setInitialPoints(width: Float, height: Float) {
        points.clear()
        // Default rectangle: 10% margin
        val marginW = width * 0.1f
        val marginH = height * 0.1f
        points.add(PointF(marginW, marginH))
        points.add(PointF(width - marginW, marginH))
        points.add(PointF(width - marginW, height - marginH))
        points.add(PointF(marginW, height - marginH))
        invalidate()
    }

    fun getPoints(): List<PointF> = points

    fun setBitmap(bitmap: Bitmap, scale: Float, offX: Float, offY: Float) {
        this.backgroundBitmap = bitmap
        this.bitmapScale = scale
        this.offsetX = offX
        this.offsetY = offY
        invalidate()
    }

    /**
     * Returns the current selection points mapped to the background bitmap's coordinates.
     */
    fun getPointsInBitmap(): List<PointF> {
        if (backgroundBitmap == null) return points
        return points.map { p ->
            PointF(
                (p.x - offsetX) / bitmapScale,
                (p.y - offsetY) / bitmapScale
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 4) return

        path.reset()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        path.close()
        canvas.drawPath(path, paint)

        for (point in points) {
            canvas.drawCircle(point.x, point.y, 20f, pointPaint)
        }

        // Draw Magnifier
        if (selectedPointIndex != -1 && backgroundBitmap != null) {
            val point = points[selectedPointIndex]
            val magnifierX = point.x
            val magnifierY = point.y - magnifierSize * 0.6f // Closer to finger

            // Map screen point to bitmap point
            val bitmapX = (point.x - offsetX) / bitmapScale
            val bitmapY = (point.y - offsetY) / bitmapScale

            canvas.save()
            magnifierPath.reset()
            magnifierPath.addCircle(magnifierX, magnifierY, magnifierSize / 2, Path.Direction.CW)
            canvas.clipPath(magnifierPath)
            
            canvas.drawColor(Color.BLACK) // Better contrast background
            
            // Draw zoomed portion
            magnifierMatrix.reset()
            // 1. Move point to origin
            magnifierMatrix.postTranslate(-bitmapX, -bitmapY)
            // 2. Zoom
            magnifierMatrix.postScale(zoomFactor, zoomFactor)
            // 3. Move to magnifier center on screen
            magnifierMatrix.postTranslate(magnifierX, magnifierY)
            
            canvas.drawBitmap(backgroundBitmap!!, magnifierMatrix, paint) // Reuse paint for smoothing
            canvas.restore()
            
            // Draw magnifier border
            canvas.drawCircle(magnifierX, magnifierY, magnifierSize / 2, magnifierBorderPaint)
            
            // Draw crosshair
            canvas.drawLine(magnifierX - 20f, magnifierY, magnifierX + 20f, magnifierY, crosshairPaint)
            canvas.drawLine(magnifierX, magnifierY - 20f, magnifierX, magnifierY + 20f, crosshairPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedPointIndex = findNearestPoint(event.x, event.y)
                return selectedPointIndex != -1
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedPointIndex != -1) {
                    points[selectedPointIndex].set(event.x, event.y)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                selectedPointIndex = -1
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findNearestPoint(x: Float, y: Float): Int {
        var minIndex = -1
        var minDist = Float.MAX_VALUE
        for (i in points.indices) {
            val dist = Math.sqrt(Math.pow((points[i].x - x).toDouble(), 2.0) + Math.pow((points[i].y - y).toDouble(), 2.0)).toFloat()
            if (dist < touchThreshold && dist < minDist) {
                minDist = dist
                minIndex = i
            }
        }
        return minIndex
    }
}
