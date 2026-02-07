package com.expensetracker.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.CategoryBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Year
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearlyReportsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(Year.now().value)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<YearlyReportsUiState> = combine(
        _selectedYear,
        categories,
        _selectedYear.flatMapLatest { year ->
            expenseRepository.getExpensesByYear(year)
        },
        _selectedYear.flatMapLatest { year ->
            expenseRepository.getMonthlyTotalsForYear(year)
        }
    ) { year, categories, expenses, monthlyTotals ->
        val categoriesMap = categories.associateBy { it.id }

        val totalIncome = expenses
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val totalExpense = expenses
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        // Expense breakdown by category
        val expenseByCategory = expenses
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, expenseList) -> expenseList.sumOf { it.amount } }

        val totalCategoryExpense = expenseByCategory.values.sum()
        val expenseBreakdown = expenseByCategory.map { (categoryId, amount) ->
            CategoryBreakdown(
                category = categoryId?.let { categoriesMap[it] },
                amount = amount,
                percentage = if (totalCategoryExpense > 0) (amount / totalCategoryExpense * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }

        // Income breakdown by category
        val incomeByCategory = expenses
            .filter { it.type == TransactionType.INCOME }
            .groupBy { it.categoryId }
            .mapValues { (_, incomeList) -> incomeList.sumOf { it.amount } }

        val totalCategoryIncome = incomeByCategory.values.sum()
        val incomeBreakdown = incomeByCategory.map { (categoryId, amount) ->
            CategoryBreakdown(
                category = categoryId?.let { categoriesMap[it] },
                amount = amount,
                percentage = if (totalCategoryIncome > 0) (amount / totalCategoryIncome * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }

        // Monthly data for bar chart
        val monthlyData = (1..12).map { month ->
            val monthStr = month.toString().padStart(2, '0')
            val income = monthlyTotals
                .find { it.month == monthStr && it.type == TransactionType.INCOME }
                ?.total ?: 0.0
            val expense = monthlyTotals
                .find { it.month == monthStr && it.type == TransactionType.EXPENSE }
                ?.total ?: 0.0
            MonthData(
                month = month,
                income = income,
                expense = expense
            )
        }

        YearlyReportsUiState(
            isLoading = false,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = totalIncome - totalExpense,
            expenseBreakdown = expenseBreakdown,
            incomeBreakdown = incomeBreakdown,
            monthlyData = monthlyData
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearlyReportsUiState())

    fun previousYear() {
        _selectedYear.value = _selectedYear.value - 1
    }

    fun nextYear() {
        _selectedYear.value = _selectedYear.value + 1
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }
}

data class YearlyReportsUiState(
    val isLoading: Boolean = true,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
    val monthlyData: List<MonthData> = emptyList()
)

data class MonthData(
    val month: Int,
    val income: Double,
    val expense: Double
)
