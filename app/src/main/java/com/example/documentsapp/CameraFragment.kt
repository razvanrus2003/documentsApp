package com.example.documentsapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentCameraBinding
import com.example.documentsapp.utils.DocumentDetector
import com.example.documentsapp.utils.ImageUtils
import com.example.documentsapp.utils.PdfGenerator
import boofcv.android.ConvertBitmap
import boofcv.struct.image.GrayU8
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val detector = DocumentDetector()
    private var grayImage: GrayU8? = null
    private var analysisWidth = 640
    private var analysisHeight = 480
    
    private var lastDetectedQuad: List<PointF>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.buttonCapture.setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        
        if (grayImage == null || grayImage!!.width != bitmap.width || grayImage!!.height != bitmap.height) {
            grayImage = GrayU8(bitmap.width, bitmap.height)
        }

        ConvertBitmap.bitmapToGray(bitmap, grayImage, null)
        if (analysisWidth != grayImage!!.width || analysisHeight != grayImage!!.height) {
            println("CameraFragment: Analysis resolution changed to ${grayImage!!.width}x${grayImage!!.height}")
        }
        analysisWidth = grayImage!!.width
        analysisHeight = grayImage!!.height
        val result = detector.detect(grayImage!!)
        val quad = result.points
        
        lastDetectedQuad = if (result.isDetected) quad else null

        activity?.runOnUiThread {
            if (_binding != null && grayImage != null) {
                val gray = grayImage!!
                binding.textStatus.text = if (result.isDetected) "Document Detected" else "Searching..."
                binding.textStatus.setTextColor(if (result.isDetected) android.graphics.Color.GREEN else android.graphics.Color.WHITE)
                
                binding.cameraOverlay.setDetectionState(result.isDetected)
                
                // Improved coordinate mapping considering rotation and aspect ratio
                val rotation = imageProxy.imageInfo.rotationDegrees
                val mappedPoints = quad.map { p ->
                    val x: Float
                    val y: Float
                    
                    // Map points from sensor coordinates to view coordinates based on rotation
                    when (rotation) {
                        90 -> {
                            x = (1 - p.y / gray.height) * binding.cameraOverlay.width
                            y = (p.x / gray.width) * binding.cameraOverlay.height
                        }
                        270 -> {
                            x = (p.y / gray.height) * binding.cameraOverlay.width
                            y = (1 - p.x / gray.width) * binding.cameraOverlay.height
                        }
                        180 -> {
                            x = (1 - p.x / gray.width) * binding.cameraOverlay.width
                            y = (1 - p.y / gray.height) * binding.cameraOverlay.height
                        }
                        else -> {
                            x = (p.x / gray.width) * binding.cameraOverlay.width
                            y = (p.y / gray.height) * binding.cameraOverlay.height
                        }
                    }
                    PointF(x, y)
                }
                binding.cameraOverlay.updateDetectedPoints(mappedPoints)
            }
        }
        imageProxy.close()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireContext().cacheDir,
            "capture_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processCapturedPhoto(photoFile)
                }
            }
        )
    }

    private fun processCapturedPhoto(file: File) {
        val exif = ExifInterface(file.absolutePath)
        val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotationDegrees = when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        var bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        if (rotationDegrees != 0) {
            bitmap = ImageUtils.rotateBitmap(bitmap, rotationDegrees)
        }

        val quad = lastDetectedQuad
        
        val finalFile = if (quad != null) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Cropping document...", Toast.LENGTH_SHORT).show()
            }
            // Deskew logic
            // Need to upscale quad points to full resolution
            // Analysis was likely 480x640 (portrait) or 640x480 (landscape)
            // But we mapped it to analysisWidth/analysisHeight
            val upscaledQuad = quad.map { p ->
                PointF(
                    p.x * bitmap.width / analysisWidth.toFloat(),
                    p.y * bitmap.height / analysisHeight.toFloat()
                )
            }
            val deskewed = ImageUtils.deskewBitmap(bitmap, upscaledQuad)
            val deskewedFile = File(requireContext().cacheDir, "deskewed_${System.currentTimeMillis()}.jpg")
            deskewedFile.outputStream().use { out ->
                deskewed.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            deskewedFile
        } else {
            // Even if no quad, save the rotated version
            val rotatedFile = File(requireContext().cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            rotatedFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            rotatedFile
        }

        val pdfUri = PdfGenerator.generatePdfFromImages(requireContext(), listOf(finalFile))
        if (pdfUri != null) {
            val bundle = Bundle().apply {
                putString("documentUri", pdfUri.toString())
            }
            findNavController().navigate(R.id.action_nav_camera_to_nav_edit, bundle)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
    }
}
