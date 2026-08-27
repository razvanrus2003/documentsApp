package com.example.documentsapp

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentEditBinding

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.documentsapp.ui.PdfPageAdapter
import java.io.IOException

import com.example.documentsapp.data.DocumentManager

class EditFragment : Fragment() {

    private var _binding: FragmentEditBinding? = null
    private val binding get() = _binding!!

    private var pdfAdapter: PdfPageAdapter? = null
    private var pfd: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriString = arguments?.getString("documentUri")
        if (uriString != null) {
            currentUri = Uri.parse(uriString)
            currentUri?.let { loadPdf(it) }
        }

        binding.buttonAddSignature.setOnClickListener {
            val bundle = Bundle().apply { putString("origin", "edit") }
            findNavController().navigate(R.id.nav_signature, bundle)
        }

        parentFragmentManager.setFragmentResultListener("signature_selected", viewLifecycleOwner) { _, bundle ->
            val signatureName = bundle.getString("signature_name")
            if (signatureName != null) {
                Toast.makeText(requireContext(), "Selected: $signatureName", Toast.LENGTH_SHORT).show()
                // Logic to place signature on PDF would go here
            }
        }

        binding.buttonCancel.setOnClickListener {
            findNavController().popBackStack(R.id.nav_home, false)
        }

        binding.buttonSave.setOnClickListener {
            currentUri?.let { uri ->
                DocumentManager.saveDocument(requireContext(), uri)
                Toast.makeText(requireContext(), "Document Saved to Recents", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack(R.id.nav_home, false)
            }
        }

        binding.buttonExport.setOnClickListener {
            findNavController().popBackStack(R.id.nav_home, false)
        }
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
            }
        } catch (e: IOException) {
            e.printStackTrace()
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
