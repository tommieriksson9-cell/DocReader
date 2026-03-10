package com.documate.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.documate.app.databinding.ActivityDocumentViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityDocumentViewerBinding
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var totalPages = 0

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
            else  -> loadTxt(file)
        }
    }

    private fun loadPdf(file: File) {
        binding.pdfPageView.visibility = View.VISIBLE
        binding.pdfNavBar.visibility   = View.VISIBLE
        binding.textView.visibility    = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer   = PdfRenderer(fileDescriptor!!)
                    totalPages    = pdfRenderer!!.pageCount
                }
                binding.progressBar.visibility = View.GONE
                renderPage(0)
                updateNavButtons()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@DocumentViewerActivity, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) { currentPage--; renderPage(currentPage); updateNavButtons() }
        }
        binding.btnNextPage.setOnClickListener {
            if (currentPage < totalPages - 1) { currentPage++; renderPage(currentPage); updateNavButtons() }
        }
    }

    private fun renderPage(pageIndex: Int) {
        val renderer = pdfRenderer ?: return
        val page = renderer.openPage(pageIndex)

        val width  = resources.displayMetrics.widthPixels
        val height = (width.toFloat() / page.width * page.height).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        binding.pdfPageView.setImageBitmap(bitmap)
        binding.pdfPageView.scaleType = ImageView.ScaleType.FIT_CENTER
        binding.textPageNumber.text   = "Page ${pageIndex + 1} of $totalPages"
    }

    private fun updateNavButtons() {
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage < totalPages - 1
    }

    private fun loadTxt(file: File) {
        binding.pdfPageView.visibility = View.GONE
        binding.pdfNavBar.visibility   = View.GONE
        binding.textView.visibility    = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { file.readText() } catch (e: Exception) { "Error: ${e.message}" }
            }
            binding.progressBar.visibility = View.GONE
            binding.textView.text = text
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
