package com.example.documentsapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.documentsapp.databinding.FragmentSvgSignatureBinding
import com.example.documentsapp.databinding.LayoutColorPickerBinding
import com.example.documentsapp.ui.SignatureDrawingView
import com.example.documentsapp.utils.SvgExporter
import com.google.android.material.slider.Slider
import java.io.FileOutputStream

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

        setupToolPalette()
        updateSelectionUI()

        binding.buttonClear.setOnClickListener {
            binding.drawingView.clear()
        }

        binding.buttonTips.setOnClickListener {
            showTipsDialog()
        }

        binding.buttonDoneSvg.setOnClickListener {
            if (binding.drawingView.getStrokes().filter { !it.isEraser }.isEmpty()) {
                Toast.makeText(requireContext(), "Please draw something before saving", Toast.LENGTH_SHORT).show()
            } else {
                showNamingDialog()
            }
        }
    }

    private fun setupToolPalette() {
        binding.buttonColorPicker.setOnClickListener {
            showColorPickerDialog()
        }

        binding.toggleGroupTools.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.button_tool_pen -> binding.drawingView.currentTool = SignatureDrawingView.DrawingTool.PEN
                    R.id.button_tool_pencil -> binding.drawingView.currentTool = SignatureDrawingView.DrawingTool.PENCIL
                    R.id.button_tool_eraser -> binding.drawingView.currentTool = SignatureDrawingView.DrawingTool.ERASER
                }
                updateSelectionUI()
            }
        }

        binding.sliderSize.addOnChangeListener { _, value, _ ->
            binding.drawingView.currentSize = value
        }
    }

    private fun showColorPickerDialog() {
        val dialogBinding = LayoutColorPickerBinding.inflate(layoutInflater)
        
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Select") { _, _ -> }
        builder.setNegativeButton("Cancel", null)
        
        val dialog = builder.create()

        var currentSelectedColor = binding.drawingView.currentColor
        
        fun updatePickerUI(color: Int, fromText: Boolean = false, fromSliders: Boolean = false) {
            currentSelectedColor = color
            dialogBinding.colorPreview.backgroundTintList = ColorStateList.valueOf(color)
            binding.drawingView.currentColor = color
            updateSelectionUI()
            
            // Sync custom views
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            if (!fromSliders) {
                dialogBinding.hueSlider.setHue(hsv[0])
                dialogBinding.satValMap.setHSV(hsv[0], hsv[1], hsv[2])
            }
            
            // Sync text fields
            if (!fromText) {
                dialogBinding.editR.setText(Color.red(color).toString())
                dialogBinding.editG.setText(Color.green(color).toString())
                dialogBinding.editB.setText(Color.blue(color).toString())
            }
        }

        // Custom View listeners
        dialogBinding.hueSlider.setOnHueChangedListener { h: Float ->
            val hsv = FloatArray(3)
            Color.colorToHSV(currentSelectedColor, hsv)
            hsv[0] = h
            val newColor = Color.HSVToColor(hsv)
            dialogBinding.satValMap.setHSV(h, hsv[1], hsv[2])
            updatePickerUI(newColor, fromSliders = true)
        }

        dialogBinding.satValMap.setOnSaturationValueChangedListener { s: Float, v: Float ->
            val hsv = FloatArray(3)
            Color.colorToHSV(currentSelectedColor, hsv)
            hsv[1] = s
            hsv[2] = v
            updatePickerUI(Color.HSVToColor(hsv), fromSliders = true)
        }

        // Text listeners
        val rgbWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val r = dialogBinding.editR.text.toString().toIntOrNull() ?: 0
                val g = dialogBinding.editG.text.toString().toIntOrNull() ?: 0
                val b = dialogBinding.editB.text.toString().toIntOrNull() ?: 0
                val newColor = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                if (newColor != currentSelectedColor) {
                    updatePickerUI(newColor, fromText = true)
                }
            }
        }
        dialogBinding.editR.addTextChangedListener(rgbWatcher)
        dialogBinding.editG.addTextChangedListener(rgbWatcher)
        dialogBinding.editB.addTextChangedListener(rgbWatcher)

        // Presets
        dialogBinding.presetBlack.setOnClickListener { updatePickerUI(Color.BLACK) }
        dialogBinding.presetRed.setOnClickListener { updatePickerUI(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)) }
        dialogBinding.presetLightBlue.setOnClickListener { updatePickerUI(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light)) }
        dialogBinding.presetGreen.setOnClickListener { updatePickerUI(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)) }
        dialogBinding.presetPurple.setOnClickListener { updatePickerUI(ContextCompat.getColor(requireContext(), android.R.color.holo_purple)) }
        dialogBinding.presetDarkBlue.setOnClickListener { updatePickerUI(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)) }

        // Initial set
        updatePickerUI(currentSelectedColor)
        
        dialog.show()
    }

    private fun updateSelectionUI() {
        binding.buttonToolPen.alpha = 1.0f
        binding.buttonToolPencil.alpha = 1.0f
        binding.buttonToolEraser.alpha = 1.0f
        
        binding.buttonColorPicker.backgroundTintList = ColorStateList.valueOf(binding.drawingView.currentColor)

        when (binding.drawingView.currentTool) {
            SignatureDrawingView.DrawingTool.PEN -> binding.buttonToolPen.alpha = 0.4f
            SignatureDrawingView.DrawingTool.PENCIL -> binding.buttonToolPencil.alpha = 0.4f
            SignatureDrawingView.DrawingTool.ERASER -> binding.buttonToolEraser.alpha = 0.4f
        }
    }

    private fun showTipsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Drawing Tips")
            .setMessage("Use the entire screen for drawing. Your signature can be resized and moved later when placing.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showNamingDialog() {
        val input = EditText(requireContext())
        input.hint = "e.g. My Signature"
        
        AlertDialog.Builder(requireContext())
            .setTitle("Name your signature")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveSignatureAsSvg(name)
                } else {
                    Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSignatureAsSvg(name: String) {
        val strokes = binding.drawingView.getStrokes()
        val width = binding.drawingView.width
        val height = binding.drawingView.height
        val svgContent = SvgExporter.generateSvg(width, height, strokes)

        try {
            val dir = java.io.File(requireContext().filesDir, "signatures")
            if (!dir.exists()) dir.mkdirs()
            
            val file = java.io.File(dir, "$name.svg")
            FileOutputStream(file).use { 
                it.write(svgContent.toByteArray())
            }
            
            Toast.makeText(requireContext(), "Signature saved: ${file.name}", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack(R.id.nav_signature, false)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to save signature", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
