package com.example.documentsapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.R
import com.google.android.material.button.MaterialButton

class ImagePageAdapter(
    private val pages: List<PageItem>,
    private val onRotate: (Int) -> Unit,
    private val onFilter: (Int) -> Unit,
    private val onRetake: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ImagePageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages[position]
        holder.imageView.setImageURI(page.processedUri)
        
        holder.btnRotate.setOnClickListener { onRotate(position) }
        holder.btnFilter.setOnClickListener { onFilter(position) }
        holder.btnRetake.setOnClickListener { onRetake(position) }
        holder.btnDeleteCorner.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount(): Int = pages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_page)
        val btnRotate: MaterialButton = view.findViewById(R.id.button_page_rotate)
        val btnFilter: MaterialButton = view.findViewById(R.id.button_page_filter)
        val btnRetake: MaterialButton = view.findViewById(R.id.button_page_retake)
        val btnDeleteCorner: MaterialButton = view.findViewById(R.id.button_page_delete_corner)
    }
}
