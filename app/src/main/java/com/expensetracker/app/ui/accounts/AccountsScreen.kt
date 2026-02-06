package com.expensetracker.app.ui.accounts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.local.entity.AccountType
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.domain.model.Account
import com.expensetracker.app.domain.model.AccountTypeNames
import com.expensetracker.app.domain.model.AccountWithBalance
import com.expensetracker.app.ui.components.AvailableColors
import com.expensetracker.app.ui.components.formatCurrency
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import com.expensetracker.app.ui.components.AdBanner
import com.expensetracker.app.ui.components.AvailableIcons
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.components.getIconForName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateBack: () -> Unit,
    currency: String,
    preferencesManager: PreferencesManager,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val accountsState by viewModel.accountsState.collectAsState()
    val context = LocalContext.current

    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountEvent.AccountSaved -> {
                    Toast.makeText(context, "Account saved", Toast.LENGTH_SHORT).show()
                }
                is AccountEvent.AccountDeleted -> {
                    Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                }
                is AccountEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account")
                    }
                }
            )
        },
        bottomBar = {
            AdBanner(preferencesManager = preferencesManager)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Total Balance Card
            item {
                TotalBalanceCard(
                    totalBalance = accountsState.totalBalance,
                    currency = currency
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(
                items = accountsState.accounts,
                key = { it.account.id }
            ) { accountWithBalance ->
                AccountListItem(
                    accountWithBalance = accountWithBalance,
                    currency = currency,
                    onClick = { viewModel.showEditDialog(accountWithBalance.account) },
                    onToggleDefault = { viewModel.toggleDefault(accountWithBalance.account) }
                )
            }
        }
    }

    // Add/Edit Dialog
    if (uiState.showDialog) {
        AccountDialog(
            isEditing = uiState.editingAccount != null,
            name = uiState.dialogName,
            type = uiState.dialogType,
            icon = uiState.dialogIcon,
            color = uiState.dialogColor,
            initialBalance = uiState.dialogInitialBalance,
            onNameChange = viewModel::updateDialogName,
            onTypeChange = viewModel::updateDialogType,
            onIconChange = viewModel::updateDialogIcon,
            onColorChange = viewModel::updateDialogColor,
            onInitialBalanceChange = viewModel::updateDialogInitialBalance,
            onSave = viewModel::saveAccount,
            onDelete = if (uiState.editingAccount != null) {
                { accountToDelete = uiState.editingAccount }
            } else null,
            onDismiss = viewModel::hideDialog
        )
    }

    // Delete Confirmation Dialog
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Account") },
            text = {
                Text("Are you sure you want to delete \"${account.name}\"? All transactions in this account will become unassigned.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account)
                        accountToDelete = null
                        viewModel.hideDialog()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TotalBalanceCard(
    totalBalance: Double,
    currency: String
) {
    val balanceColor = when {
        totalBalance > 0 -> IncomeGreen
        totalBalance < 0 -> ExpenseRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Balance hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCurrency(totalBalance, currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = balanceColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Wallet accent pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "All Accounts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountListItem(
    accountWithBalance: AccountWithBalance,
    currency: String,
    onClick: () -> Unit,
    onToggleDefault: () -> Unit = {}
) {
    val account = accountWithBalance.account
    val currentBalance = accountWithBalance.currentBalance

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(
                icon = account.icon,
                color = account.color,
                size = 30.dp,
                iconSize = 15.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (account.isDefault) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = AccountTypeNames[account.type] ?: account.type.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            Text(
                text = formatCurrency(currentBalance, currency),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (currentBalance >= 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            IconButton(
                onClick = onToggleDefault,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (account.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (account.isDefault) "Remove default" else "Set as default",
                    tint = if (account.isDefault) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDialog(
    isEditing: Boolean,
    name: String,
    type: AccountType,
    icon: String,
    color: Color,
    initialBalance: String,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    onInitialBalanceChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Account" else "New Account")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CategoryIcon(
                        icon = icon,
                        color = color,
                        size = 64.dp,
                        iconSize = 32.dp
                    )
                }

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Account Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Account Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = AccountTypeNames[type] ?: type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        AccountType.entries.forEach { accountType ->
                            DropdownMenuItem(
                                text = { Text(AccountTypeNames[accountType] ?: accountType.name) },
                                onClick = {
                                    onTypeChange(accountType)
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Initial Balance
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                            onInitialBalanceChange(value)
                        }
                    },
                    label = { Text("Initial Balance") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("0.00") }
                )

                // Icon Selector
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvailableIcons) { iconName ->
                        val isSelected = iconName == icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) color.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp,
                                        color,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { onIconChange(iconName) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconForName(iconName),
                                contentDescription = null,
                                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Color Selector
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvailableColors) { colorOption ->
                        val isSelected = colorOption == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorOption)
                                .then(
                                    if (isSelected) Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                                .clickable { onColorChange(colorOption) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

