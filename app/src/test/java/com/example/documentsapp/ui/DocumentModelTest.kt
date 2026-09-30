package com.example.documentsapp.ui

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentModelTest {

    @Test
    fun testDocumentModelProperties() {
        val doc = DocumentModel(
            id = "doc_123",
            name = "Test Document.pdf",
            uriString = "content://com.example/docs/123",
            timestamp = 1600000000000L
        )

        assertEquals("doc_123", doc.id)
        assertEquals("Test Document.pdf", doc.name)
        assertEquals("content://com.example/docs/123", doc.uriString)
        assertEquals(1600000000000L, doc.timestamp)
    }

    @Test
    fun testPageItemDefaults() {
        val uri = Mockito.mock(Uri::class.java)
        val pageItem = PageItem(
            originalUri = uri,
            processedUri = uri
        )

        assertEquals(uri, pageItem.originalUri)
        assertEquals(uri, pageItem.processedUri)
        assertEquals(0, pageItem.rotationDegrees)
        assertFalse(pageItem.isGrayScale)
    }
}
