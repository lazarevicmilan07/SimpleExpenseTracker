package com.expensetracker.app.ui.categories

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.data.repository.CategoryRepository
import com.expensetracker.app.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private val _expandedCategories = MutableStateFlow<Set<Long>>(emptySet())
    val expandedCategories: StateFlow<Set<Long>> = _expandedCategories.asStateFlow()

    val categoriesState: StateFlow<CategoriesListState> = combine(
        categoryRepository.getAllCategories(),
        preferencesManager.isPremium
    ) { categories, isPremium ->
        val rootCategories = categories.filter { it.parentCategoryId == null }
        val subcategoriesMap = categories
            .filter { it.parentCategoryId != null }
            .groupBy { it.parentCategoryId!! }
        CategoriesListState(
            categories = categories,
            rootCategories = rootCategories,
            subcategoriesMap = subcategoriesMap,
            isPremium = isPremium,
            canAddMore = isPremium || categories.size < FREE_CATEGORY_LIMIT
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesListState())

    private val _events = MutableSharedFlow<CategoryEvent>()
    val events = _events.asSharedFlow()

    fun toggleCategoryExpanded(categoryId: Long) {
        _expandedCategories.value = if (categoryId in _expandedCategories.value) {
            _expandedCategories.value - categoryId
        } else {
            _expandedCategories.value + categoryId
        }
    }

    fun showAddDialog(parentCategoryId: Long? = null) {
        val state = categoriesState.value
        if (!state.canAddMore) {
            viewModelScope.launch {
                _events.emit(CategoryEvent.ShowPremiumRequired)
            }
            return
        }
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingCategory = null,
            dialogName = "",
            dialogIcon = "category",
            dialogColor = Color(0xFF4ECDC4),
            parentCategoryId = parentCategoryId
        )
    }

    fun showEditDialog(category: Category) {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingCategory = category,
            dialogName = category.name,
            dialogIcon = category.icon,
            dialogColor = category.color
        )
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showDialog = false)
    }

    fun updateDialogName(name: String) {
        _uiState.value = _uiState.value.copy(dialogName = name)
    }

    fun updateDialogIcon(icon: String) {
        _uiState.value = _uiState.value.copy(dialogIcon = icon)
    }

    fun updateDialogColor(color: Color) {
        _uiState.value = _uiState.value.copy(dialogColor = color)
    }

    fun saveCategory() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.dialogName.isBlank()) {
                _events.emit(CategoryEvent.ShowError("Category name cannot be empty"))
                return@launch
            }

            val category = Category(
                id = state.editingCategory?.id ?: 0,
                name = state.dialogName.trim(),
                icon = state.dialogIcon,
                color = state.dialogColor,
                isDefault = state.editingCategory?.isDefault ?: false,
                parentCategoryId = state.editingCategory?.parentCategoryId ?: state.parentCategoryId
            )

            if (state.editingCategory != null) {
                categoryRepository.updateCategory(category)
            } else {
                categoryRepository.insertCategory(category)
            }

            hideDialog()
            _events.emit(CategoryEvent.CategorySaved)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            // Check if category has subcategories
            if (categoryRepository.hasSubcategories(category.id)) {
                _events.emit(CategoryEvent.ShowError("Cannot delete category with subcategories. Delete subcategories first."))
                return@launch
            }
            categoryRepository.deleteCategory(category)
            _events.emit(CategoryEvent.CategoryDeleted)
        }
    }

    companion object {
        const val FREE_CATEGORY_LIMIT = 10
    }
}

data class CategoriesUiState(
    val showDialog: Boolean = false,
    val editingCategory: Category? = null,
    val dialogName: String = "",
    val dialogIcon: String = "category",
    val dialogColor: Color = Color(0xFF4ECDC4),
    val parentCategoryId: Long? = null
)

data class CategoriesListState(
    val categories: List<Category> = emptyList(),
    val rootCategories: List<Category> = emptyList(),
    val subcategoriesMap: Map<Long, List<Category>> = emptyMap(),
    val isPremium: Boolean = false,
    val canAddMore: Boolean = true
)

sealed class CategoryEvent {
    data object CategorySaved : CategoryEvent()
    data object CategoryDeleted : CategoryEvent()
    data object ShowPremiumRequired : CategoryEvent()
    data class ShowError(val message: String) : CategoryEvent()
}
