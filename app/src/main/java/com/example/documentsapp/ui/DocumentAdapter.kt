package com.example.documentsapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.databinding.ItemDocumentBinding

class DocumentAdapter(
    private val onClick: (DocumentModel) -> Unit,
    private val onDelete: (DocumentModel) -> Unit,
) : ListAdapter<DocumentModel, DocumentAdapter.ViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(document: DocumentModel) {
            binding.textDocName.text = document.name
            binding.textDocUri.text = document.uriString
            binding.root.setOnClickListener { onClick(document) }
            binding.buttonDeleteDoc.setOnClickListener { onDelete(document) }
        }
    }

    class DocumentDiffCallback : DiffUtil.ItemCallback<DocumentModel>() {
        override fun areItemsTheSame(oldItem: DocumentModel, newItem: DocumentModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DocumentModel, newItem: DocumentModel): Boolean {
            return oldItem == newItem
        }
    }
}
