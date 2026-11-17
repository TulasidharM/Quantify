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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBankStatementImport: () -> Unit = {},
    onCategories: () -> Unit = {},
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    localWebViewModel: LocalWebViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val paymentMethods by transactionViewModel.paymentMethods.collectAsState()
    val defaultPaymentMethod by transactionViewModel.defaultPaymentMethod.collectAsState()
    val currency by backupViewModel.currency.collectAsState()
    val lastPhonePeImportEpoch by backupViewModel.lastPhonePeImportEpoch.collectAsState()
    val lastBankImportEpoch by backupViewModel.lastBankImportEpoch.collectAsState()
    val localWebState by localWebViewModel.state.collectAsState()
    val localServerRunning = localWebState.url != null
    var showLocalWebDialog by remember { mutableStateOf(false) }
    val lastPhonePeImportLabel = remember(lastPhonePeImportEpoch) {
        lastPhonePeImportEpoch?.let { epoch ->
            java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
        } ?: "Never"
    }
    val lastBankImportLabel = remember(lastBankImportEpoch) {
        lastBankImportEpoch?.let { epoch ->
            java.time.Instant.ofEpochSecond(epoch)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
        } ?: "Never"
    }

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
                        .clickable(onClick = onCategories)
                )
            }

            // Payment Methods section
            item { SectionHeader("Payment Methods") }
            items(paymentMethods, key = { it.id }) { pm ->
                val isDefault = pm.id == defaultPaymentMethod?.id
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
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
                        .clickable(onClick = { showThemeDialog = true })
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Currency") },
                    supportingContent = { Text(currency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { showCurrencyDialog = true })
                )
            }

            // Backup section
            item { SectionHeader("Backup & Restore") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = "Import CSV") },
                    headlineContent = { Text("Import PhonePe CSV") },
                    supportingContent = {
                        Column {
                            Text("Classify transactions from statement")
                            Text("Last import: $lastPhonePeImportLabel")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCsvImport)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = "Import bank statement") },
                    headlineContent = { Text("Import bank statement PDF") },
                    supportingContent = {
                        Column {
                            Text("Extract transactions from most searchable or scanned bank PDFs")
                            Text("Last import: $lastBankImportLabel")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onBankStatementImport)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Computer, contentDescription = "Laptop access") },
                    headlineContent = { Text("Use Quantify on laptop") },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Open your data in a browser on the same Wi-Fi")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (localServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                                Text(when {
                                    localServerRunning -> "Server running · phone screen stays on"
                                    localWebState.starting -> "Starting server…"
                                    else -> "Server off"
                                }, style = MaterialTheme.typography.bodySmall,
                                    color = if (localServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = localServerRunning,
                            enabled = !localWebState.starting,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showLocalWebDialog = true
                                    localWebViewModel.start()
                                } else {
                                    localWebViewModel.stop()
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        showLocalWebDialog = true
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Upload, contentDescription = "Export") },
                    headlineContent = { Text("Export Backup") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { backupLauncher.launch("quantify_backup.json") })
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = "Import") },
                    headlineContent = { Text("Import Backup") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { importBackupLauncher.launch("*/*") })
                )
            }
        }
    }

    if (showLocalWebDialog) {
        AlertDialog(
            onDismissRequest = { showLocalWebDialog = false },
            title = { Text("Use Quantify on laptop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (localServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                        Text(if (localServerRunning) "Server is running · screen will stay on" else "Server is off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (localServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Turn on laptop access only while you need it. Connect both devices to the same Wi-Fi network.")
                    when {
                        localWebState.error != null -> Text(localWebState.error!!, color = MaterialTheme.colorScheme.error)
                        localWebState.url != null -> {
                            Text("Open this address in a browser on your laptop:")
                            SelectionContainer { Text(localWebState.url!!, style = MaterialTheme.typography.bodySmall) }
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Quantify laptop link", localWebState.url))
                            }) { Text("Copy address") }
                        }
                        localWebState.starting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Starting local access…")
                        }
                        else -> Text("Turn on laptop access to start the server and show its address.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (localServerRunning) Text("Changes sync directly to your phone. The phone screen stays on while access is enabled.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (localServerRunning) localWebViewModel.stop() else localWebViewModel.start() },
                        enabled = !localWebState.starting
                    ) { Text(if (localServerRunning) "Turn off" else "Turn on") }
                    TextButton(onClick = { showLocalWebDialog = false }) { Text("Done") }
                }
            }
        )
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
