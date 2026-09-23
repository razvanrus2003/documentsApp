package com.example.documentsapp.ui

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class PageItem(
    val originalUri: Uri,
    var processedUri: Uri,
    var rotationDegrees: Int = 0,
    var isGrayScale: Boolean = false,
)

class DocumentViewModel : ViewModel() {
    private val _pages = MutableLiveData<MutableList<PageItem>>(mutableListOf())
    val pages: LiveData<MutableList<PageItem>> = _pages

    var retakePageIndex: Int = -1
    var pdfZoomScale: Float = 1.0f
    var pdfTranslateX: Float = 0f
    var pdfTranslateY: Float = 0f
    var savedSignatures = mutableListOf<PlacedSignature>()

    fun addPage(page: PageItem) {
        val current = _pages.value?.toMutableList() ?: mutableListOf()
        if (retakePageIndex != -1) {
            current[retakePageIndex] = page
            retakePageIndex = -1
        } else {
            current.add(page)
        }
        _pages.value = current
    }

    fun updatePage(index: Int, page: PageItem) {
        val current = _pages.value?.toMutableList() ?: return
        if (index in current.indices) {
            current[index] = page
            _pages.value = current
        }
    }

    fun removePage(index: Int) {
        val current = _pages.value?.toMutableList() ?: return
        if (index in current.indices) {
            current.removeAt(index)
            _pages.value = current
        }
    }
    
    fun clear() {
        _pages.value = mutableListOf()
        retakePageIndex = -1
        pdfZoomScale = 1.0f
        pdfTranslateX = 0f
        pdfTranslateY = 0f
        savedSignatures.clear()
    }
}
