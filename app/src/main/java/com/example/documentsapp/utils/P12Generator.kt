package com.example.documentsapp.utils

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*

object P12Generator {

    data class P12Params(
        val alias: String,
        val password: CharArray,
        val commonName: String,
        val organization: String,
        val organizationalUnit: String,
        val locality: String,
        val state: String,
        val country: String,
    )

    fun generateP12(params: P12Params, outputDir: File): File {
        val bcProvider = BouncyCastleProvider()
        
        // 1. Generate Key Pair
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", bcProvider)
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        // 2. Build Distinguished Name (DN)
        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        nameBuilder.addRDN(BCStyle.CN, params.commonName)
        
        if (params.organization.isNotBlank()) nameBuilder.addRDN(BCStyle.O, params.organization)
        if (params.organizationalUnit.isNotBlank()) nameBuilder.addRDN(BCStyle.OU, params.organizationalUnit)
        if (params.locality.isNotBlank()) nameBuilder.addRDN(BCStyle.L, params.locality)
        if (params.state.isNotBlank()) nameBuilder.addRDN(BCStyle.ST, params.state)
        if (params.country.isNotBlank()) nameBuilder.addRDN(BCStyle.C, params.country)
        
        val subjectName = nameBuilder.build()

        // 3. Generate Certificate
        val certificate = generateSelfSignedCertificate(keyPair, subjectName)

        // 4. Create KeyStore and Store P12
        val keyStore = KeyStore.getInstance("PKCS12", bcProvider)
        keyStore.load(null, null)
        keyStore.setKeyEntry(params.alias, keyPair.private, params.password, arrayOf(certificate))

        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "${params.alias}.p12")
        FileOutputStream(outputFile).use { fos ->
            keyStore.store(fos, params.password)
        }

        return outputFile
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair, subject: X500Name): X509Certificate {
        val random = SecureRandom()
        val serialNumber = BigInteger(64, random)
        val notBefore = Date()
        val notAfter = Date(notBefore.time + (365L * 24 * 60 * 60 * 1000 * 10)) // 10 years
        val bcProvider = BouncyCastleProvider()

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(bcProvider)
            .build(keyPair.private)
        
        return JcaX509CertificateConverter()
            .setProvider(bcProvider)
            .getCertificate(certBuilder.build(contentSigner))
    }
}
