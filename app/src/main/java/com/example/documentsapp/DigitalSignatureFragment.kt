package com.example.documentsapp

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentDigitalSignatureBinding
import com.example.documentsapp.utils.P12Generator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class DigitalSignatureFragment : Fragment() {

    private var _binding: FragmentDigitalSignatureBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Security.addProvider(BouncyCastleProvider())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDigitalSignatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill file name if empty
        if (binding.editFileName.text.isNullOrBlank()) {
            val randomId = java.util.UUID.randomUUID().toString().take(6)
            binding.editFileName.setText("sig_$randomId")
        }

        binding.buttonSaveDigital.setOnClickListener {
            if (validateFields()) {
                generateAndSave()
            }
        }

        binding.buttonHelp.setOnClickListener {
            findNavController().navigate(R.id.action_nav_digital_to_help)
        }

        binding.buttonToggleOptional.setOnClickListener {
            val isVisible = binding.layoutOptionalFields.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutOptionalFields.visibility = View.GONE
                binding.buttonToggleOptional.text = getString(R.string.action_more_optional_fields)
                binding.buttonToggleOptional.setIconResource(android.R.drawable.arrow_down_float)
            } else {
                binding.layoutOptionalFields.visibility = View.VISIBLE
                binding.buttonToggleOptional.text = getString(R.string.action_less_optional_fields)
                binding.buttonToggleOptional.setIconResource(android.R.drawable.arrow_up_float)
            }
        }
    }

    private fun validateFields(): Boolean {
        var isValid = true

        val fileName = binding.editFileName.text.toString()
        if (fileName.isBlank()) {
            binding.layoutFileName.error = getString(R.string.error_field_required)
            isValid = false
        } else {
            binding.layoutFileName.error = null
        }

        val password = binding.editPassword.text.toString()
        if (password.length < 6) {
            binding.layoutPassword.error = getString(R.string.error_password_too_short)
            isValid = false
        } else {
            binding.layoutPassword.error = null
        }

        val fields = listOf(
            binding.editCommonName to binding.layoutCommonName
        )

        for ((edit, layout) in fields) {
            if (edit.text.toString().isBlank()) {
                layout.error = getString(R.string.error_field_required)
                isValid = false
            } else {
                layout.error = null
            }
        }
        
        // Clear errors on optional fields just in case
        binding.layoutOrganization.error = null
        binding.layoutOrgUnit.error = null
        binding.layoutLocality.error = null
        binding.layoutState.error = null
        binding.layoutCountry.error = null

        return isValid
    }

    private fun generateAndSave() {
        try {
            val fileName = binding.editFileName.text.toString().replace(Regex("[^a-zA-Z0-9_]"), "_")
            val alias = fileName // Use file name as alias inside the keystore as well

            val params = P12Generator.P12Params(
                alias = alias,
                password = binding.editPassword.text.toString().toCharArray(),
                commonName = binding.editCommonName.text.toString(),
                organization = binding.editOrganization.text.toString(),
                organizationalUnit = binding.editOrgUnit.text.toString(),
                locality = binding.editLocality.text.toString(),
                state = binding.editState.text.toString(),
                country = binding.editCountry.text.toString()
            )

            val dir = File(requireContext().filesDir, "digital_signatures")
            val resultFile = P12Generator.generateP12(params, dir)

            Toast.makeText(requireContext(), "Signature generated: ${resultFile.name}", Toast.LENGTH_LONG).show()
            findNavController().popBackStack(R.id.nav_signature, false)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
