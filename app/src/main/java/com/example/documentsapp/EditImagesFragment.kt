package com.example.documentsapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.databinding.FragmentEditImagesBinding
import com.example.documentsapp.ui.DocumentViewModel
import com.example.documentsapp.ui.FilterAdapter
import com.example.documentsapp.ui.FilterItem
import com.example.documentsapp.ui.FilterType
import com.example.documentsapp.ui.ImagePageAdapter
import com.example.documentsapp.utils.ImageUtils
import com.example.documentsapp.utils.PdfGenerator
import com.example.documentsapp.utils.applySystemWindowInsetsPadding
import java.io.File

class EditImagesFragment : Fragment() {

    private var _binding: FragmentEditImagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels()
    private var imageAdapter: ImagePageAdapter? = null
    private var snapHelper: PagerSnapHelper? = null

    private var preFilterUri: Uri? = null
    private var currentPreviewFilter: FilterType? = null
    private var filterSessionPageIndex: Int = -1
    private var bwThreshold: Double = -1.0

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

        binding.buttonCancel.setOnClickListener {
            viewModel.clear()
            findNavController().popBackStack(R.id.nav_home, false)
        }

        binding.buttonDone.setOnClickListener {
            generatePdfAndContinue()
        }

        binding.buttonPageRotate.setOnClickListener {
            val index = getCurrentPageIndex()
            if (index != RecyclerView.NO_POSITION) {
                rotatePage(index)
            }
        }

        binding.buttonPageFilter.setOnClickListener {
            if (binding.recyclerFilters.isVisible) {
                if (currentPreviewFilter != null) {
                    commitFilterKeepOpen()
                } else {
                    commitFilter()
                    binding.recyclerFilters.isVisible = false
                    binding.layoutBwThreshold.isVisible = false
                    updateFilterButtonText()
                }
            } else {
                val index = getCurrentPageIndex()
                if (index != RecyclerView.NO_POSITION) {
                    filterSessionPageIndex = index
                    val page = viewModel.pages.value?.get(index)
                    preFilterUri = page?.processedUri
                    currentPreviewFilter = null
                }
                setupFilterRecycler()
                binding.recyclerFilters.isVisible = true
                updateFilterButtonText()
            }
        }

        binding.buttonPageRetake.setOnClickListener {
            val index = getCurrentPageIndex()
            if (index != RecyclerView.NO_POSITION) {
                retakePage(index)
            }
        }

        binding.seekbarBwThreshold.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.textBwThresholdValue.text = progress.toString()
                if (fromUser && currentPreviewFilter == FilterType.BW) {
                    bwThreshold = progress.toDouble()
                    val index = getCurrentPageIndex()
                    if (index != RecyclerView.NO_POSITION) {
                        applyFilterAsPreview(index, FilterType.BW)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.bottomActionsContainer.applySystemWindowInsetsPadding(bottom = true)
    }

    private fun updateFilterButtonText() {
        if (!binding.recyclerFilters.isVisible) {
            binding.buttonPageFilter.text = getString(R.string.action_filter)
        } else if (currentPreviewFilter == null) {
            binding.buttonPageFilter.text = getString(R.string.action_close_filters)
        } else {
            binding.buttonPageFilter.text = getString(R.string.action_apply_filter)
        }
    }

    private fun getCurrentPageIndex(): Int {
        val layoutManager = binding.recyclerPdfPages.layoutManager as? LinearLayoutManager
        val view = snapHelper?.findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return layoutManager?.getPosition(view) ?: RecyclerView.NO_POSITION
    }

    private fun setupFilterRecycler() {
        val filters = listOf(
            FilterItem(FilterType.BW, "B&W", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.BLUR_REMOVER, "Blur Remover", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.EQUALIZER, "Equalizer", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.GREYSCALE, "Greyscale", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.INVERT, "Invert", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.SKETCH, "Sketch", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.BRIGHTNESS, "Brightness", android.R.drawable.ic_menu_gallery),
            FilterItem(FilterType.CONTRAST, "Contrast", android.R.drawable.ic_menu_gallery)
        )

        binding.recyclerFilters.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerFilters.adapter = FilterAdapter(filters) { filterType ->
            val index = getCurrentPageIndex()
            if (index != RecyclerView.NO_POSITION) {
                if (filterSessionPageIndex != index) {
                    filterSessionPageIndex = index
                    val page = viewModel.pages.value?.get(index)
                    preFilterUri = page?.processedUri
                    currentPreviewFilter = null
                }

                if (currentPreviewFilter == filterType) {
                    if (preFilterUri != null) {
                        val page = viewModel.pages.value?.get(index)
                        if (page != null) {
                            val updatedPage = page.copy(
                                isGrayScale = false,
                                processedUri = preFilterUri!!
                            )
                            viewModel.updatePage(index, updatedPage)
                        }
                    }
                    currentPreviewFilter = null
                    bwThreshold = -1.0
                    binding.layoutBwThreshold.isVisible = false
                } else {
                    bwThreshold = if (filterType == FilterType.BW) binding.seekbarBwThreshold.progress.toDouble() else -1.0
                    applyFilterAsPreview(index, filterType)
                    currentPreviewFilter = filterType
                    binding.layoutBwThreshold.isVisible = (filterType == FilterType.BW)
                }
                updateFilterButtonText()
            }
        }
    }

    private fun setupRecyclerView() {
        if (snapHelper == null) {
            snapHelper = PagerSnapHelper()
            snapHelper?.attachToRecyclerView(binding.recyclerPdfPages)
        }
        
        binding.recyclerPdfPages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        if (imageAdapter == null) {
            imageAdapter = ImagePageAdapter(
                onDelete = { index -> deletePage(index) },
                onScanPage = { findNavController().navigate(R.id.nav_camera) }
            )
            binding.recyclerPdfPages.adapter = imageAdapter
        }

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            imageAdapter?.submitList(pages.toList())
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
        
        val updatedPage = page.copy(
            rotationDegrees = (page.rotationDegrees + 90) % 360,
            processedUri = Uri.fromFile(file)
        )
        viewModel.updatePage(index, updatedPage)
    }

    private fun applyFilterAsPreview(index: Int, filterType: FilterType) {
        val page = viewModel.pages.value?.get(index) ?: return
        val baseUri = preFilterUri ?: page.processedUri
        val bitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(baseUri)) ?: return
        
        val processedBitmap = when (filterType) {
            FilterType.BW -> ImageUtils.applyBlackAndWhiteFilter(bitmap, bwThreshold)
            FilterType.BLUR_REMOVER -> ImageUtils.applyBlurRemover(bitmap)
            FilterType.EQUALIZER -> ImageUtils.applyEqualizer(bitmap)
            FilterType.GREYSCALE -> ImageUtils.applyGreyscaleFilter(bitmap)
            FilterType.INVERT -> ImageUtils.applyInvertFilter(bitmap)
            FilterType.SKETCH -> ImageUtils.applySketchFilter(bitmap)
            FilterType.BRIGHTNESS -> ImageUtils.applyBrightnessFilter(bitmap)
            FilterType.CONTRAST -> ImageUtils.applyContrastFilter(bitmap)
        }
        
        val file = File(requireContext().cacheDir, "filter_${filterType.name}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        val updatedPage = page.copy(
            isGrayScale = if (filterType == FilterType.BW || filterType == FilterType.GREYSCALE) true else page.isGrayScale,
            processedUri = Uri.fromFile(file)
        )
        viewModel.updatePage(index, updatedPage)
    }

    private fun commitFilter() {
        preFilterUri = null
        currentPreviewFilter = null
        filterSessionPageIndex = -1
        bwThreshold = -1.0
        binding.layoutBwThreshold.isVisible = false
        updateFilterButtonText()
    }

    private fun commitFilterKeepOpen() {
        val index = getCurrentPageIndex()
        if (index != RecyclerView.NO_POSITION) {
            val page = viewModel.pages.value?.get(index)
            preFilterUri = page?.processedUri
        }
        currentPreviewFilter = null
        bwThreshold = -1.0
        binding.layoutBwThreshold.isVisible = false
        updateFilterButtonText()
    }

    private fun retakePage(index: Int) {
        viewModel.retakePageIndex = index
        findNavController().navigate(R.id.nav_camera)
    }

    private fun deletePage(index: Int) {
        viewModel.removePage(index)
    }

    private fun generatePdfAndContinue() {
        commitFilter()
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
        snapHelper?.attachToRecyclerView(null)
        snapHelper = null
        _binding = null
    }
}
