package com.example.documentsapp.ui

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdditionalViewModelTest {

    @Test
    fun testViewModelInitialState() {
        val viewModel = DocumentViewModel()
        assertNotNull(viewModel.pages.value)
        assertTrue(viewModel.pages.value!!.isEmpty())
        assertEquals(-1, viewModel.retakePageIndex)
        assertEquals(1.0f, viewModel.pdfZoomScale)
    }
}
