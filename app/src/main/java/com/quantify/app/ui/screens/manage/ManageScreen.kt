package com.quantify.app.ui.screens.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import com.quantify.app.ui.backup.BackupViewModel
import com.quantify.app.ui.theme.THEMES
import com.quantify.app.ui.theme.ThemeEntry
import com.quantify.app.ui.viewmodel.TransactionViewModel
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    onCsvImport: () -> Unit = {},
    onCategories: () -> Unit = {},
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val paymentMethods by transactionViewModel.paymentMethods.collectAsState()
    val defaultPaymentMethod by transactionViewModel.defaultPaymentMethod.collectAsState()
    val currency by backupViewModel.currency.collectAsState()

    var showPaymentMethodDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var importDialogTitle by remember { mutableStateOf("Import") }

    val themeKey by backupViewModel.themeKey.collectAsState()
    val currentTheme = remember(themeKey) { THEMES.firstOrNull { it.key == themeKey } ?: THEMES[0] }

    val scope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { backupViewModel.exportBackup(it) } }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            backupViewModel.importBackup(it) { result ->
                importDialogTitle = "Import Backup"
                importResult = result
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { scope.launch { backupViewModel.exportCsv(it) } }
    }

    val categoriesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { scope.launch { backupViewModel.exportCategories(it) } }
    }

    val importCategoriesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            backupViewModel.importCategories(it) { result ->
                importDialogTitle = "Import Categories"
                importResult = result
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Categories section
            item { SectionHeader("Categories") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = null) },
                    headlineContent = { Text("Manage Categories") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onCategories)
                )
            }

            // Payment Methods section
            item { SectionHeader("Payment Methods") }
            items(paymentMethods, key = { it.id }) { pm ->
                val isDefault = pm.id == defaultPaymentMethod?.id
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pm.name)
                            if (isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.CheckCircle, contentDescription = "Default", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            if (!isDefault && !pm.isDefault) {
                                IconButton(onClick = { transactionViewModel.setDefaultPaymentMethod(pm.id) }) {
                                    Icon(Icons.Default.StarBorder, contentDescription = "Set default", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!pm.isDefault) {
                                IconButton(onClick = { transactionViewModel.deletePaymentMethod(pm) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = "Add") },
                    headlineContent = { Text("Add Payment Method") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { showPaymentMethodDialog = true })
                )
            }

            // Settings section
            item { SectionHeader("Settings") }
            item {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(currentTheme.label) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(currentTheme.swatchLight))
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(currentTheme.swatchDark))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { showThemeDialog = true })
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Currency") },
                    supportingContent = { Text(currency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { showCurrencyDialog = true })
                )
            }

            // Backup section
            item { SectionHeader("Backup & Restore") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = "Import CSV") },
                    headlineContent = { Text("Import PhonePe CSV") },
                    supportingContent = { Text("Classify transactions from statement") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onCsvImport)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Upload, contentDescription = "Export") },
                    headlineContent = { Text("Export Backup") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { backupLauncher.launch("quantify_backup.json") })
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = "Import") },
                    headlineContent = { Text("Import Backup") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { importBackupLauncher.launch("*/*") })
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = "CSV") },
                    headlineContent = { Text("Export CSV") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { csvLauncher.launch("transactions.csv") })
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = "Categories") },
                    headlineContent = { Text("Export Categories") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { categoriesLauncher.launch("categories.csv") })
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.ImportExport, contentDescription = "Import") },
                    headlineContent = { Text("Import Categories") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = { importCategoriesLauncher.launch("*/*") })
                )
            }
        }
    }

    // Dialogs
    if (showPaymentMethodDialog) {
        NewPaymentMethodDialog(
            onDismiss = { showPaymentMethodDialog = false },
            onSave = { name ->
                transactionViewModel.addPaymentMethod(PaymentMethodEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    icon = "",
                    isDefault = false
                ))
                showPaymentMethodDialog = false
            }
        )
    }
    if (showCurrencyDialog) CurrencyDialog(selected = currency, onDismiss = { showCurrencyDialog = false }, onSelected = { backupViewModel.setCurrency(it) })
    if (showThemeDialog) ThemePickerDialog(currentKey = themeKey, onDismiss = { showThemeDialog = false }, onSelected = { key -> backupViewModel.setThemeKey(key); showThemeDialog = false })

    // Import result dialog
    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text(importDialogTitle) },
            text = { Text(importResult ?: "") },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    parentCategories: List<CategoryEntity> = emptyList(),
    initialParentId: String? = null,
    initialType: String = "EXPENSE"
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(initialType) }
    var selectedParentId by remember { mutableStateOf(initialParentId) }
    var showParentPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE"; selectedParentId = null }, label = { Text("Expense") })
                    FilterChip(selected = type == "INCOME", onClick = { type = "INCOME"; selectedParentId = null }, label = { Text("Income") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                val selectedParentName = selectedParentId?.let { parentCategories.find { c -> c.id == it }?.name }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showParentPicker = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Sub-category of (optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedParentName ?: "None (top-level)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, type, selectedParentId) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showParentPicker) {
        val filteredParents = parentCategories.filter { it.type == type && it.parentId == null }
        AlertDialog(
            onDismissRequest = { showParentPicker = false },
            title = { Text("Select Parent Category") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("None (top-level)") },
                        modifier = Modifier.clickable { selectedParentId = null; showParentPicker = false }
                    )
                    filteredParents.forEach { parent ->
                        ListItem(
                            headlineContent = { Text(parent.name) },
                            modifier = Modifier.clickable { selectedParentId = parent.id; showParentPicker = false }
                        )
                    }
                    if (filteredParents.isEmpty()) {
                        Text("No parent categories available", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParentPicker = false }) { Text("Done") }
            }
        )
    }
}

@Composable
fun NewPaymentMethodDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Payment Method") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CurrencyDialog(selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val currencies = listOf("USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD", "CHF", "CNY", "BRL")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Currency") },
        text = {
            LazyColumn(modifier = Modifier.height(250.dp)) {
                items(currencies) { curr ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        RadioButton(selected = curr == selected, onClick = { onSelected(curr); onDismiss() })
                        Text(curr, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun ThemePickerDialog(currentKey: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            LazyColumn(modifier = Modifier.height(350.dp)) {
                items(THEMES) { entry ->
                    val isSelected = entry.key == currentKey
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                                else Modifier
                            )
                            .clickable { onSelected(entry.key) },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Light swatch
                            Box(modifier = Modifier.size(32.dp).background(entry.swatchLight, MaterialTheme.shapes.small))
                            Spacer(modifier = Modifier.width(8.dp))
                            // Dark swatch
                            Box(modifier = Modifier.size(32.dp).background(entry.swatchDark, MaterialTheme.shapes.small))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
