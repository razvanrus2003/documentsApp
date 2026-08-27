package com.example.documentsapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detectedPoints: List<PointF>? = null
    private var smoothedPoints: List<PointF>? = null
    private var isDetected = false
    
    private val backgroundPaint = Paint().apply {
        color = 0x80000000.toInt()
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        alpha = 128
    }
    
    private val pointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 128
    }

    fun setDetectionState(detected: Boolean) {
        this.isDetected = detected
        if (detected) {
            linePaint.color = Color.GREEN
            linePaint.strokeWidth = 8f
            linePaint.alpha = 255
            pointPaint.color = Color.GREEN
            pointPaint.alpha = 255
        } else {
            linePaint.color = Color.WHITE
            linePaint.strokeWidth = 4f
            linePaint.alpha = 128
            pointPaint.color = Color.WHITE
            pointPaint.alpha = 128
        }
        invalidate()
    }

    fun updateDetectedPoints(points: List<PointF>?) {
        if (points == null) {
            detectedPoints = null
            smoothedPoints = null
        } else {
            detectedPoints = points
            // Directly use the points from DocumentDetector
            // Detector now handles locking and smoothing much more accurately
            smoothedPoints = points
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw detection quadrilateral if present
        smoothedPoints?.let { points ->
            if (points.size >= 4) {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                path.close()
                canvas.drawPath(path, linePaint)
                
                // Draw circles at corners
                for (point in points) {
                    canvas.drawCircle(point.x, point.y, 12f, pointPaint)
                }
            }
        }
    }
}
