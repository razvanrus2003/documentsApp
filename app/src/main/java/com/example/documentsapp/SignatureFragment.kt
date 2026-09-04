package com.example.documentsapp

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentSignatureBinding
import com.example.documentsapp.utils.applySystemWindowInsetsMargin
import java.io.File
import java.io.FileOutputStream

class SignatureFragment : Fragment() {

    private var _binding: FragmentSignatureBinding? = null
    private val binding get() = _binding!!

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val fileName = getFileName(it)
            if (fileName != null) {
                if (isValidExtension(fileName)) {
                    importFile(it, fileName)
                } else {
                    Toast.makeText(requireContext(), "Unsupported file type", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonDigitalSignature.setOnClickListener {
            findNavController().navigate(R.id.action_nav_signature_to_nav_digital)
        }

        binding.buttonSvgSignature.setOnClickListener {
            findNavController().navigate(R.id.action_nav_signature_to_nav_svg)
        }

        binding.buttonImportSignature.setOnClickListener {
            importLauncher.launch("*/*")
        }

        binding.buttonImportSignature.applySystemWindowInsetsMargin(bottom = true)
    }

    private fun getFileName(uri: android.net.Uri): String? {
        var name: String? = null
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    private fun isValidExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "svg" || ext == "p12" || ext == "pfx"
    }

    private fun importFile(uri: android.net.Uri, fileName: String) {
        try {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val targetDirName = if (ext == "svg") "signatures" else "digital_signatures"
            val targetDir = File(requireContext().filesDir, targetDirName)
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetFile = File(targetDir, fileName)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(requireContext(), "Imported: $fileName", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack(R.id.nav_signature, false)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
