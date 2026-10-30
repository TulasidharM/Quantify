package com.quantify.app.ui.screens.csvimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onClose: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.reset()
    }
    val state by viewModel.state.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadCsv(it) }
    }

    when (state.status) {
        CsvImportStatus.IDLE -> {
            CsvImportIdleScreen(onPickCsv = { csvLauncher.launch(arrayOf("text/csv", "application/vnd.ms-excel", "text/comma-separated-values")) })
        }
        CsvImportStatus.CLASSIFYING -> {
            CsvClassifyScreen(
                state = state,
                onClose = onClose,
                onNext = { viewModel.nextRow() },
                onPrev = { viewModel.prevRow() },
                onSetCategory = { id -> viewModel.setCategory(state.currentRow, id) },
                onSetLabel = { label -> viewModel.setLabel(state.currentRow, label) },
                onSetPaymentMethod = { pmId -> viewModel.setPaymentMethod(state.currentRow, pmId) },
                onIgnoreAndNext = { viewModel.ignoreAndNext(state.currentRow) },
                onSplitRow = { amount -> viewModel.splitRow(state.currentRow, amount) },
                onSaveAll = {
                    viewModel.saveAllTransactions { }
                }
            )
        }
        CsvImportStatus.DONE -> {
            CsvImportDoneScreen(saved = state.savedCount, onDone = { onClose() })
        }
        CsvImportStatus.ERROR -> {
            CsvImportErrorScreen(
                message = state.errorMessage ?: "An unexpected error occurred.",
                onRetry = { viewModel.reset() },
                onClose = onClose
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportIdleScreen(onPickCsv: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Import PhonePe CSV") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Import PhonePe Statement", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select your PhonePe CSV statement. You'll classify each transaction with a category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onPickCsv) {
                Text("Select CSV File")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvClassifyScreen(
    state: CsvImportState,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSetCategory: (String) -> Unit,
    onSetLabel: (String) -> Unit,
    onSetPaymentMethod: (String?) -> Unit,
    onIgnoreAndNext: () -> Unit,
    onSplitRow: (Double) -> Unit,
    onSaveAll: () -> Unit
) {
    val row = state.parsedRows.getOrNull(state.currentRow) ?: return
    val classification = state.classifications.getOrNull(state.currentRow) ?: RowClassification()

    var showConfirm by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val direction = row.direction
    val suggestedTypeLabel = if (direction == Direction.PAID) "Expense" else "Income"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Classify  (${state.currentRow + 1}/${state.parsedRows.size})")
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showConfirm = true },
                        enabled = state.parsedRows.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Save All")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .clickable(
                        onClick = { focusManager.clearFocus() },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            ) {
                // Progress bar
                LinearProgressIndicator(
                    progress = (state.currentRow + 1) / state.parsedRows.size.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Date & day-of-week
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${row.date.dayOfMonth}/${row.date.monthNumber}/${row.date.year}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = false,
                            onClick = { },
                            label = { Text(dayOfWeekLabel(row.date)) }
                        )
                    }
                    Text(
                        formatTime12(row.time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount + direction chip
                    val amountFormatted = String.format("%.0f", row.amount)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "₹$amountFormatted",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (row.direction == Direction.PAID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = if (row.direction == Direction.PAID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ) {
                            Text(
                                when (row.direction) {
                                    Direction.PAID -> "Paid"
                                    Direction.RECEIVED -> "Received"
                                    Direction.REFUND -> "Refund"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Vendor
                    Text(
                        row.vendor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    // Label field with clear button
                    Spacer(modifier = Modifier.height(16.dp))
                    var labelText by remember(state.currentRow) { mutableStateOf(classification.label.ifBlank { row.vendor }) }
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = {
                            labelText = it
                            onSetLabel(it)
                        },
                        label = { Text("Label (optional)") },
                        placeholder = { Text(row.vendor) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (labelText.isNotEmpty()) {
                                IconButton(onClick = {
                                    labelText = ""
                                    onSetLabel("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear label")
                                }
                            }
                        }
                    )

                    // Ignore button — marks as ignored and moves to next
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onIgnoreAndNext() }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ignore this transaction", color = MaterialTheme.colorScheme.error)
                    }

                    // Classification section
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("(Suggested: $suggestedTypeLabel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Category selection (two-row chip pattern)
                    val typeFilter = if (direction == Direction.PAID) "EXPENSE" else "INCOME"
                    val parentCats = state.categories.filter { it.parentId == null && it.type == typeFilter }.sortedBy { it.name.lowercase() }

                    // Derive selected parent directly from classification state (reactive, no local mutableStateOf)
                    val selectedParentById = classification.categoryId?.let { cid ->
                        state.categories.find { it.id == cid }?.parentId ?: cid
                    } ?: ""

                    val subsForSelectedParent = state.categories.filter {
                        it.parentId == selectedParentById && it.type == typeFilter
                    }.sortedBy { it.name.lowercase() }

                    LazyRow(modifier = Modifier.padding(start = 0.dp)) {
                        items(parentCats) { parent ->
                            FilterChip(
                                selected = parent.id == selectedParentById,
                                onClick = { onSetCategory(parent.id) },
                                label = { Text(parent.name) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }

                    // Sub-category chips
                    if (subsForSelectedParent.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sub-category (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        LazyRow(modifier = Modifier.padding(start = 0.dp)) {
                            items(subsForSelectedParent) { sub ->
                                FilterChip(
                                    selected = sub.id == classification.categoryId,
                                    onClick = { onSetCategory(sub.id) },
                                    label = { Text(sub.name) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }

                    // Payment method selection
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(modifier = Modifier.padding(start = 0.dp)) {
                        items(state.paymentMethods) { pm ->
                            FilterChip(
                                selected = pm.id == classification.paymentMethodId,
                                onClick = { onSetPaymentMethod(pm.id) },
                                label = { Text(pm.name) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }

                    // Split transaction button
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showSplit = true }) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Split transaction")
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Bottom nav
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onPrev, enabled = state.currentRow > 0) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            modifier = Modifier.size(18.dp),
                            tint = if (state.currentRow > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Prev",
                            color = if (state.currentRow > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (state.currentRow < state.parsedRows.size - 1) {
                        TextButton(onClick = onNext) {
                            Text("Next", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // Split dialog — user enters amount for this row, remainder becomes a new row
    if (showSplit) {
        var splitAmountText by remember { mutableStateOf(String.format("%.0f", row.amount / 2)) }
        AlertDialog(
            onDismissRequest = { showSplit = false },
            title = { Text("Split Transaction") },
            text = {
                Column {
                    Text(
                        "Enter amount for this row. The remainder (₹${String.format("%.0f", row.amount - splitAmountText.toDoubleOrNullOrNull())}) will become a new row after this one.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = splitAmountText,
                        onValueChange = { splitAmountText = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount for this row") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    val entered = splitAmountText.toDoubleOrNullOrNull()
                    val remainder = row.amount - entered
                    if (remainder < 0) {
                        Text(
                            "Amount exceeds total (₹${String.format("%.0f", row.amount)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (entered > 0) {
                        Text(
                            "Remaining: ₹${String.format("%.0f", remainder)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entered = splitAmountText.toDoubleOrNull()
                        if (entered != null && entered > 0 && entered < row.amount) {
                            onSplitRow(entered)
                            showSplit = false
                        }
                    },
                    enabled = splitAmountText.toDoubleOrNullOrNull() > 0 && splitAmountText.toDoubleOrNullOrNull() < row.amount
                ) {
                    Text("Split")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplit = false }) { Text("Cancel") }
            }
        )
    }

    // Confirm save dialog
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Save Transactions?") },
            text = { Text("This will insert all classified transactions. Ignored and uncategorized rows will be skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onSaveAll()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun String.toDoubleOrNullOrNull(): Double = this.toDoubleOrNull() ?: 0.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportDoneScreen(saved: Int, onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Import Complete") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Import Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("$saved transactions saved", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDone) {
                Text("Done")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportErrorScreen(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Import Error") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Error, contentDescription = "Error", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Import Failed", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onClose) {
                    Text("Cancel")
                }
                Button(onClick = onRetry) {
                    Text("Try Again")
                }
            }
        }
    }
}

private val DAY_OF_WEEK_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun dayOfWeekLabel(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.ordinal  // 0=Monday, 6=Sunday
    return DAY_OF_WEEK_LABELS[dayOfWeek]
}

private fun formatTime12(time: LocalDateTime): String {
    var hour = time.hour
    val period = if (hour >= 12) "PM" else "AM"
    if (hour > 12) hour -= 12
    if (hour == 0) hour = 12
    return "${hour}:${time.minute.toString().padStart(2, '0')} $period"
}
