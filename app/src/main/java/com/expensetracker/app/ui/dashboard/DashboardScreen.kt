package com.expensetracker.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.local.entity.TransactionType
import com.expensetracker.app.domain.model.CategoryBreakdown
import com.expensetracker.app.domain.model.ExpenseWithCategory
import com.expensetracker.app.ui.components.CategoryIcon
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    // State for delete confirmation dialog
    var transactionToDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") },
                actions = {
                    IconButton(onClick = onNavigateToAccounts) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Accounts")
                    }
                    IconButton(onClick = onNavigateToCategories) {
                        Icon(Icons.Default.Category, contentDescription = "Categories")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Swipe gesture for month navigation
            val swipeThreshold = 100f
            var totalDragAmount by remember { mutableFloatStateOf(0f) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDragAmount > swipeThreshold) {
                                    viewModel.previousMonth()
                                } else if (totalDragAmount < -swipeThreshold) {
                                    if (selectedMonth < YearMonth.now()) {
                                        viewModel.nextMonth()
                                    }
                                }
                                totalDragAmount = 0f
                            },
                            onDragCancel = {
                                totalDragAmount = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDragAmount += dragAmount
                            }
                        )
                    },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Month Selector
                item {
                    MonthSelector(
                        selectedMonth = selectedMonth,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth
                    )
                }

                // Animated content for month transitions
                item {
                    var previousMonth by remember { mutableStateOf(selectedMonth) }
                    val isForward = selectedMonth > previousMonth

                    LaunchedEffect(selectedMonth) {
                        previousMonth = selectedMonth
                    }

                    AnimatedContent(
                        targetState = Pair(selectedMonth, uiState),
                        transitionSpec = {
                            if (isForward) {
                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                        (slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                        (slideOutHorizontally { width -> width } + fadeOut())
                            }
                        },
                        label = "month_transition"
                    ) { (_, state) ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Summary Card
                            SummaryCard(
                                income = state.monthlyStats.totalIncome,
                                expense = state.monthlyStats.totalExpense,
                                balance = state.monthlyStats.balance,
                                currency = currency
                            )

                            // Recent Transactions
                            if (state.recentTransactions.isNotEmpty()) {
                                Text(
                                    text = "Recent Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                state.recentTransactions.forEach { transaction ->
                                    TransactionItemWithActions(
                                        transaction = transaction,
                                        currency = currency,
                                        onEdit = { onEditTransaction(transaction.expense.id) },
                                        onDelete = { transactionToDelete = transaction }
                                    )
                                }
                            } else {
                                EmptyState()
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Transaction") },
            text = {
                Text(
                    "Are you sure you want to delete this ${
                        if (transaction.expense.type == TransactionType.EXPENSE) "expense" else "income"
                    } of ${formatCurrency(transaction.expense.amount, currency)}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(transaction.expense.id)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }

        Text(
            text = selectedMonth.format(formatter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        IconButton(
            onClick = onNextMonth,
            enabled = selectedMonth < YearMonth.now()
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
fun SummaryCard(
    income: Double,
    expense: Double,
    balance: Double,
    currency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = formatCurrency(balance, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IncomeGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = formatCurrency(income, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ExpenseRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = formatCurrency(expense, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseBreakdownCard(
    breakdown: List<CategoryBreakdown>,
    currency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Expense Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Simple Pie Chart
            SimplePieChart(
                breakdown = breakdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            breakdown.take(5).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(item.category?.color ?: Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.category?.name ?: "Uncategorized",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.percentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatCurrency(item.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SimplePieChart(
    breakdown: List<CategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val total = breakdown.sumOf { it.amount }.toFloat()

    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val radius = canvasSize / 2f * 0.8f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        var startAngle = -90f

        breakdown.forEach { item ->
            val sweepAngle = if (total > 0f) (item.amount.toFloat() / total * 360f) else 0f
            drawArc(
                color = item.category?.color ?: Color.Gray,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun TransactionItemWithActions(
    transaction: ExpenseWithCategory,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = transaction.expense.type == TransactionType.EXPENSE
    val amountColor by animateColorAsState(
        targetValue = if (isExpense) ExpenseRed else IncomeGreen,
        label = "amount_color"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(
                icon = transaction.category?.icon ?: "more_horiz",
                color = transaction.category?.color ?: Color.Gray
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (transaction.expense.note.isNotBlank()) {
                    Text(
                        text = transaction.expense.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = transaction.expense.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Text(
                text = "${if (isExpense) "-" else "+"}${formatCurrency(transaction.expense.amount, currency)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No transactions yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = "Tap + to add your first transaction",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

fun formatCurrency(amount: Double, currencyCode: String): String {
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currencyCode)
        format.format(amount)
    } catch (e: Exception) {
        "$${String.format("%.2f", amount)}"
    }
}
