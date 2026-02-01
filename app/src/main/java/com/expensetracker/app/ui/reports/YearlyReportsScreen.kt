package com.expensetracker.app.ui.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.ui.components.MonthlyBarChart
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.Month
import java.time.Year
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

@Composable
fun YearlyReportsScreen(
    currency: String,
    viewModel: YearlyReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()

    val swipeThreshold = 100f
    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedYear) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val currentOffset = dragOffset.value
                            if (currentOffset > swipeThreshold) {
                                dragOffset.animateTo(size.width.toFloat(), tween(150))
                                viewModel.previousYear()
                                dragOffset.snapTo(-size.width.toFloat())
                                dragOffset.animateTo(0f, tween(200))
                            } else if (currentOffset < -swipeThreshold) {
                                dragOffset.animateTo(-size.width.toFloat(), tween(150))
                                viewModel.nextYear()
                                dragOffset.snapTo(size.width.toFloat())
                                dragOffset.animateTo(0f, tween(200))
                            } else {
                                dragOffset.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffset.animateTo(0f, tween(200)) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            dragOffset.snapTo(dragOffset.value + dragAmount)
                        }
                    }
                )
            },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year Selector
        item {
            YearSelector(
                selectedYear = selectedYear,
                onPreviousYear = viewModel::previousYear,
                onNextYear = viewModel::nextYear
            )
        }

        // Content with interactive drag offset
        item {
            Column(
                modifier = Modifier.offset { IntOffset(dragOffset.value.roundToInt(), 0) },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Card
                YearlySummaryCard(
                    income = uiState.totalIncome,
                    expense = uiState.totalExpense,
                    balance = uiState.balance,
                    currency = currency
                )

                // Bar Chart - Monthly Overview
                if (uiState.monthlyData.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Monthly Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            MonthlyBarChart(
                                monthlyData = uiState.monthlyData,
                                currency = currency,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Expense Pie Chart
                if (uiState.expenseBreakdown.isNotEmpty()) {
                    BreakdownCard(
                        title = "Yearly Expenses by Category",
                        breakdown = uiState.expenseBreakdown,
                        currency = currency,
                        color = ExpenseRed
                    )
                }

                // Income Pie Chart
                if (uiState.incomeBreakdown.isNotEmpty()) {
                    BreakdownCard(
                        title = "Yearly Income by Category",
                        breakdown = uiState.incomeBreakdown,
                        currency = currency,
                        color = IncomeGreen
                    )
                }

                // Monthly Breakdown List
                if (uiState.monthlyData.any { it.income > 0 || it.expense > 0 }) {
                    MonthlyBreakdownCard(
                        monthlyData = uiState.monthlyData,
                        currency = currency
                    )
                }

                // Empty state
                if (uiState.monthlyData.all { it.income == 0.0 && it.expense == 0.0 } && !uiState.isLoading) {
                    EmptyYearlyReportsState()
                }
            }
        }
    }
}

@Composable
fun YearSelector(
    selectedYear: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousYear) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
        }

        Text(
            text = selectedYear.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        IconButton(
            onClick = onNextYear,
            enabled = true
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
        }
    }
}

@Composable
fun YearlySummaryCard(
    income: Double,
    expense: Double,
    balance: Double,
    currency: String
) {
    val balanceColor = when {
        balance > 0 -> IncomeGreen
        balance < 0 -> ExpenseRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Yearly Balance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCurrency(balance, currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = balanceColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = IncomeGreen.copy(alpha = 0.1f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = IncomeGreen.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCurrency(income, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = IncomeGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = ExpenseRed.copy(alpha = 0.1f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ExpenseRed.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Expenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatCurrency(expense, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyBreakdownCard(
    monthlyData: List<MonthData>,
    currency: String
) {
    val maxAmount = monthlyData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(14.dp))

            monthlyData.forEachIndexed { index, data ->
                if (data.income > 0 || data.expense > 0) {
                    val monthName = Month.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val balance = data.income - data.expense
                    val incomeFraction = if (maxAmount > 0) (data.income / maxAmount).toFloat() else 0f
                    val expenseFraction = if (maxAmount > 0) (data.expense / maxAmount).toFloat() else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // Month name and balance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatCurrency(balance, currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (balance >= 0) IncomeGreen else ExpenseRed
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Income row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(incomeFraction.coerceAtLeast(0.02f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(IncomeGreen)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = formatCurrency(data.income, currency),
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeGreen,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Expense row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(expenseFraction.coerceAtLeast(0.02f))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ExpenseRed)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = formatCurrency(data.expense, currency),
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseRed,
                                modifier = Modifier.width(80.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    if (index < monthlyData.lastIndex && (monthlyData[index + 1].income > 0 || monthlyData[index + 1].expense > 0)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyYearlyReportsState() {
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
                text = "No data for this year",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add transactions to see yearly reports",
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
