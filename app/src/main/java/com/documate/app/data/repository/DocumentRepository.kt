package com.documate.app.data.repository

import android.content.Context
import android.net.Uri
import com.documate.app.data.model.DocumentFile
import com.documate.app.utils.DeviceScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(private val context: Context) {

    private val supportedExtensions = setOf("pdf", "docx", "doc", "xlsx", "xls", "txt")

    /** Full device scan via MediaStore + filesystem walk. */
    suspend fun scanDevice(
        onProgress: (path: String, found: Int) -> Unit
    ): List<DocumentFile> = DeviceScanner.scan(context, onProgress)

    /** Quick load of only app-internal files (no permissions needed). */
    fun getInternalDocuments(): List<DocumentFile> {
        return context.filesDir
            .listFiles()
            ?.filter { it.extension.lowercase() in supportedExtensions }
            ?.map { DocumentFile(it) }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    /** Copy a Uri (from file picker) into app's internal storage. */
    suspend fun importFromUri(uri: Uri): DocumentFile? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "document_${System.currentTimeMillis()}"
            val destFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            DocumentFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteDocument(doc: DocumentFile): Boolean = doc.file.delete()

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment
    }
}
