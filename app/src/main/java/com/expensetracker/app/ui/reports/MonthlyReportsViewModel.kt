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
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyReportsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<MonthlyReportsUiState> = combine(
        _selectedMonth,
        categories,
        _selectedMonth.flatMapLatest { month ->
            expenseRepository.getExpensesByMonth(month.year, month.monthValue)
        }
    ) { month, categories, expenses ->
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

        MonthlyReportsUiState(
            isLoading = false,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = totalIncome - totalExpense,
            expenseBreakdown = expenseBreakdown,
            incomeBreakdown = incomeBreakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyReportsUiState())

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }
}

data class MonthlyReportsUiState(
    val isLoading: Boolean = true,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList()
)
