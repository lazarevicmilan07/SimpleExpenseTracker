package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.ExpenseEntity
import com.expensetracker.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY date DESC, createdAt DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, createdAt DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Query("""
        SELECT SUM(amount) FROM expenses
        WHERE type = :type AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Double?

    @Query("""
        SELECT categoryId, SUM(amount) as total FROM expenses
        WHERE type = :type AND date BETWEEN :startDate AND :endDate
        GROUP BY categoryId
    """)
    suspend fun getCategoryTotals(type: TransactionType, startDate: Long, endDate: Long): List<CategoryTotal>

    @Query("""
        SELECT categoryId, SUM(amount) as total FROM expenses
        WHERE type = :type AND date BETWEEN :startDate AND :endDate
        GROUP BY categoryId
    """)
    fun getCategoryTotalsFlow(type: TransactionType, startDate: Long, endDate: Long): Flow<List<CategoryTotal>>

    @Query("""
        SELECT strftime('%m', date / 1000, 'unixepoch') as month,
               type,
               SUM(amount) as total
        FROM expenses
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY month, type
        ORDER BY month
    """)
    fun getMonthlyTotalsForYear(startDate: Long, endDate: Long): Flow<List<MonthlyTotal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>
}

data class CategoryTotal(
    val categoryId: Long?,
    val total: Double
)

data class MonthlyTotal(
    val month: String,
    val type: TransactionType,
    val total: Double
)
