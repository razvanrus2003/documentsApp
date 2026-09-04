package com.example.documentsapp.utils

import android.graphics.PointF
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.geometry.Geometry
import kotlin.math.abs
import kotlin.math.sqrt

class DocumentDetector {
    private var currentQuad: List<PointF>? = null
    var isDetected: Boolean = false
    
    private val hierarchy = Mat()
    private val work = Mat()
    private val binary = Mat()
    private val blurred = Mat()
    private val kernel = Mat.ones(3, 3, CvType.CV_8U)

    data class DetectionResult(val points: List<PointF>, val isDetected: Boolean)

    fun detect(image: Mat): DetectionResult {
        val width = image.cols()
        val height = image.rows()
        
        val candidates = mutableListOf<QuadCandidate>()
        
        // Split RGB into individual channels
        val channels = mutableListOf<Mat>()
        Core.split(image, channels)
        
        for (channel in channels) {
            // Speed optimization: median blur to reduce noise
            Imgproc.medianBlur(channel, blurred, 5)
            
            // --- Step 1: Binary Segmentation (Original Logic) ---
            Imgproc.threshold(blurred, binary, 160.0, 255.0, Imgproc.THRESH_BINARY)
            
            // Morphological sequence: dilate(2) -> erode(2) -> dilate(2)
            Imgproc.dilate(binary, work, kernel, Point(-1.0, -1.0), 1)
            Imgproc.erode(work, binary, kernel, Point(-1.0, -1.0), 1)
            Imgproc.dilate(binary, work, kernel, Point(-1.0, -1.0), 1)
            
            findSquares(work, candidates, 3000000, width, height)
            
            // --- Step 2: Iterative Canny (Original Logic) ---
            val cannySteps = intArrayOf(100, 60, 20)
            for (t in cannySteps) {
                if (checkEarlyExit(candidates, width, height)) break
                
                Imgproc.Canny(blurred, binary, t.toDouble(), (t * 2).toDouble())
                // Thicken Canny lines to ensure they form closed loops
                Imgproc.dilate(binary, binary, kernel)
                
                findSquares(binary, candidates, 2000000 - t, width, height)
            }
            
            if (checkEarlyExit(candidates, width, height)) break
        }
        
        // Clean up channels
        channels.forEach { it.release() }
        
        // Sort by sortFactor (higher is better)
        candidates.sortByDescending { it.sortFactor }
        
        val bestCandidate = candidates.firstOrNull()
        val guideQuad = getDefaultGuideQuad(width, height)

        if (bestCandidate != null) {
            // Apply Low-Pass stabilization (EMA) to reduce oscillations
            // Alpha 0.7 ensures it snaps fast but filters high-frequency jitter
            val targetPoints = bestCandidate.points
            currentQuad = if (currentQuad == null || !isDetected) {
                targetPoints
            } else {
                smoothQuadrilateral(currentQuad!!, targetPoints, 0.7f)
            }
            isDetected = true
        } else {
            // Gradually return to guide quad instead of instant snap back to center
            currentQuad = if (currentQuad != null) {
                smoothQuadrilateral(currentQuad!!, guideQuad, 0.2f)
            } else {
                guideQuad
            }
            isDetected = false
        }

        return DetectionResult(currentQuad!!, isDetected)
    }

    private fun findSquares(binary: Mat, candidates: MutableList<QuadCandidate>, weight: Int, width: Int, height: Int) {
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = (width * height).toDouble()
        
        for (contour in contours) {
            // Pre-filter by raw area for performance
            val areaRaw = Geometry.contourArea(contour)
            if (areaRaw < imageArea * 0.05) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val approx2f = MatOfPoint2f()
            
            val peri = Geometry.arcLength(contour2f, true)
            // standard quadrilateral approximation
            Geometry.approxPolyDP(contour2f, approx2f, 0.02 * peri, true)
            
            val approxArray = approx2f.toArray()
            if (approxArray.size == 4) {
                val points = approxArray.map { PointF(it.x.toFloat(), it.y.toFloat()) }
                val area = abs(Geometry.contourArea(approx2f))
                
                // Final safety area check (between 5% and 95% - reduced from 99% to avoid desk borders)
                if (area > imageArea * 0.05 && area < imageArea * 0.95) {
                    if (Geometry.isContourConvex(MatOfPoint(*approxArray))) {
                        val maxCos = calculateMaxCosine(points)
                        
                        // Tightened constraint from 0.5 to 0.3 to favor real documents over random background shapes
                        if (maxCos < 0.3) { 
                            val meanCos = calculateMeanCosine(points)
                            // Refined sort factor: squareness is now weighted much more heavily than raw size
                            val sortFactor = area * (1.0 - maxCos * 2.0) + weight.toDouble() * 0.1
                            candidates.add(QuadCandidate(points, area, maxCos, meanCos, weight, sortFactor))
                        }
                    }
                }
            }
        }
    }

    private fun checkEarlyExit(candidates: List<QuadCandidate>, width: Int, height: Int): Boolean {
        val best = candidates.maxByOrNull { it.sortFactor } ?: return false
        val imageArea = (width * height).toDouble()
        // Stop early only if we have a very good, centered candidate
        return best.maxCosine < 0.15 && best.area > imageArea * 0.4
    }

    private fun calculateMaxCosine(points: List<PointF>): Double {
        var maxCos = 0.0
        for (i in 0 until 4) {
            val cos = abs(calculateCosine(points[(i + 3) % 4], points[i], points[(i + 1) % 4]))
            if (cos > maxCos) maxCos = cos
        }
        return maxCos
    }

    private fun calculateMeanCosine(points: List<PointF>): Double {
        var sumCos = 0.0
        for (i in 0 until 4) {
            sumCos += abs(calculateCosine(points[(i + 3) % 4], points[i], points[(i + 1) % 4]))
        }
        return sumCos / 4.0
    }

    private fun calculateCosine(p1: PointF, p2: PointF, p3: PointF): Double {
        val dx1 = (p1.x - p2.x).toDouble()
        val dy1 = (p1.y - p2.y).toDouble()
        val dx2 = (p3.x - p2.x).toDouble()
        val dy2 = (p3.y - p2.y).toDouble()
        
        val dot = dx1 * dx2 + dy1 * dy2
        val len1 = sqrt(dx1 * dx1 + dy1 * dy1)
        val len2 = sqrt(dx2 * dx2 + dy2 * dy2)
        
        if (len1 < 1e-6 || len2 < 1e-6) return 1.0
        return dot / (len1 * len2)
    }

    private fun smoothQuadrilateral(current: List<PointF>, target: List<PointF>, alpha: Float): List<PointF> {
        return current.zip(target).map { (c, t) ->
            PointF(c.x * (1 - alpha) + t.x * alpha, c.y * (1 - alpha) + t.y * alpha)
        }
    }

    fun getDefaultGuideQuad(width: Int, height: Int): List<PointF> {
        val marginW = width * 0.15f
        val marginH = height * 0.15f
        return listOf(
            PointF(marginW, marginH),
            PointF(width - marginW, marginH),
            PointF(width - marginW, height - marginH),
            PointF(marginW, height - marginH),
        )
    }

    private data class QuadCandidate(
        val points: List<PointF>,
        val area: Double,
        val maxCosine: Double,
        val meanCosine: Double,
        val weight: Int,
        val sortFactor: Double
    )
}
