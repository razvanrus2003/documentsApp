package com.example.documentsapp.utils

import android.graphics.*

object ImageUtils {

    /**
     * Warps the given bitmap based on the 4 provided corners to produce a rectangular image.
     * corners should be in order: Top-Left, Top-Right, Bottom-Right, Bottom-Left
     */
    fun deskewBitmap(bitmap: Bitmap, corners: List<PointF>): Bitmap {
        // Calculate the width and height of the new image
        val widthA = Math.sqrt(Math.pow((corners[2].x - corners[3].x).toDouble(), 2.0) + Math.pow((corners[2].y - corners[3].y).toDouble(), 2.0))
        val widthB = Math.sqrt(Math.pow((corners[1].x - corners[0].x).toDouble(), 2.0) + Math.pow((corners[1].y - corners[0].y).toDouble(), 2.0))
        val maxWidth = Math.max(widthA, widthB).toInt()

        val heightA = Math.sqrt(Math.pow((corners[1].x - corners[2].x).toDouble(), 2.0) + Math.pow((corners[1].y - corners[2].y).toDouble(), 2.0))
        val heightB = Math.sqrt(Math.pow((corners[0].x - corners[3].x).toDouble(), 2.0) + Math.pow((corners[0].y - corners[3].y).toDouble(), 2.0))
        val maxHeight = Math.max(heightA, heightB).toInt()

        val resultBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val srcPoints = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            maxWidth.toFloat(), 0f,
            maxWidth.toFloat(), maxHeight.toFloat(),
            0f, maxHeight.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return resultBitmap
    }

    fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
