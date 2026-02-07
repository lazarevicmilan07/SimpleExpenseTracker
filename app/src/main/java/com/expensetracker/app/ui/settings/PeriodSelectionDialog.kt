package com.expensetracker.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.format.TextStyle
import java.util.*

enum class PeriodType {
    MONTH, YEAR
}

data class ExportPeriod(
    val type: PeriodType,
    val year: Int,
    val month: Int? = null // Only used when type is MONTH
) {
    fun getFileName(prefix: String, extension: String): String {
        return when (type) {
            PeriodType.MONTH -> {
                val monthName = Month.of(month!!).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                "${prefix}_${monthName}_${year}.${extension}"
            }
            PeriodType.YEAR -> "${prefix}_${year}.${extension}"
        }
    }

    fun getDisplayName(): String {
        return when (type) {
            PeriodType.MONTH -> {
                val monthName = Month.of(month!!).getDisplayName(TextStyle.FULL, Locale.getDefault())
                "$monthName $year"
            }
            PeriodType.YEAR -> year.toString()
        }
    }
}

@Composable
fun PeriodSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    onPeriodSelected: (ExportPeriod) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var selectedType by remember { mutableStateOf<PeriodType?>(null) }
    var selectedYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var selectedMonth by remember { mutableIntStateOf(LocalDate.now().monthValue) }

    val currentYear = LocalDate.now().year

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    IconButton(
                        onClick = { step = 1 },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when (step) {
                        1 -> title
                        2 -> if (selectedType == PeriodType.MONTH) "Select Month and Year" else "Select Year"
                        else -> title
                    },
                    style = if (step == 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            when (step) {
                1 -> {
                    // Step 1: Choose period type
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Select period for export:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PeriodTypeOption(
                            icon = Icons.Default.CalendarToday,
                            title = "Monthly",
                            description = "Export data for a specific month",
                            onClick = {
                                selectedType = PeriodType.MONTH
                                step = 2
                            }
                        )

                        PeriodTypeOption(
                            icon = Icons.Default.CalendarMonth,
                            title = "Yearly",
                            description = "Export data for an entire year",
                            onClick = {
                                selectedType = PeriodType.YEAR
                                step = 2
                            }
                        )
                    }
                }

                2 -> {
                    if (selectedType == PeriodType.MONTH) {
                        // Month & Year picker grid
                        MonthYearPickerContent(
                            selectedYear = selectedYear,
                            selectedMonth = selectedMonth,
                            onYearChange = { selectedYear = it },
                            onMonthChange = { selectedMonth = it }
                        )
                    } else {
                        // Year picker grid
                        YearPickerContent(
                            selectedYear = selectedYear,
                            currentYear = currentYear,
                            onYearChange = { selectedYear = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step == 2) {
                TextButton(
                    onClick = {
                        if (selectedType == PeriodType.MONTH) {
                            onPeriodSelected(ExportPeriod(PeriodType.MONTH, selectedYear, selectedMonth))
                        } else {
                            onPeriodSelected(ExportPeriod(PeriodType.YEAR, selectedYear))
                        }
                    }
                ) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MonthYearPickerContent(
    selectedYear: Int,
    selectedMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Column {
        // Year selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onYearChange(selectedYear - 1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
            }
            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = { onYearChange(selectedYear + 1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Month grid
        val months = listOf(
            "Jan", "Feb", "Mar", "Apr",
            "May", "Jun", "Jul", "Aug",
            "Sep", "Oct", "Nov", "Dec"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0..3) {
                        val monthIndex = row * 4 + col + 1
                        val isSelected = monthIndex == selectedMonth
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onMonthChange(monthIndex) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = months[monthIndex - 1],
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearPickerContent(
    selectedYear: Int,
    currentYear: Int,
    onYearChange: (Int) -> Unit
) {
    // Year range: current year - 10 to current year + 5
    val years = (currentYear - 10)..(currentYear + 5)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        years.chunked(4).forEach { rowYears ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowYears.forEach { year ->
                    val isSelected = year == selectedYear
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onYearChange(year) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = year.toString(),
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                // Fill remaining space if row has fewer than 4 items
                repeat(4 - rowYears.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PeriodTypeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
