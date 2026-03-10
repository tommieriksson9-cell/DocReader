package com.documate.app.utils

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class SpreadsheetData(val sheets: List<SheetData>)

data class SheetData(val name: String, val rows: List<List<CellData>>)

data class CellData(
    val rowIndex: Int,
    val colIndex: Int,
    val value: String,
    val isBold: Boolean = false,
    val isNumeric: Boolean = false
)

class XlsxHandler {

    fun readSpreadsheet(file: File): SpreadsheetData {
        val sheets = mutableListOf<SheetData>()
        FileInputStream(file).use { fis ->
            val workbook: Workbook = WorkbookFactory.create(fis)
            val formula = workbook.creationHelper.createFormulaEvaluator()

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val rows  = mutableListOf<List<CellData>>()

                sheet.forEach { row ->
                    val cells = mutableListOf<CellData>()
                    row.forEach { cell ->
                        val value = getCellStringValue(cell, formula)
                        // Use getFontIndex / getFont safely for POI 4.x
                        val isBold = try {
                            val fontIndex = cell.cellStyle?.fontIndexAsInt ?: 0
                            workbook.getFontAt(fontIndex)?.bold ?: false
                        } catch (e: Exception) { false }

                        cells += CellData(
                            rowIndex  = cell.rowIndex,
                            colIndex  = cell.columnIndex,
                            value     = value,
                            isBold    = isBold,
                            isNumeric = cell.cellType == CellType.NUMERIC
                        )
                    }
                    if (cells.isNotEmpty()) rows += cells
                }
                sheets += SheetData(sheet.sheetName, rows)
            }
            workbook.close()
        }
        return SpreadsheetData(sheets)
    }

    private fun getCellStringValue(cell: Cell, evaluator: FormulaEvaluator): String {
        return try {
            when (cell.cellType) {
                CellType.STRING  -> cell.stringCellValue
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell))
                        cell.dateCellValue.toString()
                    else {
                        val num = cell.numericCellValue
                        if (num == kotlin.math.floor(num)) num.toLong().toString()
                        else num.toString()
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    val ev = evaluator.evaluate(cell)
                    when (ev.cellType) {
                        CellType.STRING  -> ev.stringValue
                        CellType.NUMERIC -> ev.numberValue.toString()
                        CellType.BOOLEAN -> ev.booleanValue.toString()
                        else             -> ""
                    }
                }
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    fun updateCell(file: File, sheetIndex: Int, rowIndex: Int, colIndex: Int, newValue: String) {
        FileInputStream(file).use { fis ->
            val workbook = XSSFWorkbook(fis)
            val sheet = workbook.getSheetAt(sheetIndex)
            val row   = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            val cell  = row.getCell(colIndex)  ?: row.createCell(colIndex)
            val num   = newValue.toDoubleOrNull()
            if (num != null) cell.setCellValue(num) else cell.setCellValue(newValue)
            FileOutputStream(file).use { fos -> workbook.write(fos) }
            workbook.close()
        }
    }

    fun createNew(file: File, sheetName: String = "Sheet1") {
        val workbook = XSSFWorkbook()
        val sheet    = workbook.createSheet(sheetName)
        val headerRow   = sheet.createRow(0)
        val headerStyle = workbook.createCellStyle()
        val font        = workbook.createFont()
        font.bold = true
        headerStyle.setFont(font)
        listOf("Column A", "Column B", "Column C").forEachIndexed { i, header ->
            val cell = headerRow.createCell(i)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        for (i in 0..2) sheet.autoSizeColumn(i)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
    }

    fun getSheetNames(file: File): List<String> {
        FileInputStream(file).use { fis ->
            val workbook = WorkbookFactory.create(fis)
            val names = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
            workbook.close()
            return names
        }
    }
}
