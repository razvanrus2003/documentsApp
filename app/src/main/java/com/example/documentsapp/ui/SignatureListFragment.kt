package com.example.documentsapp.ui

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.R
import com.example.documentsapp.databinding.FragmentSignatureListBinding
import com.example.documentsapp.databinding.ItemSignatureBinding
import com.example.documentsapp.utils.SvgUtils
import com.example.documentsapp.utils.applySystemWindowInsetsMargin
import java.io.File

class SignatureListFragment : Fragment() {

    private var _binding: FragmentSignatureListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SignatureAdapter
    private var isEditMode = false
    private val selectedSignatures = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignatureListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val origin = arguments?.getString("origin") ?: "home"
        isEditMode = (origin == "edit")

        setupUI()
        setupRecyclerView()
        loadSignatures()

        binding.bottomActionBar.applySystemWindowInsetsMargin(bottom = true)
    }

    private fun setupUI() {
        if (isEditMode) {
            binding.layoutHomeActions.visibility = View.GONE
            binding.layoutEditActions.visibility = View.VISIBLE
            binding.buttonSelect.isEnabled = false
            
            binding.buttonSelect.setOnClickListener {
                val selected = selectedSignatures.firstOrNull()
                if (selected != null) {
                    parentFragmentManager.setFragmentResult("signature_selected", bundleOf("signature_name" to selected))
                    findNavController().popBackStack()
                }
            }
            
            binding.buttonCreateNew.setOnClickListener {
                findNavController().navigate(R.id.action_nav_signature_list_to_nav_signature_selection)
            }
        } else {
            binding.layoutHomeActions.visibility = View.VISIBLE
            binding.layoutEditActions.visibility = View.GONE
            
            binding.buttonAdd.setOnClickListener {
                findNavController().navigate(R.id.action_nav_signature_list_to_nav_signature_selection)
            }
            
            binding.buttonDelete.setOnClickListener {
                deleteSelected()
            }
            
            binding.buttonExport.setOnClickListener {
                exportSelected()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = SignatureAdapter(
            onSelectionChanged = { name, isSelected ->
                TransitionManager.beginDelayedTransition(binding.recyclerSignatures)
                if (isEditMode) {
                    selectedSignatures.clear()
                    if (isSelected) selectedSignatures.add(name)
                    binding.buttonSelect.isEnabled = selectedSignatures.isNotEmpty()
                } else {
                    if (isSelected) selectedSignatures.add(name) else selectedSignatures.remove(name)
                }
                adapter.notifyDataSetChanged()
            }
        )
        binding.recyclerSignatures.apply {
            adapter = this@SignatureListFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadSignatures() {
        val svgDir = File(requireContext().filesDir, "signatures")
        val p12Dir = File(requireContext().filesDir, "digital_signatures")
        
        val svgFiles = svgDir.listFiles { file -> file.extension == "svg" }?.toList() ?: emptyList()
        val p12Files = p12Dir.listFiles { file -> file.extension == "p12" }?.toList() ?: emptyList()
        
        val signatures = (svgFiles + p12Files).map { it.name }
        
        adapter.submitList(signatures)
        binding.textNoSignatures.visibility = if (signatures.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun deleteSelected() {
        if (selectedSignatures.isEmpty()) {
            Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            return
        }
        val svgDir = File(requireContext().filesDir, "signatures")
        val p12Dir = File(requireContext().filesDir, "digital_signatures")
        
        selectedSignatures.forEach { fileName ->
            if (fileName.endsWith(".svg")) {
                File(svgDir, fileName).delete()
            } else if (fileName.endsWith(".p12")) {
                File(p12Dir, fileName).delete()
            }
        }
        selectedSignatures.clear()
        loadSignatures()
        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
    }

    private fun exportSelected() {
        if (selectedSignatures.isEmpty()) {
            Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show()
            return
        }
        val svgDir = File(requireContext().filesDir, "signatures")
        val p12Dir = File(requireContext().filesDir, "digital_signatures")
        
        val uris = selectedSignatures.map { fileName ->
            val file = if (fileName.endsWith(".svg")) {
                File(svgDir, fileName)
            } else {
                File(p12Dir, fileName)
            }
            androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        }
        
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*" // Mix of SVG and P12
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Signatures"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SignatureAdapter(
        private val onSelectionChanged: (String, Boolean) -> Unit
    ) : ListAdapter<String, SignatureAdapter.ViewHolder>(StringDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSignatureBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: ItemSignatureBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(fileName: String) {
                val isSelected = selectedSignatures.contains(fileName)
                
                binding.textSignatureName.text = if (fileName.endsWith(".p12")) {
                    "[Digital] ${fileName.removeSuffix(".p12")}"
                } else {
                    fileName.removeSuffix(".svg")
                }
                
                // Tick boxes should only appear after you select something
                binding.checkboxSignature.visibility = if (selectedSignatures.isEmpty()) View.GONE else View.VISIBLE
                binding.checkboxSignature.isChecked = isSelected
                
                binding.root.setOnClickListener {
                    onSelectionChanged(fileName, !isSelected)
                }

                binding.checkboxSignature.setOnClickListener {
                    onSelectionChanged(fileName, binding.checkboxSignature.isChecked)
                }

                // Preview
                val context = binding.root.context
                if (fileName.endsWith(".svg")) {
                    val dir = File(context.filesDir, "signatures")
                    val file = File(dir, fileName)
                    if (file.exists()) {
                        try {
                            val svgContent = file.readText()
                            val density = context.resources.displayMetrics.density
                            val previewHeightDp = 64
                            val previewWidthDp = 64
                            
                            val bitmap = SvgUtils.renderSvgToBitmap(svgContent, previewWidthDp, previewHeightDp, density)
                            binding.imageSignaturePreview.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // Digital Signature Icon
                    binding.imageSignaturePreview.setImageResource(android.R.drawable.ic_lock_lock)
                }
            }
        }
    }

    class StringDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
