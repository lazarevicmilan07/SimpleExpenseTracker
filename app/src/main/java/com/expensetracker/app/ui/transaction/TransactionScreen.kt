package com.expensetracker.app.ui.transaction

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    // Animation state for the form - re-triggers on Continue
    var formAnimKey by remember { mutableStateOf(0) }
    val formAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    val formOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(formAnimKey) {
        if (formAnimKey > 0) {
            // Slide in from the right like navigation transitions
            formAlpha.snapTo(0f)
            formOffsetX.snapTo(300f)
            launch { formAlpha.animateTo(1f, androidx.compose.animation.core.tween(300)) }
            formOffsetX.animateTo(0f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.EaseOut))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionEvent.TransactionSaved -> {
                    Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is TransactionEvent.TransactionSavedAndContinue -> {
                    Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                    formAnimKey++
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

    // Intercept system back button: close the current panel instead of leaving the screen
    BackHandler(enabled = uiState.currentField != TransactionField.NONE && uiState.currentField != TransactionField.DATE) {
        viewModel.setCurrentField(TransactionField.NONE)
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
                actions = { }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            // Main content area (scrollable form)
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = formAlpha.value
                        translationX = formOffsetX.value
                    }
                    .verticalScroll(scrollState)
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
                    allCategories = allCategories,
                    currency = currency,
                    categoryDisplayText = categoryDisplayText,
                    viewModel = viewModel,
                    onDateClick = { showDatePicker = true }
                )

                // Extra space so the Note field can be scrolled up away from buttons
                Spacer(modifier = Modifier.height(if (uiState.currentField == TransactionField.NOTE) 120.dp else 16.dp))
            }

            // Auto-scroll when Note or Amount is focused so lower fields stay visible.
            // maxValue is 0 until the bottom panel/keyboard shrinks the available
            // space, so we reactively watch for it to become scrollable.
            LaunchedEffect(uiState.currentField) {
                if (uiState.currentField == TransactionField.NOTE ||
                    uiState.currentField == TransactionField.AMOUNT) {
                    snapshotFlow { scrollState.maxValue }
                        .collect { maxValue ->
                            if (maxValue > 0) {
                                scrollState.scrollTo(maxValue)
                            }
                        }
                }
            }

            // Bottom Panel (changes based on current field)
            BottomSelectionPanel(
                uiState = uiState,
                accounts = accounts,
                rootCategories = rootCategories,
                allCategories = allCategories,
                availableSubcategories = availableSubcategories,
                currency = currency,
                viewModel = viewModel,
                onSave = { viewModel.saveTransaction() },
                onContinue = { viewModel.saveAndContinue() },
                isEditing = uiState.isEditing,
                onCopy = if (uiState.isEditing && onCopyTransaction != null) {{ showCopyDialog = true }} else null,
                onDelete = if (uiState.isEditing) {{ showDeleteDialog = true }} else null,
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
    allCategories: List<Category>,
    currency: String,
    categoryDisplayText: String,
    viewModel: TransactionViewModel,
    onDateClick: () -> Unit
) {
    val selectedAccount = accounts.find { it.id == uiState.selectedAccountId }
    val selectedToAccount = accounts.find { it.id == uiState.toAccountId }
    // Resolve the displayed category for icon (subcategory if selected, otherwise parent)
    val displayedCategory = (uiState.selectedCategoryId?.let { id -> allCategories.find { it.id == id } }
        ?: uiState.selectedParentCategoryId?.let { id -> allCategories.find { it.id == id } })
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE")

    val typeColor = when (uiState.transactionType) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferBlue
    }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Date Field
        FormFieldRow(
            label = "Date",
            value = "${uiState.selectedDate.format(dateFormatter)} (${uiState.selectedDate.format(dayOfWeekFormatter)})",
            onClick = onDateClick
        )

        HorizontalDivider(
            color = if (uiState.currentField == TransactionField.DATE) typeColor else MaterialTheme.colorScheme.outlineVariant,
            thickness = if (uiState.currentField == TransactionField.DATE) 2.dp else 1.dp
        )

        // Account / From Field
        FormFieldRow(
            label = if (uiState.transactionType == TransactionType.TRANSFER) "From" else "Account",
            value = selectedAccount?.name ?: "",
            onClick = { viewModel.setCurrentField(TransactionField.ACCOUNT) },
            iconName = selectedAccount?.icon,
            iconColor = selectedAccount?.color
        )

        HorizontalDivider(
            color = if (uiState.currentField == TransactionField.ACCOUNT) typeColor else MaterialTheme.colorScheme.outlineVariant,
            thickness = if (uiState.currentField == TransactionField.ACCOUNT) 2.dp else 1.dp
        )

        if (uiState.transactionType == TransactionType.TRANSFER) {
            // To Account Field (Transfer only)
            FormFieldRow(
                label = "To",
                value = selectedToAccount?.name ?: "",
                onClick = { viewModel.setCurrentField(TransactionField.TO_ACCOUNT) },
                iconName = selectedToAccount?.icon,
                iconColor = selectedToAccount?.color
            )
        } else {
            // Category Field (Income/Expense only)
            FormFieldRow(
                label = "Category",
                value = categoryDisplayText,
                onClick = { viewModel.setCurrentField(TransactionField.CATEGORY) },
                iconName = displayedCategory?.icon,
                iconColor = displayedCategory?.color
            )
        }

        val isCategoryFieldActive = if (uiState.transactionType == TransactionType.TRANSFER) {
            uiState.currentField == TransactionField.TO_ACCOUNT
        } else {
            uiState.currentField == TransactionField.CATEGORY || uiState.currentField == TransactionField.SUBCATEGORY
        }
        HorizontalDivider(
            color = if (isCategoryFieldActive) typeColor else MaterialTheme.colorScheme.outlineVariant,
            thickness = if (isCategoryFieldActive) 2.dp else 1.dp
        )

        // Amount Field
        FormFieldRow(
            label = "Amount",
            value = if (uiState.amount.isNotEmpty()) "${getCurrencySymbol(currency)}${uiState.amount}" else "",
            onClick = { viewModel.setCurrentField(TransactionField.AMOUNT) }
        )

        HorizontalDivider(
            color = if (uiState.currentField == TransactionField.AMOUNT) typeColor else MaterialTheme.colorScheme.outlineVariant,
            thickness = if (uiState.currentField == TransactionField.AMOUNT) 2.dp else 1.dp
        )

        // Note Field - inline editable
        val noteFocusRequester = remember { FocusRequester() }
        val isNoteActive = uiState.currentField == TransactionField.NOTE

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setCurrentField(TransactionField.NOTE) }
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Note",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (isNoteActive) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = uiState.note,
                        onValueChange = { viewModel.updateNote(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                            .focusRequester(noteFocusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.setCurrentField(TransactionField.NONE) }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (uiState.note.isEmpty()) {
                                    Text(
                                        text = "Add a note...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        textAlign = TextAlign.End
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else {
                    if (uiState.note.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = uiState.note,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = if (isNoteActive) typeColor else MaterialTheme.colorScheme.outlineVariant,
            thickness = if (isNoteActive) 2.dp else 1.dp
        )

        // Auto-focus note field when it becomes active
        LaunchedEffect(isNoteActive) {
            if (isNoteActive) {
                noteFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun FormFieldRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isDotted: Boolean = false,
    iconName: String? = null,
    iconColor: Color? = null
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconName != null && iconColor != null && value.isNotEmpty()) {
                    CategoryIcon(
                        icon = iconName,
                        color = iconColor,
                        size = 24.dp,
                        iconSize = 14.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = value.ifEmpty { if (isDotted) ". . . . . . . . . . ." else "" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun BottomSelectionPanel(
    uiState: TransactionUiState,
    accounts: List<Account>,
    rootCategories: List<Category>,
    allCategories: List<Category>,
    availableSubcategories: List<Category>,
    currency: String,
    viewModel: TransactionViewModel,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    isEditing: Boolean,
    onCopy: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onNavigateToAccounts: (() -> Unit)? = null,
    onNavigateToCategories: (() -> Unit)? = null
) {
    val typeColor = when (uiState.transactionType) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferBlue
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
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
                TransactionField.CATEGORY, TransactionField.SUBCATEGORY -> {
                    CategorySelectionPanel(
                        categories = rootCategories,
                        allCategories = allCategories,
                        selectedParentCategoryId = uiState.selectedParentCategoryId,
                        subcategories = availableSubcategories,
                        selectedSubcategoryId = uiState.selectedCategoryId,
                        onCategorySelected = { viewModel.selectParentCategory(it) },
                        onSubcategorySelected = { viewModel.selectSubcategory(it) },
                        onParentSelected = {
                            uiState.selectedParentCategoryId?.let { viewModel.selectParentCategoryOnly(it) }
                        },
                        onClose = { viewModel.setCurrentField(TransactionField.NONE) },
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
                            onDone = { viewModel.setCurrentField(TransactionField.NOTE) },
                            onClose = { viewModel.setCurrentField(TransactionField.NONE) }
                        )
                        SaveButtonsPanel(
                            onSave = onSave,
                            onContinue = onContinue,
                            isEditing = isEditing,
                            onCopy = onCopy,
                            onDelete = onDelete,
                            typeColor = typeColor
                        )
                    }
                }
                TransactionField.NOTE -> {
                    SaveButtonsPanel(
                        onSave = onSave,
                        onContinue = onContinue,
                        isEditing = isEditing,
                        onCopy = onCopy,
                        onDelete = onDelete,
                        typeColor = typeColor
                    )
                }
                else -> {
                    // Show Save/Continue buttons
                    SaveButtonsPanel(
                        onSave = onSave,
                        onContinue = onContinue,
                        isEditing = isEditing,
                        onCopy = onCopy,
                        onDelete = onDelete,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CategoryIcon(
                icon = account.icon,
                color = account.color,
                size = 32.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategorySelectionPanel(
    categories: List<Category>,
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
            // Left column - Categories (always half width)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category.id == selectedParentCategoryId
                    CategoryListItem(
                        category = category,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onParentSelected()
                            } else {
                                onCategorySelected(category.id)
                            }
                        },
                        showArrow = allCategories.any { it.parentCategoryId == category.id },
                        highlightColor = if (isSelected) ExpenseRed.copy(alpha = 0.15f) else Color.Transparent
                    )
                }
            }

            // Right column - Subcategories (always half width)
            LazyColumn(
                modifier = Modifier.weight(1f),
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(
                icon = category.icon,
                color = category.color,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(
                icon = subcategory.icon,
                color = subcategory.color,
                size = 28.dp,
                iconSize = 16.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
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
    onDone: () -> Unit,
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

            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NumPadKey(text = "4", modifier = Modifier.weight(1f)) { onDigit("4") }
                NumPadKey(text = "5", modifier = Modifier.weight(1f)) { onDigit("5") }
                NumPadKey(text = "6", modifier = Modifier.weight(1f)) { onDigit("6") }
                Box(modifier = Modifier.weight(1f).height(56.dp).background(MaterialTheme.colorScheme.surface))
            }

            // Row 3: 7, 8, 9, Decimal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                NumPadKey(text = "7", modifier = Modifier.weight(1f)) { onDigit("7") }
                NumPadKey(text = "8", modifier = Modifier.weight(1f)) { onDigit("8") }
                NumPadKey(text = "9", modifier = Modifier.weight(1f)) { onDigit("9") }
                NumPadKey(text = ".", modifier = Modifier.weight(1f)) { onDigit(".") }
            }

            // Row 4: Empty, 0, Empty, Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(modifier = Modifier.weight(1f).height(56.dp).background(MaterialTheme.colorScheme.surface))
                NumPadKey(text = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
                Box(modifier = Modifier.weight(1f).height(56.dp).background(MaterialTheme.colorScheme.surface))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(IncomeGreen.copy(alpha = 0.3f))
                        .clickable(onClick = onDone),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                }
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
    onCopy: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    typeColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Copy Button (only when editing)
            if (onCopy != null) {
                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Delete Button (only when editing)
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Delete",
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Save Button
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = IncomeGreen
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, IncomeGreen.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Save",
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            // Continue Button (only for new transactions)
            if (!isEditing) {
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
