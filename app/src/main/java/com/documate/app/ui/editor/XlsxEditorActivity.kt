package com.documate.app.ui.editor

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.documate.app.R
import com.documate.app.databinding.ActivityXlsxEditorBinding
import com.documate.app.utils.XlsxHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class XlsxEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityXlsxEditorBinding
    private val xlsxHandler = XlsxHandler()
    private var filePath: String = ""
    private var currentSheetIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityXlsxEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath)
        supportActionBar?.title = file.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadSpreadsheet()
    }

    private fun loadSpreadsheet() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                try { xlsxHandler.readSpreadsheet(File(filePath)) }
                catch (e: Exception) { null }
            }
            binding.progressBar.visibility = View.GONE

            if (data == null) {
                Toast.makeText(this@XlsxEditorActivity, "Failed to load spreadsheet", Toast.LENGTH_LONG).show()
                return@launch
            }

            if (data.sheets.isEmpty()) return@launch

            // Setup sheet tabs
            binding.tabLayoutSheets.removeAllTabs()
            data.sheets.forEach { sheet ->
                binding.tabLayoutSheets.addTab(
                    binding.tabLayoutSheets.newTab().setText(sheet.name)
                )
            }

            renderSheet(data, currentSheetIndex)

            binding.tabLayoutSheets.addOnTabSelectedListener(object :
                com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                    currentSheetIndex = tab?.position ?: 0
                    renderSheet(data, currentSheetIndex)
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            })
        }
    }

    private fun renderSheet(data: com.documate.app.utils.SpreadsheetData, sheetIndex: Int) {
        val sheet = data.sheets.getOrNull(sheetIndex) ?: return
        binding.tableLayout.removeAllViews()

        if (sheet.rows.isEmpty()) {
            Toast.makeText(this, "Sheet is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Find max columns
        val maxCols = sheet.rows.maxOf { row -> (row.maxOfOrNull { it.colIndex } ?: 0) + 1 }

        sheet.rows.forEach { rowData ->
            val tableRow = TableRow(this)
            tableRow.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )

            // Fill cells up to maxCols
            val cellMap = rowData.associateBy { it.colIndex }
            for (colIndex in 0 until maxCols) {
                val cell = cellMap[colIndex]
                val editText = EditText(this).apply {
                    setText(cell?.value ?: "")
                    minWidth = 120.dpToPx()
                    maxWidth = 200.dpToPx()
                    setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                    setBackgroundResource(R.drawable.cell_border)
                    textSize = 13f
                    if (cell?.isBold == true) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setSingleLine(true)

                    // Save on focus loss
                    val rowIdx = rowData.firstOrNull()?.rowIndex ?: 0
                    val colIdx = colIndex
                    setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            val newValue = text.toString()
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    xlsxHandler.updateCell(File(filePath), sheetIndex, rowIdx, colIdx, newValue)
                                } catch (e: Exception) {
                                    // Silent fail on individual cell save
                                }
                            }
                        }
                    }
                }
                tableRow.addView(editText)
            }
            binding.tableLayout.addView(tableRow)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save  -> {
                Toast.makeText(this, "Changes auto-saved on cell edit", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
