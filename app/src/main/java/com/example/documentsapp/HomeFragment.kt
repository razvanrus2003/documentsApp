package com.example.documentsapp

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentHomeBinding

import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle

import androidx.navigation.navOptions

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
            findNavController().navigate(R.id.action_nav_home_to_nav_edit)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
