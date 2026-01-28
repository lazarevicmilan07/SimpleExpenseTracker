package com.expensetracker.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.OutputStreamWriter
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun exportToCsv(context: Context, uri: Uri): Result<Unit> = runCatching {
        val expenses = expenseRepository.getAllExpensesSync()
        val categories = categoryRepository.getAllCategories().let { flow ->
            var result = emptyList<com.expensetracker.app.domain.model.Category>()
            flow.collect { result = it; return@collect }
            result
        }
        val categoriesMap = categories.associateBy { it.id }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                val csvPrinter = CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                    "Date", "Type", "Category", "Amount", "Note"
                ))

                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                expenses.forEach { expense ->
                    val categoryName = expense.categoryId?.let { categoriesMap[it]?.name } ?: "Uncategorized"
                    val subcategoryName = expense.subcategoryId?.let { categoriesMap[it]?.name }
                    val fullCategoryName = if (subcategoryName != null) "$categoryName / $subcategoryName" else categoryName
                    csvPrinter.printRecord(
                        expense.date.format(dateFormatter),
                        expense.type.name,
                        fullCategoryName,
                        expense.amount,
                        expense.note
                    )
                }

                csvPrinter.flush()
            }
        }
    }

    suspend fun exportToPdf(context: Context, uri: Uri): Result<Unit> = runCatching {
        val expenses = expenseRepository.getAllExpensesSync()
        val categories = categoryRepository.getAllCategories().let { flow ->
            var result = emptyList<com.expensetracker.app.domain.model.Category>()
            flow.collect { result = it; return@collect }
            result
        }
        val categoriesMap = categories.associateBy { it.id }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            val pdfWriter = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            // Title
            document.add(
                Paragraph("Expense Report")
                    .setFontSize(24f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            // Summary
            val totalIncome = expenses.filter { it.type == com.expensetracker.app.data.local.entity.TransactionType.INCOME }
                .sumOf { it.amount }
            val totalExpense = expenses.filter { it.type == com.expensetracker.app.data.local.entity.TransactionType.EXPENSE }
                .sumOf { it.amount }

            document.add(Paragraph("Total Income: $${String.format("%.2f", totalIncome)}").setFontSize(12f))
            document.add(Paragraph("Total Expenses: $${String.format("%.2f", totalExpense)}").setFontSize(12f))
            document.add(Paragraph("Balance: $${String.format("%.2f", totalIncome - totalExpense)}").setFontSize(12f).setMarginBottom(20f))

            // Table
            val table = Table(UnitValue.createPercentArray(floatArrayOf(2f, 1.5f, 2f, 1.5f, 3f)))
                .useAllAvailableWidth()

            // Header
            listOf("Date", "Type", "Category", "Amount", "Note").forEach { header ->
                table.addHeaderCell(
                    Cell().add(Paragraph(header).setBold())
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                )
            }

            val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

            expenses.forEach { expense ->
                val categoryName = expense.categoryId?.let { categoriesMap[it]?.name } ?: "Uncategorized"
                val subcategoryName = expense.subcategoryId?.let { categoriesMap[it]?.name }
                val fullCategoryName = if (subcategoryName != null) "$categoryName / $subcategoryName" else categoryName
                table.addCell(expense.date.format(dateFormatter))
                table.addCell(expense.type.name)
                table.addCell(fullCategoryName)
                table.addCell("$${String.format("%.2f", expense.amount)}")
                table.addCell(expense.note)
            }

            document.add(table)
            document.close()
        }
    }
}
