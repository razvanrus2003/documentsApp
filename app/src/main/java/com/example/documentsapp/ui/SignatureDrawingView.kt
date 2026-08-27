package com.example.documentsapp.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.sqrt

class SignatureDrawingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DrawingTool {
        PEN, PENCIL, ERASER
    }

    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)
    
    private var currentPath = Path()
    private val currentPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val paths = mutableListOf<Stroke>()
    private var currentStrokePoints = mutableListOf<PointF>()
    private var currentStrokeWidths = mutableListOf<Float>()

    private var lastX = 0f
    private var lastY = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var lastStrokeWidth = 0f
    
    // Ribbon edge tracking
    private var lastLeftX = 0f
    private var lastLeftY = 0f
    private var lastRightX = 0f
    private var lastRightY = 0f
    private var hasLastEdges = false

    // Stabilization variables
    private var stabilizedX = 0f
    private var stabilizedY = 0f
    private val STABILIZATION_FACTOR = 0.4f

    private var velocityTracker: VelocityTracker? = null
    private val pathMeasure = PathMeasure()
    
    var currentColor: Int = Color.BLACK
        set(value) {
            field = value
            updatePaint()
        }
        
    var currentSize: Float = 10f
        set(value) {
            field = value
            updatePaint()
        }

    var currentTool: DrawingTool = DrawingTool.PEN
        set(value) {
            field = value
            updatePaint()
        }

    private fun updatePaint() {
        currentPaint.color = currentColor
        currentPaint.strokeWidth = currentSize
        
        when (currentTool) {
            DrawingTool.PEN -> {
                currentPaint.xfermode = null
                currentPaint.alpha = 255
                currentPaint.pathEffect = null
                currentPaint.style = Paint.Style.STROKE
            }
            DrawingTool.PENCIL -> {
                currentPaint.xfermode = null
                currentPaint.alpha = 255
                currentPaint.pathEffect = null
                currentPaint.style = Paint.Style.FILL
            }
            DrawingTool.ERASER -> {
                currentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                currentPaint.alpha = 255
                currentPaint.pathEffect = null
                currentPaint.style = Paint.Style.STROKE
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        updatePaint()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            for (stroke in paths) {
                drawStrokeOnCanvas(stroke, drawCanvas!!)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, bitmapPaint)
        }
        
        if (currentTool == DrawingTool.PEN) {
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    private fun drawStrokeOnCanvas(stroke: Stroke, canvas: Canvas) {
        if (stroke.tool == DrawingTool.PENCIL) {
            val paint = Paint(stroke.paint).apply { style = Paint.Style.FILL }
            if (stroke.points.size < 2) return
            
            val ribbonPath = Path()
            val prevL = PointF(0f, 0f)
            val prevR = PointF(0f, 0f)
            
            for (i in 0 until stroke.points.size) {
                val p = stroke.points[i]
                val w = stroke.widths.getOrElse(i) { stroke.width }
                
                // Estimate tangent
                val next = stroke.points.getOrNull(i + 1) ?: p
                val prev = stroke.points.getOrNull(i - 1) ?: p
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                
                // Normal vector
                val nx = -dy / dist
                val ny = dx / dist
                
                val lx = p.x + nx * (w / 2f)
                val ly = p.y + ny * (w / 2f)
                val rx = p.x - nx * (w / 2f)
                val ry = p.y - ny * (w / 2f)
                
                if (i > 0) {
                    ribbonPath.reset()
                    ribbonPath.moveTo(prevL.x, prevL.y)
                    ribbonPath.lineTo(lx, ly)
                    ribbonPath.lineTo(rx, ry)
                    ribbonPath.lineTo(prevR.x, prevR.y)
                    ribbonPath.close()
                    canvas.drawPath(ribbonPath, paint)
                    // Also draw a circle to fill gaps in joints
                    canvas.drawCircle(p.x, p.y, w / 2f, paint)
                } else {
                    canvas.drawCircle(p.x, p.y, w / 2f, paint)
                }
                prevL.set(lx, ly)
                prevR.set(rx, ry)
            }
        } else {
            canvas.drawPath(stroke.path, stroke.paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rawX = event.x
        val rawY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                
                stabilizedX = rawX
                stabilizedY = rawY
                lastX = rawX
                lastY = rawY
                lastMidX = rawX
                lastMidY = rawY
                lastStrokeWidth = currentSize
                hasLastEdges = false

                currentPath.reset()
                currentPath.moveTo(rawX, rawY)
                
                currentStrokePoints.add(PointF(rawX, rawY))
                currentStrokeWidths.add(lastStrokeWidth)
                
                if (currentTool == DrawingTool.PENCIL) {
                    drawCanvas?.drawCircle(rawX, rawY, lastStrokeWidth / 2f, currentPaint)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                
                stabilizedX = stabilizedX * STABILIZATION_FACTOR + rawX * (1f - STABILIZATION_FACTOR)
                stabilizedY = stabilizedY * STABILIZATION_FACTOR + rawY * (1f - STABILIZATION_FACTOR)

                val velocity = sqrt(pow(velocityTracker?.xVelocity ?: 0f) + pow(velocityTracker?.yVelocity ?: 0f))
                val targetWidth = (currentSize * (1.2f - (velocity / 3000f).coerceIn(0f, 0.9f)))
                val width = lastStrokeWidth * 0.6f + targetWidth * 0.4f
                
                val midX = (lastX + stabilizedX) / 2
                val midY = (lastY + stabilizedY) / 2
                
                if (currentTool == DrawingTool.PENCIL) {
                    val segmentPath = Path()
                    segmentPath.moveTo(lastMidX, lastMidY)
                    segmentPath.quadTo(lastX, lastY, midX, midY)
                    
                    pathMeasure.setPath(segmentPath, false)
                    val length = pathMeasure.length
                    val step = 1.0f 
                    var d = 0f
                    val pos = FloatArray(2)
                    val tan = FloatArray(2)
                    val quadPath = Path()
                    
                    while (d <= length) {
                        pathMeasure.getPosTan(d, pos, tan)
                        val t = if (length == 0f) 0f else d / length
                        val w = lastStrokeWidth + (width - lastStrokeWidth) * t
                        
                        val px = pos[0]
                        val py = pos[1]
                        val tx = tan[0]
                        val ty = tan[1]
                        
                        // Normal vector
                        val nx = -ty
                        val ny = tx
                        
                        val lx = px + nx * (w / 2f)
                        val ly = py + ny * (w / 2f)
                        val rx = px - nx * (w / 2f)
                        val ry = py - ny * (w / 2f)
                        
                        if (hasLastEdges) {
                            quadPath.reset()
                            quadPath.moveTo(lastLeftX, lastLeftY)
                            quadPath.lineTo(lx, ly)
                            quadPath.lineTo(rx, ry)
                            quadPath.lineTo(lastRightX, lastRightY)
                            quadPath.close()
                            drawCanvas?.drawPath(quadPath, currentPaint)
                            drawCanvas?.drawCircle(px, py, w / 2f, currentPaint)
                        }
                        
                        currentStrokePoints.add(PointF(px, py))
                        currentStrokeWidths.add(w)
                        
                        lastLeftX = lx
                        lastLeftY = ly
                        lastRightX = rx
                        lastRightY = ry
                        hasLastEdges = true
                        
                        if (d == length) break
                        d = (d + step).coerceAtMost(length)
                    }
                } else if (currentTool == DrawingTool.ERASER) {
                    currentPaint.strokeWidth = currentSize
                    drawCanvas?.drawLine(lastMidX, lastMidY, midX, midY, currentPaint)
                    drawCanvas?.drawCircle(midX, midY, currentSize / 2f, currentPaint)
                } else {
                    currentPath.quadTo(lastX, lastY, midX, midY)
                    currentStrokePoints.add(PointF(midX, midY))
                    currentStrokeWidths.add(width)
                }
                
                lastX = stabilizedX
                lastY = stabilizedY
                lastMidX = midX
                lastMidY = midY
                lastStrokeWidth = width
            }
            MotionEvent.ACTION_UP -> {
                if (currentTool == DrawingTool.PEN) {
                    currentPath.lineTo(rawX, rawY)
                    drawCanvas?.drawPath(currentPath, currentPaint)
                }
                
                val isEraser = currentTool == DrawingTool.ERASER
                paths.add(Stroke(
                    Path(currentPath), 
                    Paint(currentPaint), 
                    currentColor, 
                    currentSize, 
                    isEraser, 
                    ArrayList(currentStrokePoints), 
                    ArrayList(currentStrokeWidths),
                    currentTool
                ))
                
                currentPath.reset()
                currentStrokePoints.clear()
                currentStrokeWidths.clear()
                velocityTracker?.recycle()
                velocityTracker = null
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun pow(v: Float) = v * v

    fun clear() {
        paths.clear()
        currentPath.reset()
        canvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun getStrokes(): List<Stroke> = paths

    data class Stroke(
        val path: Path,
        val paint: Paint,
        val color: Int,
        val width: Float,
        val isEraser: Boolean,
        val points: List<PointF>,
        val widths: List<Float>,
        val tool: DrawingTool = DrawingTool.PEN
    )
}
