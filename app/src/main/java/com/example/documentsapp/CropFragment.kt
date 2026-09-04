package com.example.documentsapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentCropBinding
import com.example.documentsapp.ui.DocumentViewModel
import com.example.documentsapp.ui.PageItem
import com.example.documentsapp.utils.ImageUtils
import com.example.documentsapp.utils.applySystemWindowInsetsMargin
import java.io.File
import java.io.FileOutputStream

class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels()
    private var imageUri: Uri? = null
    private var currentBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("imageUri")
        if (uriString != null) {
            imageUri = Uri.parse(uriString)
            binding.imagePreview.setImageURI(imageUri)
            
            // Load bitmap and rotate based on EXIF for the magnifier
            val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                // Correct orientation
                val exif = ExifInterface(requireContext().contentResolver.openInputStream(imageUri!!)!!)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (rotation != 0) {
                    bitmap = ImageUtils.rotateBitmap(bitmap, rotation)
                }
                currentBitmap = bitmap
            }
            
            binding.imagePreview.post {
                val viewW = binding.imagePreview.width.toFloat()
                val viewH = binding.imagePreview.height.toFloat()
                
                binding.cropOverlay.setInitialPoints(viewW, viewH)
                
                if (bitmap != null) {
                    val bitmapW = bitmap.width.toFloat()
                    val bitmapH = bitmap.height.toFloat()
                    
                    val scale: Float
                    val offX: Float
                    val offY: Float
                    
                    if (bitmapW / bitmapH > viewW / viewH) {
                        scale = viewW / bitmapW
                        offX = 0f
                        offY = (viewH - bitmapH * scale) / 2f
                    } else {
                        scale = viewH / bitmapH
                        offX = (viewW - bitmapW * scale) / 2f
                        offY = 0f
                    }
                    binding.cropOverlay.setBitmap(bitmap, scale, offX, offY)
                }
            }
        }

        binding.buttonSelect.setOnClickListener {
            processCrop()
        }

        binding.buttonSelect.applySystemWindowInsetsMargin(bottom = true)
    }

    private fun processCrop() {
        val uri = imageUri ?: return
        val bitmap = currentBitmap ?: return
        
        val bitmapPoints = binding.cropOverlay.getPointsInBitmap()
        
        val processedBitmap = ImageUtils.cropAndEnhance(bitmap, bitmapPoints)
        
        val croppedFile = File(requireContext().cacheDir, "manual_crop_${System.currentTimeMillis()}.jpg")
        FileOutputStream(croppedFile).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        val page = PageItem(
            originalUri = uri,
            processedUri = Uri.fromFile(croppedFile)
        )
        viewModel.addPage(page)
        findNavController().navigate(R.id.action_nav_crop_to_nav_edit)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
