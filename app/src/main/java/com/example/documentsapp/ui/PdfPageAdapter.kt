package com.example.documentsapp.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.documentsapp.R

class PdfPageAdapter(private val pfd: ParcelFileDescriptor) :
    RecyclerView.Adapter<PdfPageAdapter.ViewHolder>() {

    private val renderer = PdfRenderer(pfd)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return ViewHolder(view as ImageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = renderer.openPage(position)
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        holder.imageView.setImageBitmap(bitmap)
        page.close()
    }

    override fun getItemCount(): Int = renderer.pageCount

    fun close() {
        renderer.close()
    }

    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}
