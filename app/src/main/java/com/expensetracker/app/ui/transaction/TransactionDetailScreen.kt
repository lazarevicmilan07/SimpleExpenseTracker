package com.expensetracker.app.ui.transaction

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.domain.model.Category
import com.expensetracker.app.domain.model.ExpenseWithCategory
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onNavigateBack: () -> Unit,
    onEditTransaction: () -> Unit,
    currency: String,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val transaction by viewModel.transaction.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionDetailEvent.TransactionDeleted -> {
                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
                is TransactionDetailEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditTransaction) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        transaction?.let { txn ->
            TransactionDetailContent(
                transaction = txn,
                currency = currency,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        transaction?.let { txn ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                title = { Text("Delete Transaction") },
                text = {
                    Text(
                        "Are you sure you want to delete this ${
                            if (txn.expense.type == TransactionType.EXPENSE) "expense" else "income"
                        } of ${formatCurrency(txn.expense.amount, currency)}?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteTransaction()
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
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
}

@Composable
fun TransactionDetailContent(
    transaction: ExpenseWithCategory,
    currency: String,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.expense.type == TransactionType.EXPENSE
    val amountColor = if (isExpense) ExpenseRed else IncomeGreen
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Amount Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = amountColor.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isExpense) "Expense" else "Income",
                    style = MaterialTheme.typography.labelLarge,
                    color = amountColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${if (isExpense) "-" else "+"}${formatCurrency(transaction.expense.amount, currency)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }
        }

        // Details Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date
                DetailRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Date",
                    value = transaction.expense.date.format(dateFormatter)
                )

                HorizontalDivider()

                // Category
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            transaction.category?.let { category ->
                                CategoryIcon(
                                    icon = category.icon,
                                    color = category.color,
                                    size = 24.dp,
                                    iconSize = 14.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = buildString {
                                        append(category.name)
                                        transaction.subcategory?.let { append(" / ${it.name}") }
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } ?: Text(
                                text = "Uncategorized",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Note (if any)
                if (transaction.expense.note.isNotBlank()) {
                    HorizontalDivider()
                    DetailRow(
                        icon = Icons.Default.Notes,
                        label = "Note",
                        value = transaction.expense.note
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun formatCurrency(amount: Double, currencyCode: String): String {
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currencyCode)
        format.format(amount)
    } catch (e: Exception) {
        "$${String.format("%.2f", amount)}"
    }
}
