package com.expensetracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.data.repository.AccountRepository
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.CategoryBreakdown
import com.expensetracker.app.domain.model.ExpenseWithCategory
import com.expensetracker.app.domain.model.MonthlyStats
import com.expensetracker.app.data.local.entity.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val currency: StateFlow<String> = preferencesManager.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    val isPremium: StateFlow<Boolean> = preferencesManager.isPremium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        initializeData()
        loadData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            categoryRepository.initializeDefaultCategories()
            accountRepository.initializeDefaultAccount()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Combine month selection, categories, and expenses reactively
            combine(
                _selectedMonth,
                categoryRepository.getAllCategories(),
                accountRepository.getAllAccounts(),
                _selectedMonth.flatMapLatest { month ->
                    expenseRepository.getExpensesByMonth(month.year, month.monthValue)
                }
            ) { month, categories, accounts, expenses ->
                arrayOf(month, categories, accounts, expenses)
            }.collect { data ->
                @Suppress("UNCHECKED_CAST")
                val categories = data[1] as List<Category>
                @Suppress("UNCHECKED_CAST")
                val accounts = data[2] as List<com.expensetracker.app.domain.model.Account>
                @Suppress("UNCHECKED_CAST")
                val expenses = data[3] as List<com.expensetracker.app.domain.model.Expense>
                updateUiState(categories, accounts, expenses)
            }
        }
    }

    private fun updateUiState(categories: List<Category>, accounts: List<com.expensetracker.app.domain.model.Account>, expenses: List<com.expensetracker.app.domain.model.Expense>) {
        val categoriesMap = categories.associateBy { it.id }
        val accountsMap = accounts.associateBy { it.id }

        // Calculate totals from the current expenses list (reactive)
        val totalIncome = expenses
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        val totalExpense = expenses
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        // Calculate category breakdown for expenses only
        val categoryTotals = expenses
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, expenseList) -> expenseList.sumOf { it.amount } }

        val totalCategoryExpense = categoryTotals.values.sum()
        val breakdown = categoryTotals.map { (categoryId, amount) ->
            CategoryBreakdown(
                category = categoryId?.let { categoriesMap[it] },
                amount = amount,
                percentage = if (totalCategoryExpense > 0) (amount / totalCategoryExpense * 100).toFloat() else 0f
            )
        }.sortedByDescending { it.amount }

        val expensesWithCategory = expenses.map { expense ->
            ExpenseWithCategory(
                expense = expense,
                category = expense.categoryId?.let { categoriesMap[it] },
                subcategory = expense.subcategoryId?.let { categoriesMap[it] },
                account = expense.accountId?.let { accountsMap[it] }
            )
        }

        _uiState.value = DashboardUiState(
            isLoading = false,
            monthlyStats = MonthlyStats(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                balance = totalIncome - totalExpense,
                categoryBreakdown = breakdown
            ),
            recentTransactions = expensesWithCategory,
            categories = categories
        )
    }

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch {
            expenseRepository.deleteExpenseById(expenseId)
            // No need to manually refresh - the Flow will automatically emit new data
        }
    }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val monthlyStats: MonthlyStats = MonthlyStats(0.0, 0.0, 0.0, emptyList()),
    val recentTransactions: List<ExpenseWithCategory> = emptyList(),
    val categories: List<Category> = emptyList()
)
