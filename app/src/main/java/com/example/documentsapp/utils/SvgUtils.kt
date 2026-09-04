package com.example.documentsapp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

object SvgUtils {

    fun renderSvgToBitmap(svgContent: String, targetWidth: Int, targetHeight: Int, density: Float = 1.0f): Bitmap {
        val scaledWidth = (targetWidth * density).toInt().coerceAtLeast(1)
        val scaledHeight = (targetHeight * density).toInt().coerceAtLeast(1)
        
        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        renderSvgToCanvas(canvas, svgContent, scaledWidth.toFloat(), scaledHeight.toFloat())
        
        return bitmap
    }

    fun renderSvgToCanvas(canvas: Canvas, svgContent: String, canvasWidth: Float, canvasHeight: Float) {
        val pathTagRegex = "<path([^>]+)/>".toRegex()
        val dRegex = "d=\"([^\"]+)\"".toRegex()
        val strokeRegex = "stroke=\"([^\"]+)\"".toRegex()
        val strokeWidthRegex = "stroke-width=\"([^\"]+)\"".toRegex()
        val opacityRegex = "(?:stroke-)?opacity=\"([^\"]+)\"".toRegex()
        val fillRegex = "fill=\"([^\"]+)\"".toRegex()

        val matches = pathTagRegex.findAll(svgContent)
        val pathList = mutableListOf<Pair<Path, Paint>>()
        
        val overallBounds = android.graphics.RectF()
        var boundsInitialized = false

        for (match in matches) {
            val attrContent = match.groupValues[1]
            
            val d = dRegex.find(attrContent)?.groupValues?.get(1) ?: continue
            val strokeColorStr = strokeRegex.find(attrContent)?.groupValues?.get(1)
            val fillColorStr = fillRegex.find(attrContent)?.groupValues?.get(1)
            val strokeWidthStr = strokeWidthRegex.find(attrContent)?.groupValues?.get(1) ?: "1.0"
            val opacityStr = opacityRegex.find(attrContent)?.groupValues?.get(1)
            
            val isFilled = fillColorStr != null && fillColorStr != "none"
            val colorStr = fillColorStr ?: strokeColorStr ?: "#000000"
            
            val paint = Paint().apply {
                color = try { Color.parseColor(colorStr) } catch (e: Exception) { Color.BLACK }
                strokeWidth = strokeWidthStr.toFloatOrNull() ?: 1.0f
                alpha = ((opacityStr?.toFloatOrNull() ?: 1.0f) * 255).toInt()
                style = if (isFilled) Paint.Style.FILL else Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            
            val path = Path()
            val tokens = d.replace(",", " ").trim().split("\\s+".toRegex())
            var i = 0
            while (i < tokens.size) {
                try {
                    when (tokens[i].uppercase()) {
                        "M" -> {
                            path.moveTo(tokens[i + 1].toFloat(), tokens[i + 2].toFloat())
                            i += 3
                        }
                        "L" -> {
                            path.lineTo(tokens[i + 1].toFloat(), tokens[i + 2].toFloat())
                            i += 3
                        }
                        "Q" -> {
                            path.quadTo(tokens[i + 1].toFloat(), tokens[i + 2].toFloat(), tokens[i + 3].toFloat(), tokens[i + 4].toFloat())
                            i += 5
                        }
                        "Z" -> {
                            path.close()
                            i += 1
                        }
                        else -> i++
                    }
                } catch (e: Exception) {
                    i++
                }
            }
            
            val pathBounds = android.graphics.RectF()
            path.computeBounds(pathBounds, true)
            
            if (paint.style == Paint.Style.STROKE) {
                pathBounds.inset(-paint.strokeWidth / 2f, -paint.strokeWidth / 2f)
            }
            
            if (!boundsInitialized) {
                overallBounds.set(pathBounds)
                boundsInitialized = true
            } else {
                overallBounds.union(pathBounds)
            }
            
            pathList.add(path to paint)
        }

        if (!boundsInitialized) return

        val contentWidth = overallBounds.width().coerceAtLeast(1f)
        val contentHeight = overallBounds.height().coerceAtLeast(1f)
        
        val scaleX = canvasWidth / contentWidth
        val scaleY = canvasHeight / contentHeight
        val scale = minOf(scaleX, scaleY) * 0.9f // 10% padding
        
        canvas.save()
        canvas.translate(canvasWidth / 2f, canvasHeight / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-overallBounds.centerX(), -overallBounds.centerY())

        for ((path, paint) in pathList) {
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }
}
