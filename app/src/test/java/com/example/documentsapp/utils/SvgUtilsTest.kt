package com.example.documentsapp.utils

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SvgUtilsTest {

    @Test
    fun testRenderSvgToBitmapDimensions() {
        val svgContent = "<svg width=\"100\" height=\"100\"><path d=\"M 0 0 L 100 100\" stroke=\"#000000\" stroke-width=\"2\"/></svg>"
        val bitmap = SvgUtils.renderSvgToBitmap(svgContent, 200, 100, 1.0f)
        
        assertNotNull(bitmap)
        assertEquals(200, bitmap.width)
        assertEquals(100, bitmap.height)
    }
}
