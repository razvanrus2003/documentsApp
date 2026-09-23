package com.example.documentsapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.R

enum class FilterType {
    BW, BLUR_REMOVER, EQUALIZER, GREYSCALE, INVERT, SKETCH, BRIGHTNESS, CONTRAST
}

data class FilterItem(
    val type: FilterType,
    val name: String,
    val iconRes: Int // We could use icons or just names for now
)

class FilterAdapter(
    private val filters: List<FilterItem>,
    private val onFilterSelected: (FilterType) -> Unit
) : RecyclerView.Adapter<FilterAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val filter = filters[position]
        holder.name.text = filter.name
        holder.preview.setImageResource(filter.iconRes)
        holder.itemView.setOnClickListener {
            onFilterSelected(filter.type)
        }
    }

    override fun getItemCount(): Int = filters.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val preview: ImageView = view.findViewById(R.id.filter_preview)
        val name: TextView = view.findViewById(R.id.filter_name)
    }
}
