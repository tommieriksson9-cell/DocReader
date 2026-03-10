package com.documate.app.ui.viewer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.documate.app.databinding.ActivityDocumentViewerBinding
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityDocumentViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath)
        supportActionBar?.title = file.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        when (file.extension.lowercase()) {
            "pdf" -> loadPdf(file)
            "txt" -> loadTxt(file)
            else  -> loadTxt(file)
        }
    }

    private fun loadPdf(file: File) {
        binding.pdfView.visibility = View.VISIBLE
        binding.textView.visibility = View.GONE

        binding.pdfView
            .fromFile(file)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .onError { error ->
                Toast.makeText(this, "Error loading PDF: ${error.message}", Toast.LENGTH_LONG).show()
            }
            .scrollHandle(DefaultScrollHandle(this))
            .spacing(10)
            .load()
    }

    private fun loadTxt(file: File) {
        binding.pdfView.visibility = View.GONE
        binding.textView.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { file.readText() } catch (e: Exception) { "Error reading file: ${e.message}" }
            }
            binding.progressBar.visibility = View.GONE
            binding.textView.text = text
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
