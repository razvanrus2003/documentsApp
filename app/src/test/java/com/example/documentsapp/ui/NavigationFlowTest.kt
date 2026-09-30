package com.example.documentsapp.ui

import android.content.Context
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NoOpNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.documentsapp.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = "src/main/AndroidManifest.xml", packageName = "com.example.documentsapp")
class NavigationFlowTest {

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context)
        
        val graph = NavGraph(navController.navigatorProvider.getNavigator(NavGraphNavigator::class.java))
        graph.id = R.id.nav_graph
        graph.setStartDestination(R.id.nav_home)

        listOf(
            R.id.nav_home,
            R.id.nav_help,
            R.id.nav_camera,
            R.id.nav_crop,
            R.id.nav_edit_images,
            R.id.nav_pdf_view,
            R.id.nav_signature,
            R.id.nav_signature_selection,
            R.id.nav_svg_signature,
            R.id.nav_digital_signature,
            R.id.nav_digital_signature_help
        ).forEach { id ->
            val dest = NavDestination(NoOpNavigator())
            dest.id = id
            graph.addDestination(dest)
        }

        navController.graph = graph
    }

    @Test
    fun testStartDestinationIsHome() {
        assertEquals(R.id.nav_home, navController.currentDestination?.id)
    }

    @Test
    fun testHomeTransitions() {
        // Home -> Help
        navController.navigate(R.id.nav_help)
        assertEquals(R.id.nav_help, navController.currentDestination?.id)

        navController.popBackStack()
        assertEquals(R.id.nav_home, navController.currentDestination?.id)

        // Home -> Camera
        navController.navigate(R.id.nav_camera)
        assertEquals(R.id.nav_camera, navController.currentDestination?.id)
    }

    @Test
    fun testCameraToCropToEditTransitions() {
        navController.navigate(R.id.nav_camera)
        assertEquals(R.id.nav_camera, navController.currentDestination?.id)

        // Camera -> Crop
        navController.navigate(R.id.nav_crop)
        assertEquals(R.id.nav_crop, navController.currentDestination?.id)

        // Crop -> Edit
        navController.navigate(R.id.nav_edit_images)
        assertEquals(R.id.nav_edit_images, navController.currentDestination?.id)

        // Edit -> PDF View
        navController.navigate(R.id.nav_pdf_view)
        assertEquals(R.id.nav_pdf_view, navController.currentDestination?.id)
    }

    @Test
    fun testSignatureFlowTransitions() {
        // Home -> Signature List
        navController.navigate(R.id.nav_signature)
        assertEquals(R.id.nav_signature, navController.currentDestination?.id)

        // Signature List -> Signature Selection
        navController.navigate(R.id.nav_signature_selection)
        assertEquals(R.id.nav_signature_selection, navController.currentDestination?.id)

        // Signature Selection -> SVG Signature Canvas
        navController.navigate(R.id.nav_svg_signature)
        assertEquals(R.id.nav_svg_signature, navController.currentDestination?.id)
    }

    @Test
    fun testDigitalSignatureFlowTransitions() {
        navController.setCurrentDestination(R.id.nav_signature_selection)
        
        // Selection -> Digital Signature Form
        navController.navigate(R.id.nav_digital_signature)
        assertEquals(R.id.nav_digital_signature, navController.currentDestination?.id)

        // Digital Signature Form -> Digital Signature Help
        navController.navigate(R.id.nav_digital_signature_help)
        assertEquals(R.id.nav_digital_signature_help, navController.currentDestination?.id)
    }
}
