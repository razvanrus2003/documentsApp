package com.example.documentsapp.utils

import android.graphics.PointF
import boofcv.alg.enhance.EnhanceImageOps
import boofcv.alg.feature.detect.edge.CannyEdge
import boofcv.alg.filter.blur.BlurImageOps
import boofcv.alg.filter.binary.BinaryImageOps
import boofcv.alg.filter.binary.Contour
import boofcv.alg.shapes.ShapeFittingOps
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors
import boofcv.struct.ConnectRule
import boofcv.struct.image.GrayS16
import boofcv.struct.image.GrayU8

class DocumentDetector {

    private var currentQuad: List<PointF>? = null
    private var isDetected = false
    private var trackingCounter = 0
    private var lockCounter = 0
    private var lastAspect = 1.41f // Default A4 aspect ratio
    
    private var canny: CannyEdge<GrayU8, GrayS16>? = null
    private var edgeImage: GrayU8? = null
    private var enhanced: GrayU8? = null
    
    data class DetectionResult(val points: List<PointF>, val isDetected: Boolean)

    fun detect(gray: GrayU8): DetectionResult {
        val width = gray.width
        val height = gray.height
        
        // Determine if we should use ROI (Search Window) for stability
        val useROI = isDetected && currentQuad != null && lockCounter > 5
        val roi = if (useROI) {
            val minX = currentQuad!!.minOf { it.x }.toInt()
            val minY = currentQuad!!.minOf { it.y }.toInt()
            val maxX = currentQuad!!.maxOf { it.x }.toInt()
            val maxY = currentQuad!!.maxOf { it.y }.toInt()
            val paddingX = (maxX - minX) / 5
            val paddingY = (maxY - minY) / 5
            android.graphics.Rect(
                Math.max(0, minX - paddingX),
                Math.max(0, minY - paddingY),
                Math.min(width, maxX + paddingX),
                Math.min(height, maxY + paddingY)
            )
        } else {
            null
        }

        val workImage = if (roi != null && roi.width() > 10 && roi.height() > 10) {
            gray.subimage(roi.left, roi.top, roi.right, roi.bottom, null)
        } else {
            gray
        }

        if (canny == null || edgeImage == null || edgeImage!!.width != workImage.width || edgeImage!!.height != workImage.height) {
            canny = FactoryEdgeDetectors.canny(2, true, true, GrayU8::class.java, GrayS16::class.java)
            edgeImage = GrayU8(workImage.width, workImage.height)
            enhanced = GrayU8(workImage.width, workImage.height)
            if (currentQuad == null) currentQuad = getDefaultGuideQuad(width, height)
            isDetected = false
        }
        
        // Fast local enhancement
        EnhanceImageOps.equalizeLocal(workImage, 3, enhanced!!, 256, null)
        
        val blurred = enhanced!!.createSameShape()
        BlurImageOps.gaussian(enhanced!!, blurred, -1.0, 1, null)
        canny!!.process(blurred, 0.2f, 0.4f, edgeImage!!)

        val contours: List<Contour> = BinaryImageOps.contourExternal(edgeImage!!, ConnectRule.EIGHT)
        val candidates = mutableListOf<QuadCandidate>()
        val minContourLength = (workImage.width + workImage.height) * 0.3

        for (contour in contours) {
            if (contour.external.size < minContourLength) continue
            
            // New Robust Strategy: Find the 4 extreme corners of the contour
            var quad = findExtremeCorners(contour.external.map { p -> PointF(p.x.toFloat(), p.y.toFloat()) })
            
            // Map back to global coordinates if in ROI mode
            if (roi != null) {
                quad = quad.map { PointF(it.x + roi.left, it.y + roi.top) }
            }

            if (isValidQuadrilateral(quad, width, height)) {
                candidates.add(QuadCandidate(quad, calculateScore(quad, width, height)))
            }
        }

        val bestCandidate = candidates.maxByOrNull { it.score }?.points
        val guideQuad = getDefaultGuideQuad(width, height)

        if (bestCandidate != null) {
            // Update aspect ratio memory
            val wA = calculateDistance(listOf(bestCandidate[0]), listOf(bestCandidate[1]))
            val hA = calculateDistance(listOf(bestCandidate[1]), listOf(bestCandidate[2]))
            if (hA > 0) {
                val currentAspect = (wA / hA).toFloat()
                lastAspect = lastAspect * 0.9f + currentAspect * 0.1f // Smooth aspect learning
            }

            // Lock-on logic and adaptive smoothing
            val shift = currentQuad?.let { calculateDistance(it, bestCandidate) } ?: 100.0
            
            // If movement is very tiny (jitter), don't update to stay solid
            if (shift < 5.0 && isDetected) {
                // Keep currentQuad as is
            } else {
                lockCounter++
                // If we've been on the document for > 10 frames, use heavy smoothing (0.15f) for a solid lock
                // Otherwise use fast snapping (0.6f)
                val alpha = if (lockCounter > 10) 0.15f else 0.6f
                currentQuad = smoothQuadrilateral(currentQuad ?: guideQuad, bestCandidate, alpha)
            }
            
            isDetected = true
            trackingCounter = 0
        } else {
            trackingCounter++
            lockCounter = 0
            if (trackingCounter > 10) {
                currentQuad = smoothQuadrilateral(currentQuad ?: guideQuad, guideQuad, 0.1f)
                isDetected = false
            } else {
                isDetected = false
            }
        }

        return DetectionResult(currentQuad ?: guideQuad, isDetected)
    }

    private fun getDefaultGuideQuad(width: Int, height: Int): List<PointF> {
        val centerX = width / 2f
        val centerY = height / 2f
        
        val guideWidth = width * 0.6f
        val guideHeight = guideWidth / lastAspect
        
        val finalHeight = if (guideHeight > height * 0.8f) height * 0.8f else guideHeight
        val finalWidth = if (finalHeight * lastAspect > width * 0.8f) width * 0.8f else finalHeight * lastAspect

        val halfW = finalWidth / 2f
        val halfH = finalHeight / 2f
        
        return listOf(
            PointF(centerX - halfW, centerY - halfH),
            PointF(centerX + halfW, centerY - halfH),
            PointF(centerX + halfW, centerY + halfH),
            PointF(centerX - halfW, centerY + halfH)
        )
    }

    private fun isValidQuadrilateral(points: List<PointF>, width: Int, height: Int): Boolean {
        if (!isConvex(points)) return false
        
        val area = calculateArea(points)
        val frameArea = width.toDouble() * height.toDouble()
        
        if (area < (frameArea * 0.03)) return false
        
        val minCornerDist = Math.min(width, height) * 0.1
        for (i in 0 until 4) {
            val p1 = points[i]
            val p2 = points[(i + 1) % 4]
            val d = Math.sqrt(Math.pow((p1.x - p2.x).toDouble(), 2.0) + Math.pow((p1.y - p2.y).toDouble(), 2.0))
            if (d < minCornerDist) return false
        }
        
        return true
    }

    private fun calculateScore(points: List<PointF>, width: Int, height: Int): Double {
        val area = calculateArea(points)
        val areaScore = area / (width * height)
        var proximityScore = 0.0
        currentQuad?.let { curr ->
            val dist = calculateDistance(curr, points)
            proximityScore = 1.0 / (1.0 + dist * 0.01)
        }
        return areaScore * 0.4 + proximityScore * 0.6
    }

    private fun calculateArea(points: List<PointF>): Double {
        var area = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return Math.abs(area) / 2.0
    }

    private fun isConvex(points: List<PointF>): Boolean {
        var sign = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val p3 = points[(i + 2) % points.size]
            val cp = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x)
            if (sign == 0.0) {
                sign = Math.signum(cp).toDouble()
            } else if (cp != 0f && Math.signum(cp).toDouble() != sign) {
                return false
            }
        }
        return true
    }

    private fun calculateDistance(q1: List<PointF>, q2: List<PointF>): Double {
        var totalDist = 0.0
        for (i in 0 until Math.min(q1.size, q2.size)) {
            totalDist += Math.sqrt(Math.pow((q1[i].x - q2[i].x).toDouble(), 2.0) + Math.pow((q1[i].y - q2[i].y).toDouble(), 2.0))
        }
        return totalDist
    }

    private fun smoothQuadrilateral(prev: List<PointF>, current: List<PointF>, alpha: Float): List<PointF> {
        return current.mapIndexed { index, pointF ->
            PointF(
                prev[index].x * (1 - alpha) + pointF.x * alpha,
                prev[index].y * (1 - alpha) + pointF.y * alpha
            )
        }
    }

    private fun findExtremeCorners(points: List<PointF>): List<PointF> {
        if (points.isEmpty()) return emptyList()
        
        // Pick points that maximize/minimize sum and difference
        // To be stable, we pick the top 3 extreme points in each direction and average them
        fun getStableExtreme(selector: (PointF) -> Float, findMax: Boolean): PointF {
            val sorted = if (findMax) points.sortedByDescending(selector) else points.sortedBy(selector)
            val topK = sorted.take(Math.min(3, sorted.size))
            var avgX = 0f; var avgY = 0f
            for (p in topK) { avgX += p.x; avgY += p.y }
            return PointF(avgX / topK.size, avgY / topK.size)
        }
        
        val tl = getStableExtreme({ it.x + it.y }, false)
        val br = getStableExtreme({ it.x + it.y }, true)
        val bl = getStableExtreme({ it.x - it.y }, false)
        val tr = getStableExtreme({ it.x - it.y }, true)
        
        return listOf(tl, tr, br, bl)
    }

    private data class QuadCandidate(val points: List<PointF>, val score: Double)
}
