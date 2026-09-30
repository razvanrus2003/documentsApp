package com.example.documentsapp.utils

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class P12GeneratorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testGenerateP12Basic() {
        val outputDir = temporaryFolder.newFolder("p12_test_1")
        val params = P12Generator.P12Params(
            alias = "test_cert_1",
            password = "password123".toCharArray(),
            commonName = "Test Signer 1",
            organization = "",
            organizationalUnit = "",
            locality = "",
            state = "",
            country = ""
        )

        val p12File = P12Generator.generateP12(params, outputDir)
        assertNotNull(p12File)
        assertTrue(p12File.exists())
        assertTrue(p12File.length() > 0)
        assertEquals("test_cert_1.p12", p12File.name)
    }

    @Test
    fun testGenerateP12CustomParams() {
        val outputDir = temporaryFolder.newFolder("p12_test_2")
        val params = P12Generator.P12Params(
            alias = "test_cert_2",
            password = "securePassword".toCharArray(),
            commonName = "John Doe",
            organization = "Acme Corp",
            organizationalUnit = "Engineering",
            locality = "San Francisco",
            state = "California",
            country = "US"
        )

        val p12File = P12Generator.generateP12(params, outputDir)
        assertNotNull(p12File)
        assertTrue(p12File.exists())
        assertTrue(p12File.length() > 0)
        assertEquals("test_cert_2.p12", p12File.name)
    }
}
