package com.expensetracker.app.ui.transaction

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.domain.model.Account
import com.expensetracker.app.domain.model.AccountTypeNames
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val rootCategories by viewModel.rootCategories.collectAsState()
    val availableSubcategories by viewModel.availableSubcategories.collectAsState()
    val allCategories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionEvent.TransactionSaved -> {
                    Toast.makeText(context, "Transaction saved", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is TransactionEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditing) "Edit Transaction" else "Add Transaction")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveTransaction() },
                        enabled = uiState.amount.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Transaction Type Toggle
            TransactionTypeToggle(
                selectedType = uiState.transactionType,
                onTypeSelected = viewModel::selectTransactionType
            )

            // Amount Input
            AmountInput(
                amount = uiState.amount,
                onAmountChange = viewModel::updateAmount,
                transactionType = uiState.transactionType
            )

            // Date Selector
            DateSelector(
                selectedDate = uiState.selectedDate,
                onClick = { showDatePicker = true }
            )

            // Note Input
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::updateNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Notes, contentDescription = null)
                }
            )

            // Account Selector
            AccountSelector(
                accounts = accounts,
                selectedAccountId = uiState.selectedAccountId,
                onAccountSelected = viewModel::selectAccount
            )

            // Category Selector
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            // Show selected category if any
            uiState.selectedCategoryId?.let { categoryId ->
                val selectedCategory = allCategories.find { it.id == categoryId }
                selectedCategory?.let { category ->
                    SelectedCategoryChip(
                        category = category,
                        parentCategory = category.parentCategoryId?.let { parentId ->
                            allCategories.find { it.id == parentId }
                        },
                        onClear = {
                            viewModel.selectCategory(null)
                            viewModel.clearSubcategorySelection()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (uiState.showSubcategorySelector && availableSubcategories.isNotEmpty()) {
                // Show subcategory selector
                Text(
                    text = "Select Subcategory",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoryGrid(
                    categories = availableSubcategories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelected = { viewModel.selectSubcategory(it!!) }
                )
            } else if (uiState.selectedCategoryId == null) {
                // Show root categories
                CategoryGrid(
                    categories = rootCategories,
                    selectedCategoryId = null,
                    onCategorySelected = { categoryId ->
                        categoryId?.let { viewModel.selectParentCategory(it) }
                    }
                )
            }
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
                    if (type == TransactionType.EXPENSE) ExpenseRed else IncomeGreen
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
fun AmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    transactionType: TransactionType
) {
    val textColor by animateColorAsState(
        targetValue = if (transactionType == TransactionType.EXPENSE) ExpenseRed else IncomeGreen,
        label = "amount_color"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (transactionType == TransactionType.EXPENSE) "-$" else "+$",
                style = MaterialTheme.typography.headlineLarge,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.width(200.dp),
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "0.00",
                        style = MaterialTheme.typography.headlineLarge,
                        color = textColor.copy(alpha = 0.3f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelector(
    selectedDate: LocalDate,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = selectedDate.format(formatter),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun SelectedCategoryChip(
    category: Category,
    parentCategory: Category?,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = category.color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(
                icon = category.icon,
                color = category.color,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (parentCategory != null) {
                    Text(
                        text = parentCategory.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear selection",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryGrid(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(200.dp)
    ) {
        items(categories) { category ->
            CategoryItem(
                category = category,
                isSelected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) }
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIcon(
            icon = category.icon,
            color = category.color,
            size = 44.dp,
            iconSize = 22.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelector(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.find { it.id == selectedAccountId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedAccount?.name ?: "Select Account",
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
            leadingIcon = {
                if (selectedAccount != null) {
                    CategoryIcon(
                        icon = selectedAccount.icon,
                        color = selectedAccount.color,
                        size = 24.dp,
                        iconSize = 14.dp
                    )
                } else {
                    Icon(Icons.Default.AccountBalance, contentDescription = null)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(
                                icon = account.icon,
                                color = account.color,
                                size = 32.dp,
                                iconSize = 16.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(account.name)
                                Text(
                                    text = AccountTypeNames[account.type] ?: account.type.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    onClick = {
                        onAccountSelected(account.id)
                        expanded = false
                    },
                    leadingIcon = null
                )
            }
        }
    }
}
