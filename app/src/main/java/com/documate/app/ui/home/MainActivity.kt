package com.documate.app.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.documate.app.R
import com.documate.app.data.model.DocumentType
import com.documate.app.data.model.SortOption
import com.documate.app.databinding.ActivityMainBinding
import com.documate.app.ui.editor.DocxEditorActivity
import com.documate.app.ui.editor.XlsxEditorActivity
import com.documate.app.ui.viewer.DocumentViewerActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DocumentAdapter

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> viewModel.importFile(uri) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.scanDevice()
        } else {
            Toast.makeText(this, "Limited access – only showing internal files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(applicationContext)
        )[MainViewModel::class.java]

        setupRecyclerView()
        setupSortBar()
        setupFilterTabs()
        setupSearchBar()
        setupFab()
        observeViewModel()
        requestPermissions()

        intent?.data?.let { uri -> viewModel.importFile(uri) }
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onOpen = { doc ->
                when (doc.type) {
                    DocumentType.PDF    -> openViewer(doc.file.absolutePath)
                    DocumentType.DOCX   -> openDocxEditor(doc.file.absolutePath)
                    DocumentType.XLSX   -> openXlsxEditor(doc.file.absolutePath)
                    DocumentType.TXT    -> openViewer(doc.file.absolutePath)
                    else                -> openViewer(doc.file.absolutePath)
                }
            },
            onDelete = { doc -> viewModel.deleteDocument(doc) }
        )
        binding.recyclerDocuments.layoutManager = LinearLayoutManager(this)
        binding.recyclerDocuments.adapter = adapter
    }

    // ── Sort bar ─────────────────────────────────────────────────────────────

    private fun setupSortBar() {
        binding.chipSortDate.setOnClickListener  { viewModel.setSortOption(SortOption.DATE) }
        binding.chipSortName.setOnClickListener  { viewModel.setSortOption(SortOption.NAME) }
        binding.chipSortSize.setOnClickListener  { viewModel.setSortOption(SortOption.SIZE) }
        binding.chipSortType.setOnClickListener  { viewModel.setSortOption(SortOption.TYPE) }
    }

    // ── Filter tabs ───────────────────────────────────────────────────────────

    private fun setupFilterTabs() {
        binding.chipFilterAll.setOnClickListener  { viewModel.setFilterType(null) }
        binding.chipFilterPdf.setOnClickListener  { viewModel.setFilterType(DocumentType.PDF) }
        binding.chipFilterDocx.setOnClickListener { viewModel.setFilterType(DocumentType.DOCX) }
        binding.chipFilterXlsx.setOnClickListener { viewModel.setFilterType(DocumentType.XLSX) }
        binding.chipFilterTxt.setOnClickListener  { viewModel.setFilterType(DocumentType.TXT) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearchBar() {
        binding.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // ── FAB ───────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabAddDocument.setOnClickListener { openFilePicker() }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // Scan progress
        viewModel.scanState.observe(this) { state ->
            if (state.isScanning) {
                binding.scanOverlay.visibility = View.VISIBLE
                binding.textScanPath.text = state.currentPath
                binding.textScanFound.text = "Found: ${state.foundCount}"
                binding.progressScan.progress = state.progress
            } else {
                binding.scanOverlay.visibility = View.GONE
            }
        }

        // Document list
        viewModel.documents.observe(this) { docs ->
            adapter.submitList(docs)
            binding.textEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
            binding.textDocCount.text = "${docs.size} document${if (docs.size != 1) "s" else ""}"
        }

        // Sort indicators
        viewModel.sortOption.observe(this) { updateSortChips(it) }
        viewModel.sortAscending.observe(this) { updateSortChips(viewModel.sortOption.value) }

        // Filter indicators
        viewModel.filterType.observe(this) { type ->
            val selectedColor = getColor(R.color.primary)
            val defaultColor  = getColor(R.color.text_secondary)
            binding.chipFilterAll.setTextColor( if (type == null)                     selectedColor else defaultColor)
            binding.chipFilterPdf.setTextColor( if (type == DocumentType.PDF)         selectedColor else defaultColor)
            binding.chipFilterDocx.setTextColor(if (type == DocumentType.DOCX)        selectedColor else defaultColor)
            binding.chipFilterXlsx.setTextColor(if (type == DocumentType.XLSX)        selectedColor else defaultColor)
            binding.chipFilterTxt.setTextColor( if (type == DocumentType.TXT)         selectedColor else defaultColor)
        }

        // Type counts
        viewModel.typeCounts.observe(this) { counts ->
            binding.chipFilterAll.text  = "ALL (${counts["ALL"]  ?: 0})"
            binding.chipFilterPdf.text  = "PDF (${counts["PDF"]  ?: 0})"
            binding.chipFilterDocx.text = "DOCX (${counts["DOCX"]?: 0})"
            binding.chipFilterXlsx.text = "XLSX (${counts["XLSX"]?: 0})"
            binding.chipFilterTxt.text  = "TXT (${counts["TXT"]  ?: 0})"
        }

        // Import
        viewModel.importedFile.observe(this) { doc ->
            doc ?: return@observe
            Toast.makeText(this, "Imported: ${doc.name}", Toast.LENGTH_SHORT).show()
            when (doc.type) {
                DocumentType.DOCX -> openDocxEditor(doc.file.absolutePath)
                DocumentType.XLSX -> openXlsxEditor(doc.file.absolutePath)
                else              -> openViewer(doc.file.absolutePath)
            }
        }

        viewModel.error.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }

    private fun updateSortChips(active: SortOption?) {
        val asc = viewModel.sortAscending.value ?: false
        val arrow = if (asc) " ▲" else " ▼"
        binding.chipSortDate.text = if (active == SortOption.DATE) "🕐 Date$arrow" else "🕐 Date"
        binding.chipSortName.text = if (active == SortOption.NAME) "🔤 A–Z$arrow"  else "🔤 A–Z"
        binding.chipSortSize.text = if (active == SortOption.SIZE) "📦 Size$arrow" else "📦 Size"
        binding.chipSortType.text = if (active == SortOption.TYPE) "📁 Type$arrow" else "📁 Type"

        val sel = getColor(R.color.primary)
        val def = getColor(R.color.text_secondary)
        binding.chipSortDate.setTextColor(if (active == SortOption.DATE) sel else def)
        binding.chipSortName.setTextColor(if (active == SortOption.NAME) sel else def)
        binding.chipSortSize.setTextColor(if (active == SortOption.SIZE) sel else def)
        binding.chipSortType.setTextColor(if (active == SortOption.TYPE) sel else def)
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) permissionLauncher.launch(permissions)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/msword", "application/vnd.ms-excel", "text/plain"
            ))
        }
        filePickerLauncher.launch(intent)
    }

    private fun openViewer(path: String) =
        startActivity(Intent(this, DocumentViewerActivity::class.java)
            .putExtra(DocumentViewerActivity.EXTRA_FILE_PATH, path))

    private fun openDocxEditor(path: String) =
        startActivity(Intent(this, DocxEditorActivity::class.java)
            .putExtra(DocxEditorActivity.EXTRA_FILE_PATH, path))

    private fun openXlsxEditor(path: String) =
        startActivity(Intent(this, XlsxEditorActivity::class.java)
            .putExtra(XlsxEditorActivity.EXTRA_FILE_PATH, path))

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_docx  -> { viewModel.createNewDocx(); true }
            R.id.action_new_xlsx  -> { viewModel.createNewXlsx(); true }
            R.id.action_rescan    -> { viewModel.scanDevice(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
