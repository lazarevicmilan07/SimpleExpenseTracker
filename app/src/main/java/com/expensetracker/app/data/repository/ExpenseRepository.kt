package com.expensetracker.app.data.repository

import com.expensetracker.app.data.local.dao.CategoryTotal
import com.expensetracker.app.data.local.dao.ExpenseDao
import com.expensetracker.app.data.local.dao.MonthlyTotal
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.data.mapper.toDomain
import com.expensetracker.app.data.mapper.toEntity
import com.expensetracker.app.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    fun getAllExpenses(): Flow<List<Expense>> =
        expenseDao.getAllExpenses().map { entities ->
            entities.map { it.toDomain() }
        }

    fun getExpensesByMonth(year: Int, month: Int): Flow<List<Expense>> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return expenseDao.getExpensesByDateRange(
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun getExpenseById(id: Long): Expense? =
        expenseDao.getExpenseById(id)?.toDomain()

    suspend fun insertExpense(expense: Expense): Long =
        expenseDao.insertExpense(expense.toEntity())

    suspend fun updateExpense(expense: Expense) =
        expenseDao.updateExpense(expense.toEntity())

    suspend fun deleteExpense(expense: Expense) =
        expenseDao.deleteExpense(expense.toEntity())

    suspend fun deleteExpenseById(id: Long) =
        expenseDao.deleteExpenseById(id)

    suspend fun getTotalIncomeByMonth(year: Int, month: Int): Double {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return expenseDao.getTotalByTypeAndDateRange(
            TransactionType.INCOME,
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ) ?: 0.0
    }

    suspend fun getTotalExpenseByMonth(year: Int, month: Int): Double {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return expenseDao.getTotalByTypeAndDateRange(
            TransactionType.EXPENSE,
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ) ?: 0.0
    }

    suspend fun getCategoryTotalsByMonth(year: Int, month: Int): Map<Long?, Double> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return expenseDao.getCategoryTotals(
            TransactionType.EXPENSE,
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ).associate { it.categoryId to it.total }
    }

    suspend fun getAllExpensesSync(): List<Expense> =
        expenseDao.getAllExpensesSync().map { it.toDomain() }

    suspend fun insertExpenses(expenses: List<Expense>) =
        expenseDao.insertExpenses(expenses.map { it.toEntity() })

    suspend fun deleteAllExpenses() =
        expenseDao.deleteAllExpenses()

    fun getCategoryTotalsFlow(type: TransactionType, year: Int, month: Int): Flow<List<CategoryTotal>> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return expenseDao.getCategoryTotalsFlow(type, startDate.toEpochMilli(), endDate.toEpochMilli())
    }

    fun getYearlyCategoryTotals(type: TransactionType, year: Int): Flow<List<CategoryTotal>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return expenseDao.getCategoryTotalsFlow(type, startDate.toEpochMilli(), endDate.toEpochMilli())
    }

    fun getMonthlyTotalsForYear(year: Int): Flow<List<MonthlyTotal>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return expenseDao.getMonthlyTotalsForYear(startDate.toEpochMilli(), endDate.toEpochMilli())
    }

    fun getExpensesByYear(year: Int): Flow<List<Expense>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return expenseDao.getExpensesByDateRange(
            startDate.toEpochMilli(),
            endDate.toEpochMilli()
        ).map { entities -> entities.map { it.toDomain() } }
    }

    private fun LocalDate.toEpochMilli(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
