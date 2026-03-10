package com.documate.app.data.model

import java.io.File

enum class DocumentType {
    PDF, DOCX, XLSX, TXT, UNKNOWN;

    companion object {
        fun fromExtension(ext: String): DocumentType = when (ext.lowercase()) {
            "pdf"         -> PDF
            "docx", "doc" -> DOCX
            "xlsx", "xls" -> XLSX
            "txt"         -> TXT
            else          -> UNKNOWN
        }
    }
}

enum class SortOption {
    DATE, NAME, SIZE, TYPE
}

data class DocumentFile(
    val file: File,
    val name: String = file.name,
    val type: DocumentType = DocumentType.fromExtension(file.extension),
    val sizeBytes: Long = file.length(),
    val lastModified: Long = file.lastModified(),
    val directory: String = file.parent ?: ""
) {
    val sizeFormatted: String get() {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else      -> "$sizeBytes B"
        }
    }
}
