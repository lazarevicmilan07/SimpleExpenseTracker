package com.expensetracker.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.repository.AccountRepository
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.model.ExpenseWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long = savedStateHandle.get<Long>("expenseId") ?: -1L

    private val _transaction = MutableStateFlow<ExpenseWithCategory?>(null)
    val transaction: StateFlow<ExpenseWithCategory?> = _transaction.asStateFlow()

    private val _events = MutableSharedFlow<TransactionDetailEvent>()
    val events = _events.asSharedFlow()

    init {
        loadTransaction()
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            if (expense != null) {
                val category = expense.categoryId?.let { categoryRepository.getCategoryById(it) }
                val subcategory = expense.subcategoryId?.let { categoryRepository.getCategoryById(it) }
                val account = expense.accountId?.let { accountRepository.getAccountById(it) }
                val toAccount = expense.toAccountId?.let { accountRepository.getAccountById(it) }
                _transaction.value = ExpenseWithCategory(expense, category, subcategory, account, toAccount)
            }
        }
    }

    fun deleteTransaction() {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpenseById(expenseId)
                _events.emit(TransactionDetailEvent.TransactionDeleted)
            } catch (e: Exception) {
                _events.emit(TransactionDetailEvent.ShowError("Failed to delete transaction"))
            }
        }
    }
}

sealed class TransactionDetailEvent {
    data object TransactionDeleted : TransactionDetailEvent()
    data class ShowError(val message: String) : TransactionDetailEvent()
}
