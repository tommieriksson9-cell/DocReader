package com.documate.app.ui.editor

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.documate.app.R
import com.documate.app.databinding.ActivityDocxEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class DocxEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityDocxEditorBinding
    private var filePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocxEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath)
        supportActionBar?.title = file.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadDocument(file)
        setupFormatButtons()
    }

    private fun loadDocument(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    // Safe text extraction — avoids POI crash on external DOCX files
                    extractTextFromDocx(file)
                } catch (e: Exception) {
                    "Could not read document: ${e.message}"
                }
            }
            binding.progressBar.visibility = View.GONE
            binding.editText.setText(text)
            val wordCount = text.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
            binding.textWordCount.text = "Words: $wordCount"
        }
    }

    private fun extractTextFromDocx(file: File): String {
        // Use ZipInputStream to read document.xml directly — avoids POI memory issues
        val sb = StringBuilder()
        val zis = java.util.zip.ZipInputStream(file.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val content = zis.bufferedReader().readText()
                // Strip XML tags, keep text content
                val text = content
                    .replace(Regex("<w:br[^/]*/?>"), "\n")
                    .replace(Regex("<w:p[ >][^>]*>|<w:p>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("&amp;"), "&")
                    .replace(Regex("&lt;"), "<")
                    .replace(Regex("&gt;"), ">")
                    .replace(Regex("&quot;"), "\"")
                    .replace(Regex("&apos;"), "'")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                sb.append(text)
                break
            }
            entry = zis.nextEntry
        }
        zis.close()
        return sb.toString().ifEmpty { "Document appears to be empty." }
    }

    private fun saveDocument() {
        val file = File(filePath)
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Save as plain txt alongside original for simplicity
                    val outFile = File(file.parent, file.nameWithoutExtension + "_edited.txt")
                    outFile.writeText(binding.editText.text.toString())
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DocxEditorActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    return@withContext
                }
            }
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this@DocxEditorActivity, "Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFormatButtons() {
        binding.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val count = s.toString().trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                binding.textWordCount.text = "Words: $count"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.buttonBold.setOnClickListener { toggleStyle(android.graphics.Typeface.BOLD) }
        binding.buttonItalic.setOnClickListener { toggleStyle(android.graphics.Typeface.ITALIC) }
    }

    private fun toggleStyle(style: Int) {
        val start = binding.editText.selectionStart
        val end   = binding.editText.selectionEnd
        if (start >= end) return
        val spannable = android.text.SpannableString(binding.editText.text)
        val existing  = spannable.getSpans(start, end, android.text.style.StyleSpan::class.java)
        val hasStyle  = existing.any { it.style == style }
        if (hasStyle) existing.filter { it.style == style }.forEach { spannable.removeSpan(it) }
        else spannable.setSpan(android.text.style.StyleSpan(style), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.editText.setText(spannable)
        binding.editText.setSelection(start, end)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_save  -> { saveDocument(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
