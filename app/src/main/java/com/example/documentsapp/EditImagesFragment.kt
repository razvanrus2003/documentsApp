package com.example.documentsapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.example.documentsapp.data.DocumentManager
import com.example.documentsapp.databinding.FragmentEditImagesBinding
import com.example.documentsapp.ui.DocumentViewModel
import com.example.documentsapp.ui.ImagePageAdapter
import com.example.documentsapp.utils.ImageUtils
import com.example.documentsapp.utils.PdfGenerator
import com.example.documentsapp.utils.applySystemWindowInsetsPadding
import java.io.File
import java.io.IOException

class EditImagesFragment : Fragment() {

    private var _binding: FragmentEditImagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels()
    private var imageAdapter: ImagePageAdapter? = null
    private var snapHelper: PagerSnapHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        binding.buttonAddPage.setOnClickListener {
            findNavController().navigate(R.id.nav_camera)
        }

        binding.buttonCancel.setOnClickListener {
            viewModel.clear()
            findNavController().popBackStack(R.id.nav_home, false)
        }

        binding.buttonDone.setOnClickListener {
            generatePdfAndContinue()
        }

        binding.bottomActions.applySystemWindowInsetsPadding(bottom = true)
    }

    private fun setupRecyclerView() {
        if (snapHelper == null) {
            snapHelper = PagerSnapHelper()
            snapHelper?.attachToRecyclerView(binding.recyclerPdfPages)
        }
        
        binding.recyclerPdfPages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            imageAdapter = ImagePageAdapter(
                pages = pages,
                onRotate = { index -> rotatePage(index) },
                onFilter = { index -> applyFilter(index) },
                onRetake = { index -> retakePage(index) },
                onDelete = { index -> deletePage(index) }
            )
            binding.recyclerPdfPages.adapter = imageAdapter
        }
    }

    private fun rotatePage(index: Int) {
        val page = viewModel.pages.value?.get(index) ?: return
        val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(page.processedUri)) ?: return
        val rotatedBitmap = ImageUtils.rotateBitmap(bitmap, 90)
        
        val file = File(requireContext().cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        page.rotationDegrees = (page.rotationDegrees + 90) % 360
        page.processedUri = Uri.fromFile(file)
        viewModel.updatePage(index, page)
    }

    private fun applyFilter(index: Int) {
        val page = viewModel.pages.value?.get(index) ?: return
        val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(page.processedUri)) ?: return
        
        val processedBitmap = ImageUtils.applyGrayScaleFilter(bitmap)
        
        val file = File(requireContext().cacheDir, "filter_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        page.isGrayScale = !page.isGrayScale
        page.processedUri = Uri.fromFile(file)
        viewModel.updatePage(index, page)
    }

    private fun retakePage(index: Int) {
        viewModel.retakePageIndex = index
        findNavController().navigate(R.id.nav_camera)
    }

    private fun deletePage(index: Int) {
        viewModel.removePage(index)
    }

    private fun generatePdfAndContinue() {
        val pages = viewModel.pages.value ?: return
        if (pages.isEmpty()) {
            Toast.makeText(requireContext(), "No pages to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        val imageFiles = pages.map { File(it.processedUri.path!!) }
        val pdfUri = PdfGenerator.generatePdfFromImages(requireContext(), imageFiles)
        
        if (pdfUri != null) {
            val bundle = Bundle().apply {
                putString("documentUri", pdfUri.toString())
            }
            findNavController().navigate(R.id.nav_pdf_view, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
