package com.documate.app.ui.editor

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.documate.app.R
import com.documate.app.databinding.ActivityDocxEditorBinding
import com.documate.app.utils.DocxHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocxEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var binding: ActivityDocxEditorBinding
    private val docxHandler = DocxHandler()
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
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    docxHandler.readFullText(file)
                } catch (e: Exception) {
                    "Error reading document: ${e.message}"
                }
            }
            binding.progressBar.visibility = android.view.View.GONE
            binding.editText.setText(text)
            binding.textWordCount.text = "Words: ${text.split("\\s+".toRegex()).count { it.isNotBlank() }}"
        }
    }

    private fun setupFormatButtons() {
        // Update word count as user types
        binding.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val count = s.toString().split("\\s+".toRegex()).count { it.isNotBlank() }
                binding.textWordCount.text = "Words: $count"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.buttonBold.setOnClickListener { toggleBold() }
        binding.buttonItalic.setOnClickListener { toggleItalic() }
    }

    private fun toggleBold() {
        val start = binding.editText.selectionStart
        val end = binding.editText.selectionEnd
        if (start < end) {
            val spannable = android.text.SpannableString(binding.editText.text)
            val existingSpans = spannable.getSpans(start, end, android.text.style.StyleSpan::class.java)
            val hasBold = existingSpans.any { it.style == android.graphics.Typeface.BOLD }
            if (hasBold) {
                existingSpans.filter { it.style == android.graphics.Typeface.BOLD }
                    .forEach { spannable.removeSpan(it) }
            } else {
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.editText.setText(spannable)
            binding.editText.setSelection(start, end)
        }
    }

    private fun toggleItalic() {
        val start = binding.editText.selectionStart
        val end = binding.editText.selectionEnd
        if (start < end) {
            val spannable = android.text.SpannableString(binding.editText.text)
            val existingSpans = spannable.getSpans(start, end, android.text.style.StyleSpan::class.java)
            val hasItalic = existingSpans.any { it.style == android.graphics.Typeface.ITALIC }
            if (hasItalic) {
                existingSpans.filter { it.style == android.graphics.Typeface.ITALIC }
                    .forEach { spannable.removeSpan(it) }
            } else {
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                    start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.editText.setText(spannable)
            binding.editText.setSelection(start, end)
        }
    }

    private fun saveDocument() {
        val file = File(filePath)
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Read existing structure, update paragraph text
                    val paragraphs = docxHandler.readParagraphs(file)
                    val newText = binding.editText.text.toString()
                    val newLines = newText.split("\n")

                    val updatedParagraphs = paragraphs.mapIndexed { i, para ->
                        para.copy(text = newLines.getOrElse(i) { "" })
                    }
                    docxHandler.writeParagraphs(file, updatedParagraphs)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DocxEditorActivity,
                            "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            binding.progressBar.visibility = android.view.View.GONE
            Toast.makeText(this@DocxEditorActivity, "Saved!", Toast.LENGTH_SHORT).show()
        }
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
