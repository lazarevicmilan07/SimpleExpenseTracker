package com.expensetracker.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.data.preferences.PreferencesManager
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

enum class TransactionField {
    NONE,
    DATE,
    ACCOUNT,
    TO_ACCOUNT,
    CATEGORY,
    SUBCATEGORY,
    AMOUNT,
    NOTE
}

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawExpenseId: Long? = savedStateHandle.get<Long>("expenseId")?.takeIf { it != -1L }
    private val copyFromId: Long? = if (savedStateHandle.get<Boolean>("useToday") != null) rawExpenseId else null
    private val useToday: Boolean = savedStateHandle.get<Boolean>("useToday") ?: false

    // When copying, expenseId must be null so save inserts a new record instead of updating the original
    private val expenseId: Long? = if (copyFromId != null) null else rawExpenseId

    val expenseIdForCopy: Long? = if (copyFromId == null) rawExpenseId else null

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    val currency: StateFlow<String> = preferencesManager.currency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

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
        if (copyFromId != null) {
            loadCopy(copyFromId, useToday)
        } else if (expenseId != null) {
            loadExpense(expenseId)
        } else {
            // New transaction - set focus to account selection
            _uiState.value = _uiState.value.copy(currentField = TransactionField.ACCOUNT)
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
                    toAccountId = expense.toAccountId,
                    transactionType = expense.type,
                    selectedDate = expense.date,
                    isEditing = true,
                    currentField = TransactionField.NONE
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

    private fun loadCopy(id: Long, useToday: Boolean) {
        viewModelScope.launch {
            expenseRepository.getExpenseById(id)?.let { expense ->
                _uiState.value = _uiState.value.copy(
                    amount = String.format("%.2f", expense.amount),
                    note = expense.note,
                    selectedCategoryId = expense.subcategoryId ?: expense.categoryId,
                    selectedParentCategoryId = expense.categoryId,
                    selectedAccountId = expense.accountId,
                    toAccountId = expense.toAccountId,
                    transactionType = expense.type,
                    selectedDate = if (useToday) LocalDate.now() else expense.date,
                    isEditing = false,
                    currentField = TransactionField.NONE
                )
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

    private var shouldClearAmount = false

    fun setCurrentField(field: TransactionField) {
        if (field == TransactionField.AMOUNT && _uiState.value.amount.isNotEmpty()) {
            shouldClearAmount = true
        }
        _uiState.value = _uiState.value.copy(currentField = field)
    }

    fun updateAmount(amount: String) {
        shouldClearAmount = false
        val filtered = amount.filter { it.isDigit() || it == '.' || it == '-' }
        if (filtered.count { it == '.' } <= 1) {
            _uiState.value = _uiState.value.copy(amount = filtered)
        }
    }

    fun appendToAmount(digit: String) {
        if (shouldClearAmount) {
            shouldClearAmount = false
            _uiState.value = _uiState.value.copy(amount = digit)
            return
        }
        val current = _uiState.value.amount
        if (digit == "." && current.replace("-", "").contains(".")) return
        val newAmount = current + digit
        _uiState.value = _uiState.value.copy(amount = newAmount)
    }

    fun deleteLastDigit() {
        if (shouldClearAmount) {
            shouldClearAmount = false
            _uiState.value = _uiState.value.copy(amount = "")
            return
        }
        val current = _uiState.value.amount
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(amount = current.dropLast(1))
        }
    }

    fun clearAmount() {
        _uiState.value = _uiState.value.copy(amount = "")
    }

    fun toggleMinus() {
        val current = _uiState.value.amount
        val newAmount = if (current.startsWith("-")) {
            current.removePrefix("-")
        } else {
            "-$current"
        }
        _uiState.value = _uiState.value.copy(amount = newAmount)
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
                    // No subcategories, use parent category directly, move to amount
                    _uiState.value = _uiState.value.copy(
                        selectedCategoryId = categoryId,
                        selectedParentCategoryId = categoryId,
                        showSubcategorySelector = false,
                        currentField = TransactionField.AMOUNT
                    )
                } else {
                    // Show subcategory selector
                    _uiState.value = _uiState.value.copy(
                        selectedParentCategoryId = categoryId,
                        selectedCategoryId = null,
                        showSubcategorySelector = true,
                        currentField = TransactionField.SUBCATEGORY
                    )
                }
            }
        }
    }

    // Called when clicking on category that has subcategories showing - to select parent only
    fun selectParentCategoryOnly(categoryId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = categoryId,
            selectedParentCategoryId = categoryId,
            showSubcategorySelector = false,
            currentField = TransactionField.AMOUNT
        )
    }

    fun selectSubcategory(subcategoryId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedCategoryId = subcategoryId,
            currentField = TransactionField.AMOUNT
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

    fun getSelectedCategory(): Category? {
        val categoryId = _uiState.value.selectedCategoryId ?: return null
        return allCategories.value.find { it.id == categoryId }
    }

    fun getSelectedParentCategory(): Category? {
        val parentId = _uiState.value.selectedParentCategoryId ?: return null
        return allCategories.value.find { it.id == parentId }
    }

    fun getCategoryDisplayText(): String {
        val parentCategory = getSelectedParentCategory()
        val selectedCategory = getSelectedCategory()

        return when {
            parentCategory == null && selectedCategory == null -> ""
            parentCategory != null && selectedCategory != null && parentCategory.id != selectedCategory.id ->
                "${parentCategory.name}/${selectedCategory.name}"
            parentCategory != null -> parentCategory.name
            selectedCategory != null -> selectedCategory.name
            else -> ""
        }
    }

    fun selectAccount(accountId: Long?) {
        val nextField = if (_uiState.value.transactionType == TransactionType.TRANSFER) {
            TransactionField.TO_ACCOUNT
        } else {
            TransactionField.CATEGORY
        }
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            currentField = nextField
        )
    }

    fun selectToAccount(accountId: Long?) {
        _uiState.value = _uiState.value.copy(
            toAccountId = accountId,
            currentField = TransactionField.AMOUNT
        )
    }

    fun selectTransactionType(type: TransactionType) {
        _uiState.value = _uiState.value.copy(
            transactionType = type,
            // Reset selections when changing type
            selectedCategoryId = if (type == TransactionType.TRANSFER) null else _uiState.value.selectedCategoryId,
            selectedParentCategoryId = if (type == TransactionType.TRANSFER) null else _uiState.value.selectedParentCategoryId,
            toAccountId = if (type != TransactionType.TRANSFER) null else _uiState.value.toAccountId
            // Don't change currentField - keep focus where it was
        )
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

            if (amount == null || amount <= 0.0) {
                _events.emit(TransactionEvent.ShowError("Please enter an amount greater than zero"))
                return@launch
            }

            if (state.selectedAccountId == null) {
                _events.emit(TransactionEvent.ShowError("Please select an account"))
                return@launch
            }

            if (state.transactionType == TransactionType.TRANSFER) {
                // Validate transfer
                if (state.toAccountId == null) {
                    _events.emit(TransactionEvent.ShowError("Please select destination account"))
                    return@launch
                }
                if (state.selectedAccountId == state.toAccountId) {
                    _events.emit(TransactionEvent.ShowError("Source and destination accounts must be different"))
                    return@launch
                }

                // Save transfer transaction
                val expense = Expense(
                    id = expenseId ?: 0,
                    amount = amount,
                    note = state.note,
                    categoryId = null,
                    subcategoryId = null,
                    accountId = state.selectedAccountId,
                    toAccountId = state.toAccountId,
                    type = TransactionType.TRANSFER,
                    date = state.selectedDate
                )

                if (expenseId != null) {
                    expenseRepository.updateExpense(expense)
                } else {
                    expenseRepository.insertExpense(expense)
                }
            } else {
                // Income or Expense
                val parentId = state.selectedParentCategoryId
                val childId = state.selectedCategoryId

                if (parentId == null && childId == null) {
                    _events.emit(TransactionEvent.ShowError("Please select a category"))
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
                    toAccountId = null,
                    type = state.transactionType,
                    date = state.selectedDate
                )

                if (expenseId != null) {
                    expenseRepository.updateExpense(expense)
                } else {
                    expenseRepository.insertExpense(expense)
                }
            }

            if (andContinue) {
                // Reset form but keep type, date, and account for convenience
                _selectedParentCategoryId.value = null
                _availableSubcategories.value = emptyList()
                _uiState.value = _uiState.value.copy(
                    amount = "",
                    note = "",
                    selectedCategoryId = null,
                    selectedParentCategoryId = null,
                    toAccountId = null,
                    showSubcategorySelector = false,
                    currentField = TransactionField.ACCOUNT
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
    val toAccountId: Long? = null,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val selectedDate: LocalDate = LocalDate.now(),
    val isEditing: Boolean = false,
    val showSubcategorySelector: Boolean = false,
    val currentField: TransactionField = TransactionField.ACCOUNT
)

sealed class TransactionEvent {
    data object TransactionSaved : TransactionEvent()
    data object TransactionSavedAndContinue : TransactionEvent()
    data object TransactionDeleted : TransactionEvent()
    data class ShowError(val message: String) : TransactionEvent()
}
