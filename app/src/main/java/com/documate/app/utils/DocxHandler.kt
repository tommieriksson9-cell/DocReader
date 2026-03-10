package com.documate.app.utils

import org.apache.poi.xwpf.usermodel.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class DocxParagraph(
    val index: Int,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontSize: Int = 12,
    val alignment: String = "LEFT"
)

class DocxHandler {

    /** Read all paragraphs from a DOCX file */
    fun readParagraphs(file: File): List<DocxParagraph> {
        val paragraphs = mutableListOf<DocxParagraph>()
        FileInputStream(file).use { fis ->
            val doc = XWPFDocument(fis)
            doc.paragraphs.forEachIndexed { index, para ->
                val text = para.text
                val run = para.runs.firstOrNull()
                paragraphs += DocxParagraph(
                    index = index,
                    text = text,
                    isBold = run?.isBold ?: false,
                    isItalic = run?.isItalic ?: false,
                    fontSize = run?.fontSize?.takeIf { it > 0 } ?: 12,
                    alignment = para.alignment?.name ?: "LEFT"
                )
            }
            doc.close()
        }
        return paragraphs
    }

    /** Read full plain text from DOCX */
    fun readFullText(file: File): String {
        return readParagraphs(file).joinToString("\n") { it.text }
    }

    /** Write updated paragraphs back to the DOCX file */
    fun writeParagraphs(file: File, paragraphs: List<DocxParagraph>) {
        val originalDoc = FileInputStream(file).use { XWPFDocument(it) }
        val originalParagraphs = originalDoc.paragraphs

        paragraphs.forEach { updated ->
            val para = originalParagraphs.getOrNull(updated.index) ?: return@forEach
            // Clear existing runs and rewrite
            val existingRuns = para.runs.toList()
            existingRuns.forEach { para.removeRun(para.runs.indexOf(it)) }

            val newRun = para.createRun()
            newRun.setText(updated.text)
            newRun.isBold = updated.isBold
            newRun.isItalic = updated.isItalic
            if (updated.fontSize > 0) newRun.fontSize = updated.fontSize
        }

        FileOutputStream(file).use { fos ->
            originalDoc.write(fos)
        }
        originalDoc.close()
    }

    /** Create a brand-new DOCX file */
    fun createNew(file: File, title: String = "New Document") {
        val doc = XWPFDocument()

        // Title paragraph
        val titlePara = doc.createParagraph()
        titlePara.alignment = ParagraphAlignment.CENTER
        val titleRun = titlePara.createRun()
        titleRun.setText(title)
        titleRun.isBold = true
        titleRun.fontSize = 18

        // Empty body paragraph
        val bodyPara = doc.createParagraph()
        bodyPara.createRun().setText("")

        FileOutputStream(file).use { doc.write(it) }
        doc.close()
    }

    /** Get word count */
    fun wordCount(file: File): Int {
        return readFullText(file).split("\\s+".toRegex()).count { it.isNotBlank() }
    }
}
