package com.expensetracker.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.app.data.preferences.PreferencesManager
import com.expensetracker.app.domain.usecase.ExportPeriodParams
import com.expensetracker.app.ui.components.AdBanner

enum class PendingExportAction {
    EXCEL, PDF, BACKUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onShowPremium: () -> Unit,
    preferencesManager: PreferencesManager,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userPreferences by viewModel.userPreferences.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Track which action is pending and the selected period
    var pendingAction by remember { mutableStateOf<PendingExportAction?>(null) }
    var selectedPeriod by remember { mutableStateOf<ExportPeriod?>(null) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var periodDialogTitle by remember { mutableStateOf("") }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val excelExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            selectedPeriod?.let { period ->
                viewModel.exportToExcel(
                    context,
                    it,
                    ExportPeriodParams(period.year, period.month)
                )
            }
        }
        selectedPeriod = null
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            selectedPeriod?.let { period ->
                viewModel.exportToPdf(
                    context,
                    it,
                    ExportPeriodParams(period.year, period.month)
                )
            }
        }
        selectedPeriod = null
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            selectedPeriod?.let { period ->
                viewModel.backup(
                    context,
                    it,
                    ExportPeriodParams(period.year, period.month)
                )
            }
        }
        selectedPeriod = null
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restore(context, it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ExportSuccess -> {
                    Toast.makeText(context, "${event.type} exported successfully", Toast.LENGTH_SHORT).show()
                }
                is SettingsEvent.BackupSuccess -> {
                    Toast.makeText(context, "Backup created successfully", Toast.LENGTH_SHORT).show()
                }
                is SettingsEvent.RestoreSuccess -> {
                    Toast.makeText(context, "Data restored successfully", Toast.LENGTH_SHORT).show()
                }
                is SettingsEvent.ShowPremiumRequired -> {
                    onShowPremium()
                }
                is SettingsEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Handle period selection result
    LaunchedEffect(selectedPeriod, pendingAction) {
        if (selectedPeriod != null && pendingAction != null) {
            when (pendingAction) {
                PendingExportAction.EXCEL -> {
                    excelExportLauncher.launch(selectedPeriod!!.getFileName("expenses", "xlsx"))
                }
                PendingExportAction.PDF -> {
                    pdfExportLauncher.launch(selectedPeriod!!.getFileName("expense_report", "pdf"))
                }
                PendingExportAction.BACKUP -> {
                    backupLauncher.launch(selectedPeriod!!.getFileName("backup", "json"))
                }
                null -> {}
            }
            pendingAction = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Premium Banner
                if (!userPreferences.isPremium) {
                    item {
                        PremiumBanner(onClick = onShowPremium)
                    }
                }

                // Appearance Section
                item {
                    SettingsSectionHeader("Appearance")
                }

                item {
                    SettingsSwitch(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = userPreferences.isDarkMode,
                        onCheckedChange = viewModel::setDarkMode
                    )
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.AttachMoney,
                        title = "Currency",
                        subtitle = userPreferences.currency,
                        onClick = { viewModel.showCurrencyPicker() }
                    )
                }

                // Data Section
                item {
                    SettingsSectionHeader("Data")
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.TableChart,
                        title = "Export to Excel",
                        subtitle = if (userPreferences.isPremium) "Export all transactions" else "Premium feature",
                        onClick = {
                            pendingAction = PendingExportAction.EXCEL
                            periodDialogTitle = "Export to Excel"
                            showPeriodDialog = true
                        },
                        isPremium = !userPreferences.isPremium
                    )
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.PictureAsPdf,
                        title = "Export to PDF",
                        subtitle = if (userPreferences.isPremium) "Generate expense report" else "Premium feature",
                        onClick = {
                            pendingAction = PendingExportAction.PDF
                            periodDialogTitle = "Export to PDF"
                            showPeriodDialog = true
                        },
                        isPremium = !userPreferences.isPremium
                    )
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.Backup,
                        title = "Backup",
                        subtitle = if (userPreferences.isPremium) "Create data backup" else "Premium feature",
                        onClick = {
                            pendingAction = PendingExportAction.BACKUP
                            periodDialogTitle = "Backup Data"
                            showPeriodDialog = true
                        },
                        isPremium = !userPreferences.isPremium
                    )
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.Restore,
                        title = "Restore",
                        subtitle = if (userPreferences.isPremium) "Restore from backup" else "Premium feature",
                        onClick = { showRestoreConfirmDialog = true },
                        isPremium = !userPreferences.isPremium
                    )
                }

                // About Section
                item {
                    SettingsSectionHeader("About")
                }

                item {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0",
                        onClick = {}
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Ad banner at the bottom, sitting on top of the nav bar
            AdBanner(
                preferencesManager = preferencesManager,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
            )
        }
    }

    // Period Selection Dialog
    if (showPeriodDialog) {
        PeriodSelectionDialog(
            title = periodDialogTitle,
            onDismiss = {
                showPeriodDialog = false
                pendingAction = null
            },
            onPeriodSelected = { period ->
                showPeriodDialog = false
                selectedPeriod = period
            }
        )
    }

    // Currency Picker Dialog
    if (uiState.showCurrencyPicker) {
        CurrencyPickerDialog(
            currentCurrency = userPreferences.currency,
            onCurrencySelected = { currency ->
                viewModel.setCurrency(currency)
                viewModel.hideCurrencyPicker()
            },
            onDismiss = { viewModel.hideCurrencyPicker() }
        )
    }

    // Restore Confirmation Dialog
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Restore Data") },
            text = {
                Text(
                    "This will permanently delete all your current data including transactions, categories, subcategories, and accounts, and replace it with the data from the backup file.\n\nThis action cannot be undone. Are you sure you want to continue?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        restoreLauncher.launch(arrayOf("application/json"))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Loading Overlay
    if (uiState.isExporting || uiState.isBackingUp || uiState.isRestoring) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = when {
                            uiState.isExporting -> "Exporting..."
                            uiState.isBackingUp -> "Creating backup..."
                            else -> "Restoring..."
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upgrade to Premium",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Unlock all features with a one-time purchase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isPremium: Boolean = false
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            if (isPremium) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Premium",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun CurrencyPickerDialog(
    currentCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currencies = listOf(
        // Major
        "USD" to "US Dollar",
        "EUR" to "Euro",
        "GBP" to "British Pound",
        "JPY" to "Japanese Yen",
        "CHF" to "Swiss Franc",
        // Americas
        "CAD" to "Canadian Dollar",
        "MXN" to "Mexican Peso",
        "BRL" to "Brazilian Real",
        "ARS" to "Argentine Peso",
        "CLP" to "Chilean Peso",
        "COP" to "Colombian Peso",
        "PEN" to "Peruvian Sol",
        "UYU" to "Uruguayan Peso",
        // Asia-Pacific
        "CNY" to "Chinese Yuan",
        "INR" to "Indian Rupee",
        "KRW" to "South Korean Won",
        "AUD" to "Australian Dollar",
        "NZD" to "New Zealand Dollar",
        "SGD" to "Singapore Dollar",
        "HKD" to "Hong Kong Dollar",
        "TWD" to "New Taiwan Dollar",
        "THB" to "Thai Baht",
        "MYR" to "Malaysian Ringgit",
        "IDR" to "Indonesian Rupiah",
        "PHP" to "Philippine Peso",
        "VND" to "Vietnamese Dong",
        "PKR" to "Pakistani Rupee",
        "BDT" to "Bangladeshi Taka",
        "LKR" to "Sri Lankan Rupee",
        // Europe (non-Euro)
        "SEK" to "Swedish Krona",
        "NOK" to "Norwegian Krone",
        "DKK" to "Danish Krone",
        "PLN" to "Polish Zloty",
        "CZK" to "Czech Koruna",
        "HUF" to "Hungarian Forint",
        "RON" to "Romanian Leu",
        "BGN" to "Bulgarian Lev",
        "HRK" to "Croatian Kuna",
        "RSD" to "Serbian Dinar",
        "UAH" to "Ukrainian Hryvnia",
        "ISK" to "Icelandic Krona",
        "TRY" to "Turkish Lira",
        "RUB" to "Russian Ruble",
        "GEL" to "Georgian Lari",
        // Middle East & Africa
        "ILS" to "Israeli Shekel",
        "AED" to "UAE Dirham",
        "SAR" to "Saudi Riyal",
        "QAR" to "Qatari Riyal",
        "KWD" to "Kuwaiti Dinar",
        "BHD" to "Bahraini Dinar",
        "OMR" to "Omani Rial",
        "JOD" to "Jordanian Dinar",
        "EGP" to "Egyptian Pound",
        "MAD" to "Moroccan Dirham",
        "ZAR" to "South African Rand",
        "NGN" to "Nigerian Naira",
        "KES" to "Kenyan Shilling",
        "GHS" to "Ghanaian Cedi",
        "TZS" to "Tanzanian Shilling"
    )

    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        if (searchQuery.isBlank()) currencies
        else currencies.filter { (code, name) ->
            code.contains(searchQuery, ignoreCase = true) ||
                name.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search currency...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                items(filteredCurrencies) { (code, name) ->
                    ListItem(
                        headlineContent = { Text(code) },
                        supportingContent = { Text(name) },
                        trailingContent = {
                            if (code == currentCurrency) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable { onCurrencySelected(code) }
                    )
                }
            }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
