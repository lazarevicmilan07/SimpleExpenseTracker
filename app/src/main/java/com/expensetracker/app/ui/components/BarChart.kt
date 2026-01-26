package com.expensetracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.ui.reports.MonthData
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen

@Composable
fun MonthlyBarChart(
    monthlyData: List<MonthData>,
    modifier: Modifier = Modifier
) {
    val maxValue = monthlyData.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
    val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 24.dp.toPx() // Leave space for labels
            val barGroupWidth = chartWidth / 12
            val barWidth = barGroupWidth * 0.35f
            val spacing = barGroupWidth * 0.1f

            monthlyData.forEachIndexed { index, data ->
                val groupX = index * barGroupWidth

                // Income bar (green)
                val incomeHeight = if (maxValue > 0) (data.income / maxValue * chartHeight).toFloat() else 0f
                drawRect(
                    color = IncomeGreen,
                    topLeft = Offset(
                        x = groupX + spacing,
                        y = chartHeight - incomeHeight
                    ),
                    size = Size(barWidth, incomeHeight)
                )

                // Expense bar (red)
                val expenseHeight = if (maxValue > 0) (data.expense / maxValue * chartHeight).toFloat() else 0f
                drawRect(
                    color = ExpenseRed,
                    topLeft = Offset(
                        x = groupX + spacing + barWidth + 2.dp.toPx(),
                        y = chartHeight - expenseHeight
                    ),
                    size = Size(barWidth, expenseHeight)
                )
            }

            // Draw baseline
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Month labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            months.forEach { month ->
                Text(
                    text = month,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Legend
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = IncomeGreen)
                }
            }
            Text(
                text = "Income",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.width(24.dp))

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = ExpenseRed)
                }
            }
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
