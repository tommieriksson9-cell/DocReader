package com.documate.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.documate.app.data.model.DocumentFile
import com.documate.app.data.model.DocumentType
import com.documate.app.data.model.SortOption
import com.documate.app.data.repository.DocumentRepository
import com.documate.app.utils.DocxHandler
import com.documate.app.utils.XlsxHandler
import kotlinx.coroutines.launch
import java.io.File

data class ScanState(
    val isScanning: Boolean = false,
    val currentPath: String = "",
    val foundCount: Int = 0,
    val progress: Int = 0
)

class MainViewModel(private val context: Context) : ViewModel() {

    private val repository = DocumentRepository(context)
    private val docxHandler = DocxHandler()
    private val xlsxHandler = XlsxHandler()

    // Raw scanned list
    private val _allDocuments = MutableLiveData<List<DocumentFile>>(emptyList())

    // Scan state
    private val _scanState = MutableLiveData(ScanState())
    val scanState: LiveData<ScanState> = _scanState

    // Sort + filter state
    private val _sortOption   = MutableLiveData(SortOption.DATE)
    private val _sortAscending= MutableLiveData(false)
    private val _filterType   = MutableLiveData<DocumentType?>(null)  // null = ALL
    private val _searchQuery  = MutableLiveData("")

    val sortOption:    LiveData<SortOption>   = _sortOption
    val sortAscending: LiveData<Boolean>      = _sortAscending
    val filterType:    LiveData<DocumentType?>= _filterType
    val searchQuery:   LiveData<String>       = _searchQuery

    // Derived: filtered + sorted list
    val documents: LiveData<List<DocumentFile>> = MediatorLiveData<List<DocumentFile>>().also { med ->
        val recompute = { med.value = applyFilterSort() }
        med.addSource(_allDocuments)  { recompute() }
        med.addSource(_sortOption)    { recompute() }
        med.addSource(_sortAscending) { recompute() }
        med.addSource(_filterType)    { recompute() }
        med.addSource(_searchQuery)   { recompute() }
    }

    // Count per type (for filter tabs)
    val typeCounts: LiveData<Map<String, Int>> = MediatorLiveData<Map<String, Int>>().also { med ->
        med.addSource(_allDocuments) { list ->
            val counts = mutableMapOf("ALL" to list.size)
            list.forEach { counts[it.type.name] = (counts[it.type.name] ?: 0) + 1 }
            med.value = counts
        }
    }

    private val _importedFile = MutableLiveData<DocumentFile?>()
    val importedFile: LiveData<DocumentFile?> = _importedFile

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init { scanDevice() }

    // ── Scan ────────────────────────────────────────────────────────────────

    fun scanDevice() {
        viewModelScope.launch {
            _scanState.value = ScanState(isScanning = true)
            try {
                // Load internal files immediately as a fast first result
                val internal = repository.getInternalDocuments()
                _allDocuments.value = internal

                val allFound = mutableListOf<DocumentFile>()
                val found = repository.scanDevice { path, count ->
                    _scanState.postValue(
                        ScanState(
                            isScanning  = true,
                            currentPath = path,
                            foundCount  = count,
                            progress    = minOf(99, count * 4) // rough estimate
                        )
                    )
                }
                allFound.addAll(found)
                // Merge internal files not already in scan results
                val paths = found.map { it.file.absolutePath }.toSet()
                internal.filter { it.file.absolutePath !in paths }.forEach { allFound.add(it) }

                _allDocuments.value = allFound
            } catch (e: Exception) {
                _error.value = "Scan failed: ${e.message}"
            } finally {
                _scanState.value = ScanState(isScanning = false, progress = 100)
            }
        }
    }

    // ── Sort / Filter ────────────────────────────────────────────────────────

    fun setSortOption(option: SortOption) {
        if (_sortOption.value == option) {
            _sortAscending.value = !(_sortAscending.value ?: false)
        } else {
            _sortOption.value = option
            _sortAscending.value = option == SortOption.NAME
        }
    }

    fun setFilterType(type: DocumentType?) { _filterType.value = type }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    private fun applyFilterSort(): List<DocumentFile> {
        val query   = _searchQuery.value?.lowercase() ?: ""
        val filter  = _filterType.value
        val sort    = _sortOption.value ?: SortOption.DATE
        val asc     = _sortAscending.value ?: false

        var list = (_allDocuments.value ?: emptyList())
            .filter { doc ->
                (filter == null || doc.type == filter) &&
                (query.isEmpty() || doc.name.lowercase().contains(query))
            }

        val comparator: Comparator<DocumentFile> = when (sort) {
            SortOption.NAME -> compareBy { it.name.lowercase() }
            SortOption.DATE -> compareBy { it.lastModified }
            SortOption.SIZE -> compareBy { it.sizeBytes }
            SortOption.TYPE -> compareBy<DocumentFile> { it.type.name }.thenBy { it.name.lowercase() }
        }

        list = if (asc) list.sortedWith(comparator) else list.sortedWith(comparator.reversed())
        return list
    }

    // ── Import / Create ──────────────────────────────────────────────────────

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val doc = repository.importFromUri(uri)
                _importedFile.value = doc
                doc?.let {
                    val current = _allDocuments.value.orEmpty().toMutableList()
                    current.add(0, it)
                    _allDocuments.value = current
                }
            } catch (e: Exception) {
                _error.value = "Import failed: ${e.message}"
            }
        }
    }

    fun deleteDocument(doc: DocumentFile) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _allDocuments.value = _allDocuments.value.orEmpty().filter {
                it.file.absolutePath != doc.file.absolutePath
            }
        }
    }

    fun createNewDocx() {
        viewModelScope.launch {
            try {
                val file = File(context.filesDir, "New Document ${System.currentTimeMillis()}.docx")
                docxHandler.createNew(file)
                val doc = DocumentFile(file)
                val current = _allDocuments.value.orEmpty().toMutableList()
                current.add(0, doc)
                _allDocuments.value = current
                _importedFile.value = doc
            } catch (e: Exception) {
                _error.value = "Failed to create document: ${e.message}"
            }
        }
    }

    fun createNewXlsx() {
        viewModelScope.launch {
            try {
                val file = File(context.filesDir, "New Spreadsheet ${System.currentTimeMillis()}.xlsx")
                xlsxHandler.createNew(file)
                val doc = DocumentFile(file)
                val current = _allDocuments.value.orEmpty().toMutableList()
                current.add(0, doc)
                _allDocuments.value = current
                _importedFile.value = doc
            } catch (e: Exception) {
                _error.value = "Failed to create spreadsheet: ${e.message}"
            }
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(context) as T
    }
}
