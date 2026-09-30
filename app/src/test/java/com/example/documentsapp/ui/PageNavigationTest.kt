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
class PageNavigationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: DocumentViewModel

    @Before
    fun setUp() {
        viewModel = DocumentViewModel()
    }

    @Test
    fun testAddMultiplePagesNavigation() {
        val uri1 = Mockito.mock(Uri::class.java)
        val uri2 = Mockito.mock(Uri::class.java)
        val uri3 = Mockito.mock(Uri::class.java)

        viewModel.addPage(PageItem(originalUri = uri1, processedUri = uri1))
        viewModel.addPage(PageItem(originalUri = uri2, processedUri = uri2))
        viewModel.addPage(PageItem(originalUri = uri3, processedUri = uri3))

        val pages = viewModel.pages.value
        assertNotNull(pages)
        assertEquals(3, pages?.size)
        assertEquals(uri1, pages?.get(0)?.processedUri)
        assertEquals(uri2, pages?.get(1)?.processedUri)
        assertEquals(uri3, pages?.get(2)?.processedUri)
    }

    @Test
    fun testPageRotationNavigationState() {
        val uri = Mockito.mock(Uri::class.java)
        val page = PageItem(originalUri = uri, processedUri = uri, rotationDegrees = 0)

        viewModel.addPage(page)
        
        // Simulate rotating page 0 by 90 degrees
        val updatedPage = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
        viewModel.updatePage(0, updatedPage)

        assertEquals(90, viewModel.pages.value?.get(0)?.rotationDegrees)
        
        // Rotate another 90 degrees (total 180)
        val rotatedAgain = viewModel.pages.value!![0].copy(rotationDegrees = (viewModel.pages.value!![0].rotationDegrees + 90) % 360)
        viewModel.updatePage(0, rotatedAgain)
        assertEquals(180, viewModel.pages.value?.get(0)?.rotationDegrees)
    }

    @Test
    fun testPageDeletionNavigationState() {
        val uri1 = Mockito.mock(Uri::class.java)
        val uri2 = Mockito.mock(Uri::class.java)

        viewModel.addPage(PageItem(originalUri = uri1, processedUri = uri1))
        viewModel.addPage(PageItem(originalUri = uri2, processedUri = uri2))
        assertEquals(2, viewModel.pages.value?.size)

        // Delete first page
        viewModel.removePage(0)
        assertEquals(1, viewModel.pages.value?.size)
        assertEquals(uri2, viewModel.pages.value?.get(0)?.processedUri)
    }

    @Test
    fun testRetakePageNavigationIndex() {
        val uri1 = Mockito.mock(Uri::class.java)
        val uri2 = Mockito.mock(Uri::class.java)

        viewModel.addPage(PageItem(originalUri = uri1, processedUri = uri1))
        
        // Set retake index to 0 (retaking page 0)
        viewModel.retakePageIndex = 0
        assertEquals(0, viewModel.retakePageIndex)

        // Adding a new page when retakePageIndex is set should replace page 0
        viewModel.addPage(PageItem(originalUri = uri2, processedUri = uri2))
        
        assertEquals(1, viewModel.pages.value?.size)
        assertEquals(uri2, viewModel.pages.value?.get(0)?.processedUri)
        assertEquals(-1, viewModel.retakePageIndex)
    }
}
