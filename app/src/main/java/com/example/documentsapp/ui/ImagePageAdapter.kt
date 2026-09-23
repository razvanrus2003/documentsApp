package com.example.documentsapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.R
import com.google.android.material.button.MaterialButton

class ImagePageAdapter(
    private val onDelete: (Int) -> Unit,
    private val onScanPage: () -> Unit
) : ListAdapter<PageItem, RecyclerView.ViewHolder>(PageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_PAGE = 0
        private const val VIEW_TYPE_SCAN = 1
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < super.getItemCount()) VIEW_TYPE_PAGE else VIEW_TYPE_SCAN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SCAN) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scan_page, parent, false)
            ScanViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_page, parent, false)
            PageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PageViewHolder) {
            val page = getItem(position)
            holder.imageView.setImageURI(page.processedUri)
            holder.btnDeleteCorner.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
        } else if (holder is ScanViewHolder) {
            holder.itemView.setOnClickListener { onScanPage() }
        }
    }

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_page)
        val btnDeleteCorner: MaterialButton = view.findViewById(R.id.button_page_delete_corner)
    }

    class ScanViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class PageDiffCallback : DiffUtil.ItemCallback<PageItem>() {
        override fun areItemsTheSame(oldItem: PageItem, newItem: PageItem): Boolean {
            return oldItem.originalUri == newItem.originalUri
        }

        override fun areContentsTheSame(oldItem: PageItem, newItem: PageItem): Boolean {
            return oldItem == newItem
        }
    }
}
