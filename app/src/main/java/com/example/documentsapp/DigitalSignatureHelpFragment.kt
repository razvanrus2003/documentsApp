package com.example.documentsapp

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.documentsapp.databinding.FragmentDigitalSignatureHelpBinding
import com.example.documentsapp.utils.applySystemWindowInsetsPadding

class DigitalSignatureHelpFragment : Fragment() {

    private var _binding: FragmentDigitalSignatureHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDigitalSignatureHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Render HTML content for bold tags
        val helpText = getString(R.string.digital_signature_help_text)
        binding.textHelpContent.text = Html.fromHtml(helpText, Html.FROM_HTML_MODE_COMPACT)

        view.applySystemWindowInsetsPadding(bottom = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
