package com.example.documentsapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.data.DocumentManager
import com.example.documentsapp.databinding.FragmentPdfViewBinding
import com.example.documentsapp.ui.DocumentViewModel
import com.example.documentsapp.ui.PdfPageAdapter
import com.example.documentsapp.utils.PdfSigner
import com.example.documentsapp.utils.SvgUtils
import com.example.documentsapp.utils.applySystemWindowInsetsPadding
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.util.UUID

class PdfViewFragment : Fragment() {

    private var _binding: FragmentPdfViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DocumentViewModel by activityViewModels()
    private var pdfAdapter: PdfPageAdapter? = null
    private var pfd: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null
    private var isSignatureApplied = false
    private var isDigitalSignature = false
    private var appliedSignatureContent: String? = null
    private var appliedSignaturePassword: CharArray? = null
    private var signaturePageIndices = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("documentUri")
        if (uriString != null) {
            currentUri = Uri.parse(uriString)
            currentUri?.let { 
                loadPdf(it)
                binding.textFileName.text = DocumentManager.getFileName(requireContext(), it) ?: DocumentManager.generateUntitledName(requireContext())
            }
        }

        binding.textFileName.setOnClickListener {
            startInlineRename()
        }

        binding.editFileName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishInlineRename()
                true
            } else {
                false
            }
        }

        binding.editFileName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                finishInlineRename()
            }
        }

        binding.buttonAddSignature.setOnClickListener {
            val bundle = Bundle().apply { putString("origin", "edit") }
            findNavController().navigate(R.id.nav_signature, bundle)
        }

        binding.buttonShare.setOnClickListener {
            shareDocument()
        }

        parentFragmentManager.setFragmentResultListener("signature_selected", viewLifecycleOwner) { _, bundle ->
            val signatureName = bundle.getString("signature_name")
            if (signatureName != null) {
                loadAndShowSignature(signatureName)
            }
        }

        binding.buttonSaveSigned.setOnClickListener {
            flattenAndSave()
        }

        binding.bottomActions.applySystemWindowInsetsPadding(bottom = true)

        binding.recyclerPdfPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSignatureApplied && !isDigitalSignature) {
                    updateOverlayPosition()
                }
            }
        })
    }

    private fun startInlineRename() {
        val currentName = binding.textFileName.text.toString()
        binding.textFileName.visibility = View.GONE
        binding.editFileName.apply {
            visibility = View.VISIBLE
            setText(currentName.substringBeforeLast("."))
            requestFocus()
            setSelection(text?.length ?: 0)
            
            // Show keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun finishInlineRename() {
        val uri = currentUri ?: return
        val newName = binding.editFileName.text.toString().trim()
        
        if (newName.isNotEmpty()) {
            val newUri = DocumentManager.renameDocument(requireContext(), uri, newName)
            if (newUri != null) {
                currentUri = newUri
                binding.textFileName.text = DocumentManager.getFileName(requireContext(), newUri) ?: "$newName.pdf"
            }
        }
        
        binding.editFileName.visibility = View.GONE
        binding.textFileName.visibility = View.VISIBLE
        
        // Hide keyboard
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editFileName.windowToken, 0)
    }

    private fun shareDocument() {
        val uri = currentUri ?: return
        val shareUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(uri.path!!)
            )
        } else {
            uri
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Document"))
    }

    private fun updateOverlayPosition() {
        val layoutManager = binding.recyclerPdfPages.layoutManager as LinearLayoutManager
        for (pageIndex in signaturePageIndices) {
            val itemView = layoutManager.findViewByPosition(pageIndex)
            if (itemView != null) {
                binding.signatureOverlay.translationY = itemView.top.toFloat()
                binding.signatureOverlay.visibility = View.VISIBLE
                return
            }
        }
        binding.signatureOverlay.visibility = View.GONE
    }

    private fun flattenAndSave() {
        val uri = currentUri ?: return
        
        // Use a temporary file first
        val tempFile = File(requireContext().cacheDir, "temp_${System.currentTimeMillis()}.pdf")
        
        try {
            if (isDigitalSignature && appliedSignatureContent != null && appliedSignaturePassword != null) {
                // Real digital signing
                val p12Dir = File(requireContext().filesDir, "digital_signatures")
                val p12File = File(p12Dir, appliedSignatureContent!!)
                
                try {
                    PdfSigner.signPdf(requireContext(), uri, tempFile, p12File, appliedSignaturePassword!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(requireContext(), "Digital signing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } else if (isSignatureApplied && !isDigitalSignature && appliedSignatureContent != null) {
                flattenSvgToPdf(uri, tempFile)
            } else {
                // Save/Overwrite without changes
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            var success = false
            try {
                // For SAF URIs, "wt" (write-truncate) is the most reliable for overwriting
                requireContext().contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback for file scheme or if openFileDescriptor fails
                if (uri.scheme == "file") {
                    val originalFile = File(uri.path!!)
                    tempFile.copyTo(originalFile, overwrite = true)
                    success = true
                }
            }
            
            if (success) {
                tempFile.delete()
                DocumentManager.saveDocument(requireContext(), uri)
                Toast.makeText(requireContext(), "Document saved", Toast.LENGTH_SHORT).show()
                viewModel.clear()
                findNavController().popBackStack(R.id.nav_home, false)
            } else {
                // If overwrite failed, save as a new file in internal storage
                val finalFile = File(requireContext().filesDir, "signed_${UUID.randomUUID().toString().take(6)}.pdf")
                tempFile.copyTo(finalFile, overwrite = true)
                val finalUri = Uri.fromFile(finalFile)
                DocumentManager.saveDocument(requireContext(), finalUri)
                Toast.makeText(requireContext(), "Saved to internal storage", Toast.LENGTH_SHORT).show()
                viewModel.clear()
                findNavController().popBackStack(R.id.nav_home, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun flattenSvgToPdf(uri: Uri, outFile: File) {
        val pfd = requireContext().contentResolver.openFileDescriptor(uri, "r") ?: return
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        val pdfDocument = android.graphics.pdf.PdfDocument()
        
        val overlay = binding.signatureOverlay
        val overlayMatrix = overlay.getSignatureMatrix()
        
        // Use the current width of the recycler to calculate scale
        val viewWidth = binding.recyclerPdfPages.width.toFloat()
        
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas
            
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            
            if (signaturePageIndices.contains(i)) {
                appliedSignatureContent?.let { svgContent ->
                    canvas.save()
                    
                    // The view height depends on aspect ratio of the page
                    val viewHeight = viewWidth * (page.height.toFloat() / page.width.toFloat())
                    val scaleToPdf = page.width.toFloat() / viewWidth
                    
                    canvas.scale(scaleToPdf, scaleToPdf)
                    canvas.concat(overlayMatrix)
                    
                    SvgUtils.renderSvgToCanvas(
                        canvas, 
                        svgContent, 
                        overlay.signatureBitmap?.width?.toFloat() ?: 200f, 
                        overlay.signatureBitmap?.height?.toFloat() ?: 100f
                    )
                    canvas.restore()
                }
            }
            
            pdfDocument.finishPage(pdfPage)
            page.close()
            bitmap.recycle()
        }
        
        pdfDocument.writeTo(FileOutputStream(outFile))
        pdfDocument.close()
        renderer.close()
        pfd.close()
    }

    private fun loadPdf(uri: Uri) {
        try {
            pfd = requireContext().contentResolver.openFileDescriptor(uri, "r")
            pfd?.let {
                pdfAdapter = PdfPageAdapter(it)
                binding.recyclerPdfPages.apply {
                    adapter = pdfAdapter
                    layoutManager = LinearLayoutManager(requireContext())
                }
                
                // Check if it's a digital signature using real detection
                if (PdfSigner.isPdfDigitallySigned(requireContext(), uri)) {
                    binding.layoutSignatureStatus.visibility = View.VISIBLE
                    binding.textSignatureStatus.text = "Digitally Signed Document"
                } else {
                    binding.layoutSignatureStatus.visibility = View.GONE
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadAndShowSignature(name: String) {
        val svgDir = File(requireContext().filesDir, "signatures")
        val p12Dir = File(requireContext().filesDir, "digital_signatures")
        
        if (name.endsWith(".svg")) {
            val file = File(svgDir, name)
            if (file.exists()) {
                try {
                    val svgContent = file.readText()
                    appliedSignatureContent = svgContent
                    val density = resources.displayMetrics.density
                    val bitmap = SvgUtils.renderSvgToBitmap(svgContent, 200, 100, density)
                    
                    binding.signatureOverlay.visibility = View.VISIBLE
                    isSignatureApplied = true
                    isDigitalSignature = false
                    
                    val layoutManager = binding.recyclerPdfPages.layoutManager as LinearLayoutManager
                    val currentPos = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                    signaturePageIndices.clear() // Only one signature for now
                    signaturePageIndices.add(currentPos)
                    
                    // Delay until next frame to ensure layout is updated if needed, 
                    // or just use recycler width
                    binding.recyclerPdfPages.post {
                        val itemView = layoutManager.findViewByPosition(currentPos)
                        val viewWidth = binding.recyclerPdfPages.width
                        val viewHeight = if (itemView != null) itemView.height else binding.recyclerPdfPages.height
                        
                        binding.signatureOverlay.setSignature(bitmap, viewWidth, viewHeight)
                        if (itemView != null) {
                            binding.signatureOverlay.translationY = itemView.top.toFloat()
                        }
                        binding.signatureOverlay.setPosition(viewWidth / 2f, viewHeight / 2f)
                    }
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }
        } else if (name.endsWith(".p12")) {
            val file = File(p12Dir, name)
            if (file.exists()) {
                showSignaturePasswordDialog(file)
            }
        }
    }

    private fun showSignaturePasswordDialog(p12File: File) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_password_input, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.edit_password)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_password_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_done) { _, _ ->
                val password = passwordInput.text.toString().toCharArray()
                if (verifyPassword(p12File, password)) {
                    isSignatureApplied = true
                    isDigitalSignature = true
                    appliedSignatureContent = p12File.name
                    appliedSignaturePassword = password
                    binding.layoutSignatureStatus.visibility = View.VISIBLE
                    binding.textSignatureStatus.text = "Digitally Signed with ${p12File.name} (pending save)"
                    Toast.makeText(requireContext(), "Digital signature applied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.error_invalid_password, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun verifyPassword(p12File: File, password: CharArray): Boolean {
        return try {
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(p12File).use { fis ->
                ks.load(fis, password)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pdfAdapter?.close()
        try {
            pfd?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        _binding = null
    }
}
