package com.documate.app.ui.viewer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import kotlin.math.max
import kotlin.math.min

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        private const val MIN_ZOOM = 1.0f
        private const val MAX_ZOOM = 5.0f
    }

    private lateinit var binding: ActivityDocumentViewerBinding
    private var pdfRenderer:    PdfRenderer?           = null
    private var fileDescriptor: ParcelFileDescriptor?  = null
    private var currentPage  = 0
    private var totalPages   = 0

    // Zoom / pan
    private val matrix      = Matrix()
    private val savedMatrix  = Matrix()
    private var scaleFactor  = 1.0f
    private var lastTouchX   = 0f
    private var lastTouchY   = 0f
    private var isDragging   = false
    private lateinit var scaleDetector: ScaleGestureDetector

    // Base matrix — fits the page to screen (reset target)
    private val baseMatrix = Matrix()

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

    // ── PDF loading ──────────────────────────────────────────────────────────

    private fun loadPdf(file: File) {
        binding.pdfPageView.visibility = View.VISIBLE
        binding.pdfNavBar.visibility   = View.VISIBLE
        binding.scrollTxt.visibility   = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        setupZoom()

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer    = PdfRenderer(fileDescriptor!!)
                    totalPages     = pdfRenderer!!.pageCount
                    true
                } catch (e: Exception) { false }
            }
            binding.progressBar.visibility = View.GONE
            if (ok) {
                // Wait for the view to be laid out so we know its exact dimensions
                binding.pdfPageView.post {
                    renderPage(0)
                    updateNav()
                }
            } else {
                Toast.makeText(this@DocumentViewerActivity,
                    "Could not open PDF", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage(currentPage)
                updateNav()
            }
        }
        binding.btnNextPage.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage(currentPage)
                updateNav()
            }
        }

        // Double-tap resets zoom to fit
        binding.pdfPageView.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick() { resetZoom() }
        })
    }

    private fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        val page     = renderer.openPage(index)

        // Render at 2× screen density for sharpness
        val viewW  = binding.pdfPageView.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val viewH  = binding.pdfPageView.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels

        // High-res render
        val scale  = (viewW.toFloat() / page.width) * 2f
        val bmpW   = (page.width  * scale).toInt()
        val bmpH   = (page.height * scale).toInt()

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        binding.pdfPageView.setImageBitmap(bitmap)
        binding.pdfPageView.scaleType = ImageView.ScaleType.MATRIX

        // Compute base matrix: scale bitmap to fit view width, centre vertically
        val scaleToFit = viewW.toFloat() / bmpW      // always fits width exactly
        val scaledH    = bmpH * scaleToFit
        val dy         = max(0f, (viewH - scaledH) / 2f)  // centre if shorter than view

        baseMatrix.reset()
        baseMatrix.postScale(scaleToFit, scaleToFit)
        baseMatrix.postTranslate(0f, dy)

        // Reset zoom state to base
        scaleFactor = 1.0f
        matrix.set(baseMatrix)
        savedMatrix.set(baseMatrix)
        binding.pdfPageView.imageMatrix = matrix

        binding.textPageNumber.text = "${index + 1} / $totalPages"
    }

    private fun updateNav() {
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage < totalPages - 1
    }

    // ── Pinch zoom + drag ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoom() {
        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = scaleFactor * detector.scaleFactor
                val clamped  = max(MIN_ZOOM, min(newScale, MAX_ZOOM))
                val ratio    = clamped / scaleFactor
                scaleFactor  = clamped

                matrix.postScale(ratio, ratio, detector.focusX, detector.focusY)
                binding.pdfPageView.imageMatrix = matrix
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                savedMatrix.set(matrix)
            }
        })

        binding.pdfPageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && !scaleDetector.isInProgress && scaleFactor > 1.05f) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        matrix.set(savedMatrix)
                        matrix.postTranslate(dx, dy)
                        binding.pdfPageView.imageMatrix = matrix
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    savedMatrix.set(matrix)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            true
        }
    }

    private fun resetZoom() {
        scaleFactor = 1.0f
        matrix.set(baseMatrix)
        savedMatrix.set(baseMatrix)
        binding.pdfPageView.imageMatrix = matrix
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    private fun loadTxt(file: File) {
        binding.pdfPageView.visibility = View.GONE
        binding.pdfNavBar.visibility   = View.GONE
        binding.scrollTxt.visibility   = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try { file.readText(Charsets.UTF_8) }
                catch (e: Exception) {
                    try { file.readText(Charsets.ISO_8859_1) }
                    catch (e2: Exception) { "Error: ${e2.message}" }
                }
            }
            binding.progressBar.visibility = View.GONE
            binding.textView.text = text.ifEmpty { "(Empty file)" }
        }
    }

    // ── Double-tap helper ────────────────────────────────────────────────────

    abstract class DoubleClickListener : View.OnClickListener {
        private var lastClick = 0L
        override fun onClick(v: View) {
            val now = System.currentTimeMillis()
            if (now - lastClick < 300) onDoubleClick()
            lastClick = now
        }
        abstract fun onDoubleClick()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
