package com.expensetracker.app.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRestoreUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun backup(context: Context, uri: Uri): Result<Unit> = runCatching {
        val expenses = expenseRepository.getAllExpensesSync()
        val categories = categoryRepository.getAllCategories().first()

        val backupData = BackupData(
            version = BACKUP_VERSION,
            timestamp = System.currentTimeMillis(),
            expenses = expenses.map { it.toBackupExpense() },
            categories = categories.map { it.toBackupCategory() }
        )

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(json.encodeToString(backupData))
            }
        }
    }

    suspend fun restore(context: Context, uri: Uri): Result<Unit> = runCatching {
        val backupData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val content = reader.readText()
                json.decodeFromString<BackupData>(content)
            }
        } ?: throw IllegalStateException("Could not read backup file")

        // Clear existing data
        expenseRepository.deleteAllExpenses()

        // Restore categories first
        val categoryIdMapping = mutableMapOf<Long, Long>()
        backupData.categories.forEach { backupCategory ->
            val category = backupCategory.toCategory()
            val newId = categoryRepository.insertCategory(category.copy(id = 0))
            categoryIdMapping[backupCategory.id] = newId
        }

        // Restore expenses with updated category IDs
        val expenses = backupData.expenses.map { backupExpense ->
            backupExpense.toExpense().copy(
                id = 0,
                categoryId = backupExpense.categoryId?.let { categoryIdMapping[it] }
            )
        }
        expenseRepository.insertExpenses(expenses)
    }

    companion object {
        private const val BACKUP_VERSION = 1
    }
}

@Serializable
data class BackupData(
    val version: Int,
    val timestamp: Long,
    val expenses: List<BackupExpense>,
    val categories: List<BackupCategory>
)

@Serializable
data class BackupExpense(
    val id: Long,
    val amount: Double,
    val note: String,
    val categoryId: Long?,
    val accountId: Long? = null,
    val type: String,
    val date: String,
    val createdAt: Long
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val isDefault: Boolean,
    val createdAt: Long
)

private fun Expense.toBackupExpense() = BackupExpense(
    id = id,
    amount = amount,
    note = note,
    categoryId = categoryId,
    accountId = accountId,
    type = type.name,
    date = date.toString(),
    createdAt = createdAt
)

private fun BackupExpense.toExpense() = Expense(
    id = id,
    amount = amount,
    note = note,
    categoryId = categoryId,
    accountId = accountId,
    type = TransactionType.valueOf(type),
    date = LocalDate.parse(date),
    createdAt = createdAt
)

private fun Category.toBackupCategory() = BackupCategory(
    id = id,
    name = name,
    icon = icon,
    color = color.value.toLong(),
    isDefault = isDefault,
    createdAt = createdAt
)

private fun BackupCategory.toCategory() = Category(
    id = id,
    name = name,
    icon = icon,
    color = Color(color.toULong()),
    isDefault = isDefault,
    createdAt = createdAt
)
