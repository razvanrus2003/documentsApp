package com.example.documentsapp.utils

import com.example.documentsapp.ui.SignatureDrawingView
import java.util.Locale
import kotlin.math.sqrt

object SvgExporter {

    fun generateSvg(width: Int, height: Int, strokes: List<SignatureDrawingView.Stroke>): String {
        if (strokes.isEmpty()) return ""

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        // Calculate bounding box
        for (stroke in strokes) {
            if (stroke.isEraser) continue
            for (p in stroke.points) {
                minX = minOf(minX, p.x)
                minY = minOf(minY, p.y)
                maxX = maxOf(maxX, p.x)
                maxY = maxOf(maxY, p.y)
            }
        }

        // If no points, fall back to canvas size
        if (minX == Float.MAX_VALUE) {
            minX = 0f; minY = 0f; maxX = width.toFloat(); maxY = height.toFloat()
        }

        // Buffer for stroke width
        val padding = 10f
        minX -= padding
        minY -= padding
        maxX += padding
        maxY += padding

        val contentWidth = maxX - minX
        val contentHeight = maxY - minY
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        sb.append(String.format(Locale.US, "<svg width=\"%.1f\" height=\"%.1f\" viewBox=\"%.1f %.1f %.1f %.1f\" xmlns=\"http://www.w3.org/2000/svg\">\n", 
            contentWidth, contentHeight, -contentWidth/2, -contentHeight/2, contentWidth, contentHeight))
        
        for (stroke in strokes) {
            if (stroke.isEraser || stroke.points.isEmpty()) continue
            
            val colorHex = String.format(Locale.US, "#%06X", 0xFFFFFF and stroke.color)
            val alpha = stroke.paint.alpha / 255f
            val opacityAttr = if (alpha < 1f) String.format(Locale.US, " opacity=\"%.2f\"", alpha) else ""
            
            if (stroke.tool == SignatureDrawingView.DrawingTool.PENCIL) {
                // Classic Pen: Export as a single FILLED polygon representing the ribbon
                if (stroke.points.size < 2) continue
                
                val leftEdge = mutableListOf<String>()
                val rightEdge = mutableListOf<String>()
                
                for (i in stroke.points.indices) {
                    val p = stroke.points[i]
                    val w = stroke.widths.getOrElse(i) { stroke.width }
                    
                    // Estimate normal vector
                    val next = stroke.points.getOrNull(i + 1) ?: p
                    val prev = stroke.points.getOrNull(i - 1) ?: p
                    val dx = next.x - prev.x
                    val dy = next.y - prev.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                    val nx = -dy / dist
                    val ny = dx / dist
                    
                    val lx = (p.x + nx * (w / 2f)) - centerX
                    val ly = (p.y + ny * (w / 2f)) - centerY
                    val rx = (p.x - nx * (w / 2f)) - centerX
                    val ry = (p.y - ny * (w / 2f)) - centerY
                    
                    leftEdge.add(String.format(Locale.US, "%.1f,%.1f", lx, ly))
                    rightEdge.add(String.format(Locale.US, "%.1f,%.1f", rx, ry))
                }
                
                sb.append("  <path d=\"M ")
                sb.append(leftEdge.joinToString(" L "))
                sb.append(" L ")
                sb.append(rightEdge.asReversed().joinToString(" L "))
                sb.append(" Z\" fill=\"$colorHex\"$opacityAttr />\n")
                
            } else {
                // Standard Pen: Use Midpoint Bezier algorithm for smooth export
                val points = stroke.points
                if (points.isEmpty()) continue
                
                sb.append("  <path d=\"")
                if (points.size >= 3) {
                    sb.append(String.format(Locale.US, "M %.1f %.1f ", points[0].x - centerX, points[0].y - centerY))
                    for (i in 1 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val midX = ((p1.x + p2.x) / 2) - centerX
                        val midY = ((p1.y + p2.y) / 2) - centerY
                        sb.append(String.format(Locale.US, "Q %.1f %.1f %.1f %.1f ", p1.x - centerX, p1.y - centerY, midX, midY))
                    }
                    val last = points.last()
                    sb.append(String.format(Locale.US, "L %.1f %.1f ", last.x - centerX, last.y - centerY))
                } else if (points.size == 2) {
                    sb.append(String.format(Locale.US, "M %.1f %.1f L %.1f %.1f ", points[0].x - centerX, points[0].y - centerY, points[1].x - centerX, points[1].y - centerY))
                } else {
                    sb.append(String.format(Locale.US, "M %.1f %.1f L %.1f %.1f ", points[0].x - centerX, points[0].y - centerY, points[0].x - centerX, points[0].y - centerY))
                }
                sb.append(String.format(Locale.US, "\" stroke=\"$colorHex\" stroke-width=\"%.1f\"$opacityAttr fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\" />\n", stroke.width))
            }
        }
        
        sb.append("</svg>")
        return sb.toString()
    }
}
