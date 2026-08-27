package com.example.documentsapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.documentsapp.databinding.ActivityMainBinding

import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

import androidx.navigation.navOptions

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.InputStream
import java.io.OutputStream

import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: androidx.navigation.NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Inject test PDFs if they don't exist
        injectTestPdfs()

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_camera, R.id.nav_help
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun injectTestPdfs() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (sharedPrefs.getBoolean("test_pdfs_injected", false)) return

        copyAssetToDownloads("sample_1.pdf")
        copyAssetToDownloads("sample_2.pdf")

        sharedPrefs.edit { putBoolean("test_pdfs_injected", true) }
    }

    private fun copyAssetToDownloads(fileName: String) {
        try {
            val inputStream: InputStream = assets.open(fileName)
            val resolver = contentResolver
            
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                // For older versions, we might need WRITE_EXTERNAL_STORAGE permission
                // For simplicity in this test injection, I'll use the app's external dir if public one is restricted
                // or just skip if we want to be safe.
                // However, user asked to add to system.
                null 
            }

            uri?.let {
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.use { out ->
                    inputStream.copyTo(out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
