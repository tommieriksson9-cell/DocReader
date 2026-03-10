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

    // Request all storage permissions at once
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Even partial grants — try scanning
        viewModel.scanDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(
            this, MainViewModelFactory(applicationContext)
        )[MainViewModel::class.java]

        setupRecyclerView()
        setupSortBar()
        setupFilterTabs()
        setupSearchBar()
        setupFab()
        observeViewModel()
        requestStoragePermissions()

        intent?.data?.let { uri -> viewModel.importFile(uri) }
    }

    private fun requestStoragePermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            ).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                    needed.add(it)
            }
        } else {
            // Android 12 and below
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).forEach {
                if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                    needed.add(it)
            }
        }

        if (needed.isEmpty()) {
            viewModel.scanDevice()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(
            onOpen = { doc ->
                when (doc.type) {
                    DocumentType.DOCX -> openDocxEditor(doc.file.absolutePath)
                    DocumentType.XLSX -> openXlsxEditor(doc.file.absolutePath)
                    else              -> openViewer(doc.file.absolutePath)
                }
            },
            onDelete = { doc -> viewModel.deleteDocument(doc) }
        )
        binding.recyclerDocuments.layoutManager = LinearLayoutManager(this)
        binding.recyclerDocuments.adapter = adapter
    }

    private fun setupSortBar() {
        binding.chipSortDate.setOnClickListener { viewModel.setSortOption(SortOption.DATE) }
        binding.chipSortName.setOnClickListener { viewModel.setSortOption(SortOption.NAME) }
        binding.chipSortSize.setOnClickListener { viewModel.setSortOption(SortOption.SIZE) }
        binding.chipSortType.setOnClickListener { viewModel.setSortOption(SortOption.TYPE) }
    }

    private fun setupFilterTabs() {
        binding.chipFilterAll.setOnClickListener  { viewModel.setFilterType(null) }
        binding.chipFilterPdf.setOnClickListener  { viewModel.setFilterType(DocumentType.PDF) }
        binding.chipFilterDocx.setOnClickListener { viewModel.setFilterType(DocumentType.DOCX) }
        binding.chipFilterXlsx.setOnClickListener { viewModel.setFilterType(DocumentType.XLSX) }
        binding.chipFilterTxt.setOnClickListener  { viewModel.setFilterType(DocumentType.TXT) }
    }

    private fun setupSearchBar() {
        binding.editSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupFab() {
        binding.fabAddDocument.setOnClickListener { openFilePicker() }
    }

    private fun observeViewModel() {
        viewModel.scanState.observe(this) { state ->
            binding.scanOverlay.visibility = if (state.isScanning) View.VISIBLE else View.GONE
            if (state.isScanning) {
                binding.textScanPath.text  = state.currentPath
                binding.textScanFound.text = "Found: ${state.foundCount}"
                binding.progressScan.progress = state.progress
            }
        }

        viewModel.documents.observe(this) { docs ->
            adapter.submitList(docs)
            binding.textEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
            binding.textDocCount.text = "${docs.size} document${if (docs.size != 1) "s" else ""}"
        }

        viewModel.sortOption.observe(this)    { updateSortChips(it) }
        viewModel.sortAscending.observe(this) { updateSortChips(viewModel.sortOption.value) }

        viewModel.filterType.observe(this) { type ->
            val sel = getColor(R.color.primary)
            val def = getColor(R.color.text_secondary)
            binding.chipFilterAll.setTextColor(if (type == null) sel else def)
            binding.chipFilterPdf.setTextColor(if (type == DocumentType.PDF)  sel else def)
            binding.chipFilterDocx.setTextColor(if (type == DocumentType.DOCX) sel else def)
            binding.chipFilterXlsx.setTextColor(if (type == DocumentType.XLSX) sel else def)
            binding.chipFilterTxt.setTextColor(if (type == DocumentType.TXT)  sel else def)
        }

        viewModel.typeCounts.observe(this) { counts ->
            binding.chipFilterAll.text  = "ALL (${counts["ALL"]  ?: 0})"
            binding.chipFilterPdf.text  = "PDF (${counts["PDF"]  ?: 0})"
            binding.chipFilterDocx.text = "DOCX (${counts["DOCX"] ?: 0})"
            binding.chipFilterXlsx.text = "XLSX (${counts["XLSX"] ?: 0})"
            binding.chipFilterTxt.text  = "TXT (${counts["TXT"]  ?: 0})"
        }

        viewModel.importedFile.observe(this) { doc ->
            doc ?: return@observe
            Toast.makeText(this, "Opened: ${doc.name}", Toast.LENGTH_SHORT).show()
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
        val asc   = viewModel.sortAscending.value ?: false
        val arrow = if (asc) " ▲" else " ▼"
        val sel   = getColor(R.color.primary)
        val def   = getColor(R.color.text_secondary)
        binding.chipSortDate.text = "Date${if (active == SortOption.DATE) arrow else ""}"; binding.chipSortDate.setTextColor(if (active == SortOption.DATE) sel else def)
        binding.chipSortName.text = "A-Z${if (active == SortOption.NAME)  arrow else ""}"; binding.chipSortName.setTextColor(if (active == SortOption.NAME) sel else def)
        binding.chipSortSize.text = "Size${if (active == SortOption.SIZE) arrow else ""}"; binding.chipSortSize.setTextColor(if (active == SortOption.SIZE) sel else def)
        binding.chipSortType.text = "Type${if (active == SortOption.TYPE) arrow else ""}"; binding.chipSortType.setTextColor(if (active == SortOption.TYPE) sel else def)
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
            R.id.action_new_docx -> { viewModel.createNewDocx(); true }
            R.id.action_new_xlsx -> { viewModel.createNewXlsx(); true }
            R.id.action_rescan   -> { viewModel.scanDevice();    true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
