package com.example.documentsapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class DocumentsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        PDFBoxResourceLoader.init(applicationContext)
    }
}
