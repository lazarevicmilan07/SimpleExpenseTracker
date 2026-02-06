package com.expensetracker.app.ui.transaction

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.domain.model.Account
import com.expensetracker.app.domain.model.AccountTypeNames
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.components.getCurrencySymbol
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TransferBlue = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    onNavigateBack: () -> Unit,
    onCopyTransaction: ((Long, Boolean) -> Unit)? = null,
    onNavigateToAccounts: (() -> Unit)? = null,
    onNavigateToCategories: (() -> Unit)? = null,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val rootCategories by viewModel.rootCategories.collectAsState()
    val availableSubcategories by viewModel.availableSubcategories.collectAsState()
    val allCategories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current

    // Compute category display text reactively
    val categoryDisplayText = remember(uiState.selectedParentCategoryId, uiState.selectedCategoryId, allCategories) {
        val parentCategory = uiState.selectedParentCategoryId?.let { id -> allCategories.find { it.id == id } }
        val selectedCategory = uiState.selectedCategoryId?.let { id -> allCategories.find { it.id == id } }

        when {
            parentCategory == null && selectedCategory == null -> ""
            parentCategory != null && selectedCategory != null && parentCategory.id != selectedCategory.id ->
                "${parentCategory.name}/${selectedCategory.name}"
            parentCategory != null -> parentCategory.name
            selectedCategory != null -> selectedCategory.name
            else -> ""
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionEvent.TransactionSaved -> {
                    Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is TransactionEvent.TransactionSavedAndContinue -> {
                    Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                }
                is TransactionEvent.TransactionDeleted -> {
                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is TransactionEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val typeColor = when (uiState.transactionType) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferBlue
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.transactionType) {
                            TransactionType.EXPENSE -> "Expense"
                            TransactionType.INCOME -> "Income"
                            TransactionType.TRANSFER -> "Transfer"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditing && onCopyTransaction != null) {
                        IconButton(onClick = { showCopyDialog = true }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Transaction",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content area (scrollable form)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Transaction Type Toggle
                TransactionTypeToggle(
                    selectedType = uiState.transactionType,
                    onTypeSelected = viewModel::selectTransactionType
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Form Fields
                TransactionFormFields(
                    uiState = uiState,
                    accounts = accounts,
                    currency = currency,
                    categoryDisplayText = categoryDisplayText,
                    viewModel = viewModel,
                    onDateClick = { showDatePicker = true }
                )
            }

            // Bottom Panel (changes based on current field)
            BottomSelectionPanel(
                uiState = uiState,
                accounts = accounts,
                rootCategories = rootCategories,
                availableSubcategories = availableSubcategories,
                currency = currency,
                viewModel = viewModel,
                onSave = { viewModel.saveTransaction() },
                onContinue = { viewModel.saveAndContinue() },
                isEditing = uiState.isEditing,
                onNavigateToAccounts = onNavigateToAccounts,
                onNavigateToCategories = onNavigateToCategories
            )
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = uiState.selectedDate,
            onDateSelected = { date ->
                viewModel.selectDate(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showCopyDialog && onCopyTransaction != null) {
        val expenseId = viewModel.expenseIdForCopy
        if (expenseId != null) {
            AlertDialog(
                onDismissRequest = { showCopyDialog = false },
                title = { Text("Copy Transaction") },
                text = { Text("Which date should the copy use?") },
                confirmButton = {
                    TextButton(onClick = {
                        showCopyDialog = false
                        onCopyTransaction(expenseId, true)
                    }) {
                        Text("Today's Date")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCopyDialog = false
                        onCopyTransaction(expenseId, false)
                    }) {
                        Text("Original Date")
                    }
                }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TransactionTypeToggle(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        TransactionType.entries.forEach { type ->
            val isSelected = type == selectedType
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    when (type) {
                        TransactionType.EXPENSE -> ExpenseRed
                        TransactionType.INCOME -> IncomeGreen
                        TransactionType.TRANSFER -> TransferBlue
                    }
                } else Color.Transparent,
                label = "tab_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tab_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onTypeSelected(type) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun TransactionFormFields(
    uiState: TransactionUiState,
    accounts: List<Account>,
    currency: String,
    categoryDisplayText: String,
    viewModel: TransactionViewModel,
    onDateClick: () -> Unit
) {
    val selectedAccount = accounts.find { it.id == uiState.selectedAccountId }
    val selectedToAccount = accounts.find { it.id == uiState.toAccountId }
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy (EEE)")

    val typeColor = when (uiState.transactionType) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferBlue
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Date Field
        FormFieldRow(
            label = "Date",
            value = uiState.selectedDate.format(dateFormatter),
            isActive = uiState.currentField == TransactionField.DATE,
            activeColor = typeColor,
            onClick = onDateClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Account / From Field
        FormFieldRow(
            label = if (uiState.transactionType == TransactionType.TRANSFER) "From" else "Account",
            value = selectedAccount?.name ?: "",
            isActive = uiState.currentField == TransactionField.ACCOUNT,
            activeColor = typeColor,
            onClick = { viewModel.setCurrentField(TransactionField.ACCOUNT) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (uiState.transactionType == TransactionType.TRANSFER) {
            // To Account Field (Transfer only)
            FormFieldRow(
                label = "To",
                value = selectedToAccount?.name ?: "",
                isActive = uiState.currentField == TransactionField.TO_ACCOUNT,
                activeColor = typeColor,
                onClick = { viewModel.setCurrentField(TransactionField.TO_ACCOUNT) }
            )
        } else {
            // Category Field (Income/Expense only)
            FormFieldRow(
                label = "Category",
                value = categoryDisplayText,
                isActive = uiState.currentField == TransactionField.CATEGORY ||
                          uiState.currentField == TransactionField.SUBCATEGORY,
                activeColor = typeColor,
                onClick = { viewModel.setCurrentField(TransactionField.CATEGORY) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Amount Field
        FormFieldRow(
            label = "Amount",
            value = if (uiState.amount.isNotEmpty()) "${getCurrencySymbol(currency)}${uiState.amount}" else "",
            isActive = uiState.currentField == TransactionField.AMOUNT,
            activeColor = typeColor,
            onClick = { viewModel.setCurrentField(TransactionField.AMOUNT) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Note Field
        FormFieldRow(
            label = "Note",
            value = uiState.note,
            isActive = uiState.currentField == TransactionField.NOTE,
            activeColor = typeColor,
            onClick = { viewModel.setCurrentField(TransactionField.NOTE) },
            isDotted = true
        )
    }
}

@Composable
fun FormFieldRow(
    label: String,
    value: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    isDotted: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value.ifEmpty { if (isDotted) ". . . . . . . . . . ." else "" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty())
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
        if (isActive) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(activeColor)
            )
        }
    }
}

@Composable
fun BottomSelectionPanel(
    uiState: TransactionUiState,
    accounts: List<Account>,
    rootCategories: List<Category>,
    availableSubcategories: List<Category>,
    currency: String,
    viewModel: TransactionViewModel,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    isEditing: Boolean,
    onNavigateToAccounts: (() -> Unit)? = null,
    onNavigateToCategories: (() -> Unit)? = null
) {
    val typeColor = when (uiState.transactionType) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferBlue
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Column {
            when (uiState.currentField) {
                TransactionField.ACCOUNT -> {
                    AccountSelectionPanel(
                        accounts = accounts,
                        selectedAccountId = uiState.selectedAccountId,
                        excludeAccountId = null,
                        onAccountSelected = { viewModel.selectAccount(it) },
                        onClose = { viewModel.setCurrentField(TransactionField.NONE) },
                        onEditAccounts = onNavigateToAccounts,
                        title = if (uiState.transactionType == TransactionType.TRANSFER) "From Account" else "Accounts"
                    )
                }
                TransactionField.TO_ACCOUNT -> {
                    AccountSelectionPanel(
                        accounts = accounts,
                        selectedAccountId = uiState.toAccountId,
                        excludeAccountId = uiState.selectedAccountId,
                        onAccountSelected = { viewModel.selectToAccount(it) },
                        onClose = { viewModel.setCurrentField(TransactionField.NONE) },
                        onEditAccounts = onNavigateToAccounts,
                        title = "To Account"
                    )
                }
                TransactionField.CATEGORY -> {
                    CategorySelectionPanel(
                        categories = rootCategories,
                        selectedCategoryId = uiState.selectedParentCategoryId,
                        onCategorySelected = { viewModel.selectParentCategory(it) },
                        onClose = { viewModel.setCurrentField(TransactionField.NONE) },
                        onEditCategories = onNavigateToCategories
                    )
                }
                TransactionField.SUBCATEGORY -> {
                    SubcategorySelectionPanel(
                        allCategories = rootCategories,
                        selectedParentCategoryId = uiState.selectedParentCategoryId,
                        subcategories = availableSubcategories,
                        selectedSubcategoryId = uiState.selectedCategoryId,
                        onCategorySelected = { viewModel.selectParentCategory(it) },
                        onSubcategorySelected = { viewModel.selectSubcategory(it) },
                        onParentSelected = { viewModel.selectParentCategoryOnly(uiState.selectedParentCategoryId!!) },
                        onClose = { viewModel.setCurrentField(TransactionField.CATEGORY) },
                        onEditCategories = onNavigateToCategories
                    )
                }
                TransactionField.AMOUNT -> {
                    Column {
                        AmountInputPanel(
                            amount = uiState.amount,
                            currency = currency,
                            onDigit = { viewModel.appendToAmount(it) },
                            onDelete = { viewModel.deleteLastDigit() },
                            onMinus = { viewModel.toggleMinus() },
                            onClose = { viewModel.setCurrentField(TransactionField.NONE) }
                        )
                        SaveButtonsPanel(
                            onSave = onSave,
                            onContinue = onContinue,
                            isEditing = isEditing,
                            typeColor = typeColor
                        )
                    }
                }
                TransactionField.NOTE -> {
                    Column {
                        NoteInputPanel(
                            note = uiState.note,
                            onNoteChange = { viewModel.updateNote(it) },
                            onClose = { viewModel.setCurrentField(TransactionField.NONE) }
                        )
                        SaveButtonsPanel(
                            onSave = onSave,
                            onContinue = onContinue,
                            isEditing = isEditing,
                            typeColor = typeColor
                        )
                    }
                }
                else -> {
                    // Show Save/Continue buttons
                    SaveButtonsPanel(
                        onSave = onSave,
                        onContinue = onContinue,
                        isEditing = isEditing,
                        typeColor = typeColor
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSelectionPanel(
    accounts: List<Account>,
    selectedAccountId: Long?,
    excludeAccountId: Long?,
    onAccountSelected: (Long) -> Unit,
    onClose: () -> Unit,
    onEditAccounts: (() -> Unit)? = null,
    title: String
) {
    val filteredAccounts = accounts.filter { it.id != excludeAccountId }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                IconButton(onClick = { onEditAccounts?.invoke() }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = 300.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredAccounts) { account ->
                AccountChip(
                    account = account,
                    isSelected = account.id == selectedAccountId,
                    onClick = { onAccountSelected(account.id) }
                )
            }
        }
    }
}

@Composable
fun AccountChip(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.surface,
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null,
        tonalElevation = if (isSelected) 0.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun CategorySelectionPanel(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long) -> Unit,
    onClose: () -> Unit,
    onEditCategories: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                IconButton(onClick = { onEditCategories?.invoke() }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories) { category ->
                CategoryListItem(
                    category = category,
                    isSelected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) },
                    showArrow = true
                )
            }
        }
    }
}

@Composable
fun SubcategorySelectionPanel(
    allCategories: List<Category>,
    selectedParentCategoryId: Long?,
    subcategories: List<Category>,
    selectedSubcategoryId: Long?,
    onCategorySelected: (Long) -> Unit,
    onSubcategorySelected: (Long) -> Unit,
    onParentSelected: () -> Unit,
    onClose: () -> Unit,
    onEditCategories: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                IconButton(onClick = { onEditCategories?.invoke() }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            // Left column - All categories (with selected parent highlighted)
            LazyColumn(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allCategories) { category ->
                    val isSelected = category.id == selectedParentCategoryId
                    CategoryListItem(
                        category = category,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                // Click on already selected category to select parent only
                                onParentSelected()
                            } else {
                                // Click on different category to switch
                                onCategorySelected(category.id)
                            }
                        },
                        showArrow = true,
                        highlightColor = if (isSelected) ExpenseRed.copy(alpha = 0.15f) else Color.Transparent
                    )
                }
            }

            // Right column - Subcategories
            LazyColumn(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subcategories) { subcategory ->
                    SubcategoryListItem(
                        subcategory = subcategory,
                        isSelected = subcategory.id == selectedSubcategoryId,
                        onClick = { onSubcategorySelected(subcategory.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryListItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    showArrow: Boolean = false,
    highlightColor: Color = Color.Transparent
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected && highlightColor != Color.Transparent) highlightColor
               else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
               else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (showArrow) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SubcategoryListItem(
    subcategory: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subcategory.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AmountInputPanel(
    amount: String,
    currency: String,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onMinus: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Numeric Keyboard
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // Row 1: 1, 2, 3, Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NumPadKey(text = "1", modifier = Modifier.weight(1f)) { onDigit("1") }
                NumPadKey(text = "2", modifier = Modifier.weight(1f)) { onDigit("2") }
                NumPadKey(text = "3", modifier = Modifier.weight(1f)) { onDigit("3") }
                NumPadKey(icon = Icons.AutoMirrored.Filled.Backspace, modifier = Modifier.weight(1f)) { onDelete() }
            }

            // Row 2: 4, 5, 6, Minus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NumPadKey(text = "4", modifier = Modifier.weight(1f)) { onDigit("4") }
                NumPadKey(text = "5", modifier = Modifier.weight(1f)) { onDigit("5") }
                NumPadKey(text = "6", modifier = Modifier.weight(1f)) { onDigit("6") }
                NumPadKey(text = "-", modifier = Modifier.weight(1f)) { onMinus() }
            }

            // Row 3: 7, 8, 9, Empty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NumPadKey(text = "7", modifier = Modifier.weight(1f)) { onDigit("7") }
                NumPadKey(text = "8", modifier = Modifier.weight(1f)) { onDigit("8") }
                NumPadKey(text = "9", modifier = Modifier.weight(1f)) { onDigit("9") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            // Row 4: Empty, 0, Decimal, Empty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface)
                )
                NumPadKey(text = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
                NumPadKey(text = ".", modifier = Modifier.weight(1f)) { onDigit(".") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
fun NumPadKey(
    text: String? = null,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        } else if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NoteInputPanel(
    note: String,
    onNoteChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Note",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            placeholder = { Text("Add a note...") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onClose() })
        )
    }
}

@Composable
fun SaveButtonsPanel(
    onSave: () -> Unit,
    onContinue: () -> Unit,
    isEditing: Boolean,
    typeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save Button
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = typeColor
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Save",
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Continue Button (only for new transactions)
            if (!isEditing) {
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.weight(0.6f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = "Continue",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Convert LocalDate to UTC millis for DatePicker (which uses UTC)
    val initialMillis = selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // Convert UTC millis back to LocalDate (DatePicker returns UTC)
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onDateSelected(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
