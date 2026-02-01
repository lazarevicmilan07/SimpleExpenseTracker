package com.expensetracker.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.data.repository.AccountRepository
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.model.Account
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long? = savedStateHandle.get<Long>("expenseId")?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // All categories for lookup
    private val allCategories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Root categories (no parent) for initial selection
    val rootCategories: StateFlow<List<Category>> = categoryRepository.getRootCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Subcategories of currently selected parent category
    private val _selectedParentCategoryId = MutableStateFlow<Long?>(null)
    private val _availableSubcategories = MutableStateFlow<List<Category>>(emptyList())
    val availableSubcategories: StateFlow<List<Category>> = _availableSubcategories.asStateFlow()

    // For backward compatibility
    val categories: StateFlow<List<Category>> = allCategories

    val accounts: StateFlow<List<Account>> = accountRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<TransactionEvent>()
    val events = _events.asSharedFlow()

    init {
        if (expenseId != null) {
            loadExpense(expenseId)
        } else {
            loadDefaultAccount()
        }
    }

    private fun loadDefaultAccount() {
        viewModelScope.launch {
            accountRepository.getDefaultAccount()?.let { account ->
                _uiState.value = _uiState.value.copy(selectedAccountId = account.id)
            }
        }
    }

    private fun loadExpense(id: Long) {
        viewModelScope.launch {
            expenseRepository.getExpenseById(id)?.let { expense ->
                _uiState.value = _uiState.value.copy(
                    amount = String.format("%.2f", expense.amount),
                    note = expense.note,
                    selectedCategoryId = expense.subcategoryId ?: expense.categoryId,
                    selectedParentCategoryId = expense.categoryId,
                    selectedAccountId = expense.accountId,
                    transactionType = expense.type,
                    selectedDate = expense.date,
                    isEditing = true
                )
                // If subcategoryId is set, load subcategories and show selector
                if (expense.subcategoryId != null && expense.categoryId != null) {
                    _selectedParentCategoryId.value = expense.categoryId
                    categoryRepository.getSubcategories(expense.categoryId).collect { subcategories ->
                        _availableSubcategories.value = subcategories
                        if (subcategories.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(showSubcategorySelector = true)
                        }
                    }
                }
            }
        }
    }

    fun updateAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.value = _uiState.value.copy(amount = filtered)
        }
    }

    fun formatAmount() {
        val current = _uiState.value.amount
        val value = current.toDoubleOrNull()
        if (value != null) {
            _uiState.value = _uiState.value.copy(amount = String.format("%.2f", value))
        }
    }

    fun updateNote(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    fun selectCategory(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    fun selectParentCategory(categoryId: Long) {
        _selectedParentCategoryId.value = categoryId
        viewModelScope.launch {
            categoryRepository.getSubcategories(categoryId).collect { subcategories ->
                _availableSubcategories.value = subcategories
                if (subcategories.isEmpty()) {
                    // No subcategories, use parent category directly
                    _uiState.value = _uiState.value.copy(
                        selectedCategoryId = categoryId,
                        selectedParentCategoryId = categoryId,
                        showSubcategorySelector = false
                    )
                } else {
                    // Show subcategory selector
                    _uiState.value = _uiState.value.copy(
                        selectedParentCategoryId = categoryId,
                        selectedCategoryId = null, // Clear until subcategory is selected
                        showSubcategorySelector = true
                    )
                }
            }
        }
    }

    fun selectSubcategory(subcategoryId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = subcategoryId
        )
    }

    fun clearSubcategorySelection() {
        _selectedParentCategoryId.value = null
        _availableSubcategories.value = emptyList()
        _uiState.value = _uiState.value.copy(
            showSubcategorySelector = false,
            selectedParentCategoryId = null,
            selectedCategoryId = null
        )
    }

    fun getSelectedCategoryName(): String? {
        val categoryId = _uiState.value.selectedCategoryId ?: return null
        return allCategories.value.find { it.id == categoryId }?.name
    }

    fun getSelectedParentCategoryName(): String? {
        val parentId = _selectedParentCategoryId.value ?: return null
        return allCategories.value.find { it.id == parentId }?.name
    }

    fun selectAccount(accountId: Long?) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun selectTransactionType(type: TransactionType) {
        _uiState.value = _uiState.value.copy(transactionType = type)
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun saveTransaction() {
        doSave(andContinue = false)
    }

    fun saveAndContinue() {
        doSave(andContinue = true)
    }

    private fun doSave(andContinue: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = state.amount.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                _events.emit(TransactionEvent.ShowError("Please enter a valid amount"))
                return@launch
            }

            val parentId = state.selectedParentCategoryId
            val childId = state.selectedCategoryId

            if (parentId == null && childId == null) {
                _events.emit(TransactionEvent.ShowError("Please select a category"))
                return@launch
            }

            // If parent category has subcategories, one must be selected
            if (state.showSubcategorySelector && _availableSubcategories.value.isNotEmpty()) {
                val hasSubcategorySelected = parentId != null && childId != null && parentId != childId
                if (!hasSubcategorySelected) {
                    _events.emit(TransactionEvent.ShowError("Please select a subcategory"))
                    return@launch
                }
            }

            if (state.selectedAccountId == null) {
                _events.emit(TransactionEvent.ShowError("Please select an account"))
                return@launch
            }

            val hasSubcategory = parentId != null && childId != null && parentId != childId

            val expense = Expense(
                id = expenseId ?: 0,
                amount = amount,
                note = state.note,
                categoryId = parentId ?: childId,
                subcategoryId = if (hasSubcategory) childId else null,
                accountId = state.selectedAccountId,
                type = state.transactionType,
                date = state.selectedDate
            )

            if (expenseId != null) {
                expenseRepository.updateExpense(expense)
            } else {
                expenseRepository.insertExpense(expense)
            }

            if (andContinue) {
                // Reset form but keep type, date, and account for convenience
                _uiState.value = _uiState.value.copy(
                    amount = "",
                    note = "",
                    selectedCategoryId = null,
                    selectedParentCategoryId = null,
                    showSubcategorySelector = false
                )
                _events.emit(TransactionEvent.TransactionSavedAndContinue)
            } else {
                _events.emit(TransactionEvent.TransactionSaved)
            }
        }
    }

    fun deleteTransaction() {
        val id = expenseId ?: return
        viewModelScope.launch {
            expenseRepository.deleteExpenseById(id)
            _events.emit(TransactionEvent.TransactionDeleted)
        }
    }
}

data class TransactionUiState(
    val amount: String = "",
    val note: String = "",
    val selectedCategoryId: Long? = null,
    val selectedParentCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val selectedDate: LocalDate = LocalDate.now(),
    val isEditing: Boolean = false,
    val showSubcategorySelector: Boolean = false
)

sealed class TransactionEvent {
    data object TransactionSaved : TransactionEvent()
    data object TransactionSavedAndContinue : TransactionEvent()
    data object TransactionDeleted : TransactionEvent()
    data class ShowError(val message: String) : TransactionEvent()
}
