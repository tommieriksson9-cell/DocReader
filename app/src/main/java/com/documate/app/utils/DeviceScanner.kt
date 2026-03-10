package com.documate.app.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.documate.app.data.model.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DeviceScanner {

    private val SUPPORTED_EXTENSIONS = setOf("pdf", "docx", "doc", "xlsx", "xls", "txt")

    private val MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "text/plain"
    )

    suspend fun scan(
        context: Context,
        onProgress: (path: String, found: Int) -> Unit
    ): List<DocumentFile> = withContext(Dispatchers.IO) {

        val results = mutableMapOf<String, DocumentFile>()

        // ── 1. MediaStore — works on all Android versions ────────────────────
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            // Build MIME selection
            val selection = MIME_TYPES.joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            }

            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, selection, MIME_TYPES,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val path   = if (dataCol >= 0) it.getString(dataCol) ?: continue else continue
                    val size   = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val dateMs = if (dateCol >= 0) it.getLong(dateCol) * 1000L else 0L
                    val file   = File(path)

                    if (file.exists() && file.canRead()
                        && file.extension.lowercase() in SUPPORTED_EXTENSIONS) {
                        results[path] = DocumentFile(
                            file         = file,
                            sizeBytes    = size,
                            lastModified = dateMs,
                            directory    = file.parent ?: ""
                        )
                        onProgress(file.parent ?: path, results.size)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ── 2. Direct filesystem walk as fallback / supplement ───────────────
        val dirs = buildList {
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            add(context.getExternalFilesDir(null))
            add(context.filesDir)
            // Common locations
            val root = Environment.getExternalStorageDirectory()
            if (root != null) {
                add(File(root, "Documents"))
                add(File(root, "Download"))
                add(File(root, "WhatsApp/Media/WhatsApp Documents"))
            }
        }.filterNotNull().filter { it.exists() && it.canRead() }

        dirs.forEach { dir ->
            onProgress(dir.absolutePath, results.size)
            try {
                dir.walkTopDown()
                    .maxDepth(5)
                    .onEnter { folder ->
                        onProgress(folder.absolutePath, results.size)
                        true
                    }
                    .filter { it.isFile
                            && it.canRead()
                            && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                    .forEach { file ->
                        if (!results.containsKey(file.absolutePath)) {
                            results[file.absolutePath] = DocumentFile(file)
                            onProgress(file.parent ?: file.absolutePath, results.size)
                        }
                    }
            } catch (e: Exception) {
                // Skip directories we can't read
            }
        }

        results.values.sortedByDescending { it.lastModified }
    }
}
