package com.documate.app.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.documate.app.data.model.DocumentFile
import com.documate.app.data.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Scans the device for documents using MediaStore + direct filesystem walk. */
object DeviceScanner {

    private val SUPPORTED_MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "text/plain"
    )

    private val SUPPORTED_EXTENSIONS = setOf("pdf", "docx", "doc", "xlsx", "xls", "txt")

    /** Progress callback: (scannedPath, foundCount) */
    suspend fun scan(
        context: Context,
        onProgress: (path: String, found: Int) -> Unit
    ): List<DocumentFile> = withContext(Dispatchers.IO) {

        val results = mutableMapOf<String, DocumentFile>() // path → file, deduped

        // ── 1. MediaStore scan (fastest, most reliable on Android 10+) ──────
        try {
            val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val mimeSelection = SUPPORTED_MIME_TYPES.joinToString(" OR ") {
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            }

            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                mimeSelection,
                SUPPORTED_MIME_TYPES,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val dataCol     = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol     = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol     = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val path     = it.getString(dataCol) ?: continue
                    val size     = it.getLong(sizeCol)
                    val dateMs   = it.getLong(dateCol) * 1000L
                    val file     = File(path)

                    if (file.exists() && file.extension.lowercase() in SUPPORTED_EXTENSIONS) {
                        val docFile = DocumentFile(
                            file        = file,
                            sizeBytes   = size,
                            lastModified = dateMs,
                            directory   = file.parent ?: ""
                        )
                        results[path] = docFile
                        onProgress(file.parent ?: path, results.size)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ── 2. Filesystem fallback – common dirs ────────────────────────────
        val scanDirs = buildList {
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            add(context.filesDir)
            add(context.getExternalFilesDir(null))

            // WhatsApp documents
            Environment.getExternalStorageDirectory()?.let { root ->
                add(File(root, "WhatsApp/Media/WhatsApp Documents"))
                add(File(root, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"))
            }
        }.filterNotNull()

        scanDirs.forEach { dir ->
            if (dir.exists() && dir.canRead()) {
                dir.walkTopDown()
                    .maxDepth(6)
                    .onEnter { folder ->
                        onProgress(folder.absolutePath, results.size)
                        true
                    }
                    .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                    .forEach { file ->
                        if (!results.containsKey(file.absolutePath)) {
                            results[file.absolutePath] = DocumentFile(file)
                        }
                    }
            }
        }

        results.values.sortedByDescending { it.lastModified }
    }
}
