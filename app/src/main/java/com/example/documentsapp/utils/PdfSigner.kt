package com.example.documentsapp.utils

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar

object PdfSigner {

    fun isPdfDigitallySigned(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val document = PDDocument.load(input)
                val signatures = document.signatureDictionaries
                var isSigned = signatures.isNotEmpty()
                
                if (!isSigned) {
                    val acroForm = document.documentCatalog.acroForm
                    if (acroForm != null) {
                        for (field in acroForm.fields) {
                            if (field is com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField) {
                                if (field.signature != null) {
                                    isSigned = true
                                    break
                                }
                            }
                        }
                    }
                }
                
                document.close()
                isSigned
            } ?: false
        } catch (ignored: Exception) {
            false
        }
    }

    fun signPdf(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        p12File: File,
        password: CharArray,
    ) {
        val bcProvider = BouncyCastleProvider()
        val ks = KeyStore.getInstance("PKCS12", bcProvider)
        FileInputStream(p12File).use { fis ->
            ks.load(fis, password)
        }

        val aliases = ks.aliases()
        if (!aliases.hasMoreElements()) {
            throw IllegalStateException("No aliases found in KeyStore")
        }
        val alias = aliases.nextElement()
        val privateKey = ks.getKey(alias, password) as PrivateKey
        val chain = ks.getCertificateChain(alias)
        if (chain == null || chain.isEmpty()) {
            throw IllegalStateException("No certificate chain found for alias $alias")
        }
        val certificate = chain[0] as X509Certificate

        // Copy input URI to a temporary file to ensure we have a seekable source for incremental save
        val tempInput = File(context.cacheDir, "sign_input_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            tempInput.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        try {
            // Load from the seekable file
            val document = PDDocument.load(tempInput)
            
            val signature = PDSignature()
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
            signature.name = certificate.subjectX500Principal.name
            signature.signDate = Calendar.getInstance()

            val signatureInterface = SignatureInterface { content: InputStream ->
                val certList = listOf(certificate)
                val certs = JcaCertStore(certList)
                val gen = CMSSignedDataGenerator()
                val sha256Signer = JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(bcProvider)
                    .build(privateKey)
                gen.addSignerInfoGenerator(
                    JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(bcProvider).build())
                        .build(sha256Signer, certificate),
                )
                gen.addCertificates(certs)
                val cmsProcessable = CMSProcessableByteArray(content.readBytes())
                val signedData = gen.generate(cmsProcessable, false)
                signedData.encoded
            }

            document.addSignature(signature, signatureInterface)
            
            // Incremental save is required for digital signatures to keep original content valid
            FileOutputStream(outputFile).use<FileOutputStream, Unit> { output ->
                document.saveIncremental(output)
            }
            document.close()
        } finally {
            if (tempInput.exists()) tempInput.delete()
        }
    }
}
