package com.example.documentsapp.utils

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.example.documentsapp.ui.SignatureDrawingView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SvgExporterTest {

    @Test
    fun testGenerateSvgEmpty() {
        val svg = SvgExporter.generateSvg(400, 200, emptyList())
        assertEquals("", svg)
    }

    @Test
    fun testGenerateSvgWithPenStroke() {
        val path = Path()
        val paint = Paint()
        val points = listOf(PointF(10f, 10f), PointF(50f, 50f), PointF(100f, 100f))
        val widths = listOf(5f, 5f, 5f)
        
        val stroke = SignatureDrawingView.Stroke(
            path = path,
            paint = paint,
            color = 0xFF0000,
            width = 5f,
            isEraser = false,
            points = points,
            widths = widths,
            tool = SignatureDrawingView.DrawingTool.PEN
        )

        val svg = SvgExporter.generateSvg(400, 200, listOf(stroke))
        assertNotNull(svg)
        assertTrue(svg.contains("<?xml"))
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("<path"))
        assertTrue(svg.contains("</svg>"))
    }
}
