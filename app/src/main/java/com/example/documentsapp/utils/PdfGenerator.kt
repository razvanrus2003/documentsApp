package com.example.documentsapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PdfGenerator {

    fun generatePdfFromImages(context: Context, imageFiles: List<File>): Uri? {
        val pdfDocument = PdfDocument()
        
        try {
            for ((index, file) in imageFiles.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                val canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                
                pdfDocument.finishPage(page)
            }
            
            val outputFile = File(context.cacheDir, "generated_document_${System.currentTimeMillis()}.pdf")
            pdfDocument.writeTo(FileOutputStream(outputFile))
            return Uri.fromFile(outputFile)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }
}
