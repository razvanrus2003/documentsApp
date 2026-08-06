package com.example.documentsapp

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentSvgSignatureBinding

class SvgSignatureFragment : Fragment() {

    private var _binding: FragmentSvgSignatureBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSvgSignatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonSaveSvg.setOnClickListener {
            // Returns to the screen before the Signature selection page
            findNavController().popBackStack(R.id.nav_signature, true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
