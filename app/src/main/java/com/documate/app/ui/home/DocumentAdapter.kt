package com.documate.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.documate.app.R
import com.documate.app.data.model.DocumentFile
import com.documate.app.data.model.DocumentType
import com.documate.app.databinding.ItemDocumentBinding
import java.text.SimpleDateFormat
import java.util.*

class DocumentAdapter(
    private val onOpen: (DocumentFile) -> Unit,
    private val onDelete: (DocumentFile) -> Unit
) : ListAdapter<DocumentFile, DocumentAdapter.ViewHolder>(DiffCallback()) {

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

        fun bind(doc: DocumentFile) {
            binding.textFileName.text = doc.name
            binding.textFileSize.text = doc.sizeFormatted
            binding.textFilePath.text = doc.directory
            binding.textFileDate.text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(doc.lastModified))

            binding.imageFileIcon.setImageResource(
                when (doc.type) {
                    DocumentType.PDF    -> R.drawable.ic_pdf
                    DocumentType.DOCX   -> R.drawable.ic_docx
                    DocumentType.XLSX   -> R.drawable.ic_xlsx
                    DocumentType.TXT    -> R.drawable.ic_txt
                    else                -> R.drawable.ic_file_generic
                }
            )

            binding.chipType.text = doc.type.name
            binding.chipType.setChipBackgroundColorResource(
                when (doc.type) {
                    DocumentType.PDF    -> R.color.pdf_color
                    DocumentType.DOCX   -> R.color.docx_color
                    DocumentType.XLSX   -> R.color.xlsx_color
                    else                -> R.color.txt_color
                }
            )

            binding.root.setOnClickListener { onOpen(doc) }
            binding.buttonDelete.setOnClickListener { onDelete(doc) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DocumentFile>() {
        override fun areItemsTheSame(a: DocumentFile, b: DocumentFile) =
            a.file.absolutePath == b.file.absolutePath
        override fun areContentsTheSame(a: DocumentFile, b: DocumentFile) =
            a.lastModified == b.lastModified && a.sizeBytes == b.sizeBytes
    }
}
