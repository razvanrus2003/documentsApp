package com.example.documentsapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentCameraBinding
import com.example.documentsapp.ui.DocumentViewModel
import com.example.documentsapp.ui.PageItem
import com.example.documentsapp.utils.DocumentDetector
import com.example.documentsapp.utils.ImageUtils
import com.example.documentsapp.utils.applySystemWindowInsetsMargin
import com.example.documentsapp.utils.applySystemWindowInsetsPadding
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private lateinit var cameraExecutor: ExecutorService
    private val detector = DocumentDetector()
    
    // Persistent buffers to avoid GC pressure
    private var analysisMat: Mat? = null
    private var yuvMat: Mat? = null
    private var yuvByteArray: ByteArray? = null
    
    private var analysisWidth = 640
    private var analysisHeight = 480
    
    private var lastDetectedQuad: List<PointF>? = null
    private var frameCounter = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
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

        binding.buttonFlash.setOnClickListener {
            toggleFlash()
        }

        binding.switchDetectionMode.setOnCheckedChangeListener { _, isChecked ->
            binding.textStatus.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
            binding.cameraOverlay.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.buttonCapture.applySystemWindowInsetsMargin(bottom = true)
        binding.topControls.applySystemWindowInsetsPadding(top = true)
    }

    private fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        binding.buttonFlash.setIconResource(
            if (flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(
            {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                
                val analysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            val captureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(analysisResolutionSelector)
                .build().also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(captureResolutionSelector)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(analysisResolutionSelector)
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
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer
                )
                imageCapture?.flashMode = flashMode
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        frameCounter++
        // Process every 2nd frame to ensure performance
        if (frameCounter % 2 != 0) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Initialize or update buffers if resolution changed
        if (yuvMat == null || yuvMat!!.cols() != imageProxy.width || yuvMat!!.rows() != imageProxy.height + imageProxy.height / 2) {
            analysisWidth = imageProxy.width
            analysisHeight = imageProxy.height
            // YUV_420_888 layout: Y plane followed by interleaved UV
            yuvMat = Mat(analysisHeight + analysisHeight / 2, analysisWidth, CvType.CV_8UC1)
            analysisMat = Mat(analysisHeight, analysisWidth, CvType.CV_8UC3)
            yuvByteArray = ByteArray((analysisWidth * analysisHeight * 1.5).toInt())
            println("CameraFragment: Buffers initialized for $analysisWidth x $analysisHeight")
        }

        val startTime = System.currentTimeMillis()
        try {
            val yPlane = mediaImage.planes[0]
            val uPlane = mediaImage.planes[1]
            val vPlane = mediaImage.planes[2]
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            val yStride = yPlane.rowStride
            val uvStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride
            
            val bytes = yuvByteArray!!
            
            // 1. Copy Y plane (respecting stride)
            for (row in 0 until analysisHeight) {
                yBuffer.position(row * yStride)
                yBuffer.get(bytes, row * analysisWidth, analysisWidth)
            }
            
            // 2. Copy interleaved UV planes (creating NV21 layout: Y then interleaved V,U)
            // We only need to iterate half the rows and columns for UV
            val uvStart = analysisWidth * analysisHeight
            val uvRowData = ByteArray(uvStride)
            
            for (row in 0 until analysisHeight / 2) {
                vBuffer.position(row * uvStride)
                vBuffer.get(uvRowData, 0, min(uvStride, vBuffer.remaining()))
                
                for (col in 0 until analysisWidth / 2) {
                    // In NV21, V is at index 0, U is at index 1 in the interleaved pair
                    bytes[uvStart + (row * analysisWidth) + (col * 2)] = uvRowData[col * uvPixelStride]
                    
                    // We also need the U value
                    uBuffer.position(row * uvStride + col * uvPixelStride)
                    if (uBuffer.remaining() > 0) {
                        bytes[uvStart + (row * analysisWidth) + (col * 2) + 1] = uBuffer.get()
                    }
                }
            }
            
            yuvMat!!.put(0, 0, bytes)
            Imgproc.cvtColor(yuvMat!!, analysisMat!!, Imgproc.COLOR_YUV2RGB_NV21)
            
            val result = detector.detect(analysisMat!!)
            val quad = result.points
            
            val duration = System.currentTimeMillis() - startTime
            if (frameCounter % 10 == 0) {
                Log.d(TAG, "Analysis (Multi-Channel RGB) took ${duration}ms. Detected: ${result.isDetected}")
            }

            lastDetectedQuad = if (result.isDetected) quad else null

            activity?.runOnUiThread {
                val currentBinding = _binding ?: return@runOnUiThread
                currentBinding.textStatus.text = if (result.isDetected) "Document Detected" else "Searching..."
                currentBinding.textStatus.setTextColor(if (result.isDetected) android.graphics.Color.GREEN else android.graphics.Color.WHITE)
                
                currentBinding.cameraOverlay.setDetectionState(result.isDetected)
                
                val rotation = imageProxy.imageInfo.rotationDegrees
                val mappedPoints = quad.map { p ->
                    val x: Float
                    val y: Float
                    val p_y = ((p.y - analysisHeight / 2) * 1.5 + analysisHeight/2).toFloat();
                    when (rotation) {
                        90 -> {
                            x = (1 - p_y / analysisHeight) * currentBinding.cameraOverlay.width
                            y = (p.x / analysisWidth) * currentBinding.cameraOverlay.height
                        }
                        270 -> {
                            x = (p.y / analysisHeight) * currentBinding.cameraOverlay.width
                            y = (1 - p.x / analysisWidth) * currentBinding.cameraOverlay.height
                        }
                        180 -> {
                            x = (1 - p.x / analysisWidth) * currentBinding.cameraOverlay.width
                            y = (1 - p.y / analysisHeight) * currentBinding.cameraOverlay.height
                        }
                        else -> {
                            x = (p.x / analysisWidth) * currentBinding.cameraOverlay.width
                            y = (p.y / analysisHeight) * currentBinding.cameraOverlay.height
                        }
                    }
                    PointF(x, y)
                }
                currentBinding.cameraOverlay.updateDetectedPoints(mappedPoints)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during image analysis", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val isAutoMode = binding.switchDetectionMode.isChecked
        val capturedQuad = if (isAutoMode) lastDetectedQuad else null
        val capturedWidth = analysisWidth
        val capturedHeight = analysisHeight

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
                    if (isAutoMode) {
                        processCapturedPhoto(photoFile, capturedQuad, capturedWidth, capturedHeight)
                    } else {
                        // Manual mode: go to crop screen
                        val bundle = Bundle().apply {
                            putString("imageUri", Uri.fromFile(photoFile).toString())
                        }
                        findNavController().navigate(R.id.action_nav_camera_to_nav_crop, bundle)
                    }
                }
            }
        )
    }

    private fun processCapturedPhoto(file: File, quad: List<PointF>?, analysisW: Int, analysisH: Int) {
        val exif = ExifInterface(file.absolutePath)
        val rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotationDegrees = when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        Log.d(TAG, "Captured photo: ${bitmap.width}x${bitmap.height}, Rotation: $rotationDegrees")
        Log.d(TAG, "Analysis resolution: ${analysisW}x${analysisH}")
        
        val finalFile = if (quad != null) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Cropping high-res document...", Toast.LENGTH_SHORT).show()
            }
            
            // Translate quad points from analysis resolution to high-res bitmap resolution.
            val upscaledQuad = ImageUtils.mapAnalysisToBitmap(
                quad, 
                analysisW.toFloat(), analysisH.toFloat(), 
                bitmap.width.toFloat(), bitmap.height.toFloat()
            )
            
            Log.d(TAG, "Upscaled Quad: $upscaledQuad")
            
            val processedBitmap = ImageUtils.cropAndEnhance(bitmap, upscaledQuad)
            
            val deskewedFile = File(requireContext().cacheDir, "deskewed_${System.currentTimeMillis()}.jpg")
            deskewedFile.outputStream().use { out ->
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            deskewedFile
        } else {
            val rotatedBitmap = if (rotationDegrees != 0) {
                ImageUtils.rotateBitmap(bitmap, rotationDegrees)
            } else {
                bitmap
            }
            val portraitBitmap = ImageUtils.ensurePortraitOrientation(rotatedBitmap)
            val rotatedFile = File(requireContext().cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            rotatedFile.outputStream().use { out ->
                portraitBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            rotatedFile
        }

        val page = PageItem(
            originalUri = Uri.fromFile(file),
            processedUri = Uri.fromFile(finalFile)
        )
        viewModel.addPage(page)
        findNavController().navigate(R.id.action_nav_camera_to_nav_edit)
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
