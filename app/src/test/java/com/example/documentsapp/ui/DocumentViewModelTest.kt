package com.example.documentsapp.ui

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: DocumentViewModel

    @Before
    fun setUp() {
        viewModel = DocumentViewModel()
    }

    @Test
    fun testAddAndRemovePage() {
        val uri = Mockito.mock(Uri::class.java)
        val page = PageItem(originalUri = uri, processedUri = uri)

        viewModel.addPage(page)
        assertEquals(1, viewModel.pages.value?.size)
        assertEquals(page, viewModel.pages.value?.get(0))

        viewModel.removePage(0)
        assertEquals(0, viewModel.pages.value?.size)
    }

    @Test
    fun testUpdatePage() {
        val uri1 = Mockito.mock(Uri::class.java)
        val uri2 = Mockito.mock(Uri::class.java)
        val page1 = PageItem(originalUri = uri1, processedUri = uri1)
        val page2 = PageItem(originalUri = uri1, processedUri = uri2, isGrayScale = true)

        viewModel.addPage(page1)
        viewModel.updatePage(0, page2)

        assertEquals(1, viewModel.pages.value?.size)
        assertTrue(viewModel.pages.value?.get(0)?.isGrayScale == true)
        assertEquals(uri2, viewModel.pages.value?.get(0)?.processedUri)
    }

    @Test
    fun testClear() {
        val uri = Mockito.mock(Uri::class.java)
        val page = PageItem(originalUri = uri, processedUri = uri)
        viewModel.addPage(page)

        viewModel.clear()
        assertEquals(0, viewModel.pages.value?.size)
        assertEquals(1.0f, viewModel.pdfZoomScale)
    }
}
