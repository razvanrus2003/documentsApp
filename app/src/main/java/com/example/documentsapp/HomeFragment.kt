package com.example.documentsapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.documentsapp.data.DocumentManager
import com.example.documentsapp.databinding.FragmentHomeBinding
import com.example.documentsapp.ui.DocumentAdapter
import com.example.documentsapp.ui.DocumentModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var documentAdapter: DocumentAdapter

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedPdf(it)
        }
    }

    private val topLevelNavOptions = navOptions {
        popUpTo(R.id.nav_home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadDocuments()

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_help -> {
                        findNavController().navigate(R.id.nav_help, null, topLevelNavOptions)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.buttonScan.setOnClickListener {
            findNavController().navigate(R.id.nav_camera, null, topLevelNavOptions)
        }

        binding.buttonImport.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    private fun setupRecyclerView() {
        documentAdapter = DocumentAdapter { document ->
            openDocument(document)
        }
        binding.recyclerRecentDocs.apply {
            adapter = documentAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadDocuments() {
        val documents = DocumentManager.getRecentDocuments(requireContext())
        documentAdapter.submitList(documents)
        binding.textNoDocs.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleSelectedPdf(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val bundle = Bundle().apply {
            putString("documentUri", uri.toString())
        }
        findNavController().navigate(R.id.action_nav_home_to_nav_edit, bundle)
    }

    private fun openDocument(document: DocumentModel) {
        val uri = Uri.parse(document.uriString)
        if (isUriAccessible(uri)) {
            val bundle = Bundle().apply {
                putString("documentUri", document.uriString)
            }
            findNavController().navigate(R.id.action_nav_home_to_nav_edit, bundle)
        } else {
            Toast.makeText(requireContext(), "File is no longer accessible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            val pfd = requireContext().contentResolver.openFileDescriptor(uri, "r")
            pfd?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        loadDocuments()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
