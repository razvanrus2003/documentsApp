package com.example.documentsapp.utils

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.geometry.Geometry
import kotlin.math.*

object ImageUtils {
    
    private const val TARGET_WIDTH = 1240
    private const val TARGET_HEIGHT = 1754
    
    fun applyEnhancements(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        
        // Convert to LAB color space to enhance L channel (luminance)
        val lab = Mat()
        Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGB2Lab)
        
        val channels = mutableListOf<Mat>()
        Core.split(lab, channels)
        
        // Apply CLAHE to the L channel
        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        clahe.apply(channels[0], channels[0])
        
        Core.merge(channels, lab)
        Imgproc.cvtColor(lab, src, Imgproc.COLOR_Lab2RGB)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src, result)
        
        // Cleanup
        src.release()
        lab.release()
        channels.forEach { it.release() }
        
        return result
    }

    fun applyGrayScaleFilter(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        
        // Thresholding for B&W look
        val bw = Mat()
        Imgproc.adaptiveThreshold(gray, bw, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Imgproc.cvtColor(bw, src, Imgproc.COLOR_GRAY2RGB)
        Utils.matToBitmap(src, result)
        
        src.release()
        gray.release()
        bw.release()
        
        return result
    }

    fun ensurePortraitOrientation(bitmap: Bitmap): Bitmap {
        if (bitmap.width > bitmap.height) {
            return rotateBitmap(bitmap, 90)
        }
        return bitmap
    }

    /**
     * Warps the given bitmap based on the 4 provided corners to produce a rectangular image.
     * corners should be in order: Top-Left, Top-Right, Bottom-Right, Bottom-Left
     */
    fun deskewBitmap(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        val sorted = sortCorners(corners)
        
        // Calculate the width and height of the new image
        val widthA = sqrt((sorted[2].x - sorted[3].x).toDouble().pow(2.0) + (sorted[2].y - sorted[3].y).toDouble().pow(2.0))
        val widthB = sqrt((sorted[1].x - sorted[0].x).toDouble().pow(2.0) + (sorted[1].y - sorted[0].y).toDouble().pow(2.0))
        val maxWidth = max(widthA, widthB).toInt()

        val heightA = sqrt((sorted[1].x - sorted[2].x).toDouble().pow(2.0) + (sorted[1].y - sorted[2].y).toDouble().pow(2.0))
        val heightB = sqrt((sorted[0].x - sorted[3].x).toDouble().pow(2.0) + (sorted[0].y - sorted[3].y).toDouble().pow(2.0))
        val maxHeight = max(heightA, heightB).toInt()

        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        
        val srcPoints = MatOfPoint2f(
            Point(sorted[0].x.toDouble(), sorted[0].y.toDouble()),
            Point(sorted[1].x.toDouble(), sorted[1].y.toDouble()),
            Point(sorted[2].x.toDouble(), sorted[2].y.toDouble()),
            Point(sorted[3].x.toDouble(), sorted[3].y.toDouble()),
        )
        
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth.toDouble(), 0.0),
            Point(maxWidth.toDouble(), maxHeight.toDouble()),
            Point(0.0, maxHeight.toDouble())
        )
        
        // In OpenCV 5.0, getPerspectiveTransform moved to Geometry
        val transform = Geometry.getPerspectiveTransform(srcPoints, dstPoints)
        val dstMat = Mat(maxHeight, maxWidth, srcMat.type())
        Imgproc.warpPerspective(srcMat, dstMat, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))
        
        val resultBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dstMat, resultBitmap)
        
        // Cleanup
        srcMat.release()
        dstMat.release()
        transform.release()
        srcPoints.release()
        dstPoints.release()
        
        return resultBitmap
    }

    private fun sortCorners(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points
        
        // TL, TR, BR, BL
        val sorted = points.sortedBy { it.x + it.y }
        val tl = sorted[0]
        val br = sorted[3]
        
        val remaining = points.filter { it != tl && it != br }
        val tr = remaining.minByOrNull { it.y - it.x } ?: remaining[0]
        val bl = remaining.maxByOrNull { it.y - it.x } ?: remaining[1]
        
        return listOf(tl, tr, br, bl)
    }

    /**
     * Unified method to crop, enhance, and ensure portrait orientation for a document.
     * @param bitmap The source bitmap (high-res).
     * @param corners Corners in bitmap coordinates (0 to width/height).
     */
    fun cropAndEnhance(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        var processed = deskewBitmap(bitmap, corners)
        processed = applyEnhancements(processed)
        val portrait = ensurePortraitOrientation(processed)
        
        return Bitmap.createScaledBitmap(portrait, TARGET_WIDTH, TARGET_HEIGHT, true)
    }

    /**
     * Maps points from an analysis frame to a high-res bitmap, accounting for aspect-ratio differences
     * due to sensor cropping (both are assumed to be centered on the same sensor).
     */
    fun mapAnalysisToBitmap(
        points: List<PointF>,
        analysisW: Float, analysisH: Float,
        bitmapW: Float, bitmapH: Float
    ): List<PointF> {
        val analysisRatio = analysisW / analysisH
        val bitmapRatio = bitmapW / bitmapH

        val scale: Float
        val offsetX: Float
        val offsetY: Float
        
        if (bitmapRatio > analysisRatio) {
            // Bitmap is wider than analysis frame (relative to height)
            scale = bitmapH / analysisH
            val effectiveWidth = analysisW * scale
            offsetX = (effectiveWidth - bitmapW) / 2f
            offsetY = 0f
        } else {
            // Bitmap is taller than analysis frame (relative to width)
            scale = bitmapW / analysisW
            val effectiveHeight = analysisH * scale
            offsetX = 0f
            offsetY = (effectiveHeight - bitmapH) / 2f
        }

        return points.map { p ->
            PointF(p.x * scale - offsetX, p.y * scale - offsetY)
        }
    }

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
