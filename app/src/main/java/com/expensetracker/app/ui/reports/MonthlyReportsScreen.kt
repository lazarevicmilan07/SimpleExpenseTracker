package com.expensetracker.app.ui.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.domain.model.CategoryBreakdown
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable
fun MonthlyReportsScreen(
    currency: String,
    viewModel: MonthlyReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    val swipeThreshold = 100f
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    var previousMonth by remember { mutableStateOf(selectedMonth) }
    val isForward = selectedMonth > previousMonth

    LaunchedEffect(selectedMonth) {
        previousMonth = selectedMonth
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                    onDragCancel = { totalDragAmount = 0f },
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

        // Animated content
        item {
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
                label = "monthly_reports_transition"
            ) { (_, state) ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary Card
                    ReportsSummaryCard(
                        income = state.totalIncome,
                        expense = state.totalExpense,
                        balance = state.balance,
                        currency = currency
                    )

                    // Expense Pie Chart
                    if (state.expenseBreakdown.isNotEmpty()) {
                        BreakdownCard(
                            title = "Expenses by Category",
                            breakdown = state.expenseBreakdown,
                            currency = currency,
                            color = ExpenseRed
                        )
                    }

                    // Income Pie Chart
                    if (state.incomeBreakdown.isNotEmpty()) {
                        BreakdownCard(
                            title = "Income by Category",
                            breakdown = state.incomeBreakdown,
                            currency = currency,
                            color = IncomeGreen
                        )
                    }

                    // Empty state
                    if (state.expenseBreakdown.isEmpty() && state.incomeBreakdown.isEmpty() && !state.isLoading) {
                        EmptyReportsState()
                    }
                }
            }
        }
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
fun ReportsSummaryCard(
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
fun BreakdownCard(
    title: String,
    breakdown: List<CategoryBreakdown>,
    currency: String,
    color: Color
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pie Chart
            PieChart(
                breakdown = breakdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            breakdown.forEach { item ->
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
fun PieChart(
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
fun EmptyReportsState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No data for this month",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add transactions to see reports",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
