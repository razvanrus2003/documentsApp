package com.example.documentsapp

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.example.documentsapp.databinding.FragmentHelpBinding
import com.example.documentsapp.utils.applySystemWindowInsetsPadding

class HelpFragment : Fragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applySystemWindowInsetsPadding(bottom = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
