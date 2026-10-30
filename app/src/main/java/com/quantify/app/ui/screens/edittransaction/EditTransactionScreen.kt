package com.quantify.app.ui.screens.edittransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.data.local.entity.CategoryEntity
import com.quantify.app.data.local.entity.PaymentMethodEntity
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.ui.viewmodel.TransactionViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transactionId: String? = null,
    onClose: () -> Unit,
    transactionViewModel: TransactionViewModel? = null,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val vm = transactionViewModel ?: viewModel
    val categories by vm.categories.collectAsState()
    val paymentMethods by vm.paymentMethods.collectAsState()
    val defaultPaymentMethod by vm.defaultPaymentMethod.collectAsState()
    val loadedTransaction by vm.loadedTransaction.collectAsState()

    var type by remember { mutableStateOf("EXPENSE") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") } // parent or subcategory id (what gets saved)
    var selectedParentId by remember { mutableStateOf("") }   // parent category id (for showing subs)
    var selectedPaymentMethod by remember { mutableStateOf<String?>(null) }
    var date by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    }

    // Auto-select default payment method for new transactions
    LaunchedEffect(defaultPaymentMethod, transactionId) {
        if (transactionId == null) {
            selectedPaymentMethod = defaultPaymentMethod?.id
        }
    }

    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            vm.loadTransaction(transactionId)
        }
    }

    LaunchedEffect(loadedTransaction) {
        loadedTransaction?.let { txn ->
            type = txn.type
            amount = txn.amount.toString()
            description = txn.description ?: ""
            // The saved categoryId may be a parent or a subcategory
            selectedCategoryId = txn.categoryId
            val cat = categories.find { it.id == txn.categoryId }
            selectedParentId = cat?.parentId ?: txn.categoryId
            selectedPaymentMethod = txn.paymentMethodId
            date = txn.date.toLocalDateTime(TimeZone.currentSystemDefault())
        }
    }

    val expenseCategories = categories.filter { it.type == "EXPENSE" }
    val incomeCategories = categories.filter { it.type == "INCOME" }
    val currentCategories = if (type == "EXPENSE") expenseCategories else incomeCategories

    val topLevelCategories = currentCategories.filter { it.parentId == null }
    val subCategories = currentCategories.filter { it.parentId == selectedParentId }

    // Reset selection when type changes and current selection is no longer valid
    val validParent = currentCategories.any { it.id == selectedParentId && it.parentId == null }
    LaunchedEffect(type) {
        if (!validParent) {
            selectedParentId = ""
            selectedCategoryId = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transactionId != null) "Edit Transaction" else "New Transaction") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = {
                        if (amount.isNotBlank() && selectedCategoryId.isNotBlank()) {
                            val txn = TransactionEntity(
                                id = transactionId ?: UUID.randomUUID().toString(),
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                type = type,
                                categoryId = selectedCategoryId,
                                paymentMethodId = selectedPaymentMethod,
                                description = description,
                                dateEpochSeconds = date.toInstant(TimeZone.currentSystemDefault()).epochSeconds
                            )
                            if (transactionId != null) vm.updateTransaction(txn) else vm.addTransaction(txn)
                            onClose()
                        }
                    }, enabled = selectedCategoryId.isNotBlank()) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            var showDatePicker by remember { mutableStateOf(false) }
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = date.toInstant(TimeZone.currentSystemDefault()).epochSeconds * 1000
            )
            LaunchedEffect(date) {
                datePickerState.selectedDateMillis = date.toInstant(TimeZone.currentSystemDefault()).epochSeconds * 1000
            }
            val dateStr = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"

            // Type selector + Date
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = type == "EXPENSE",
                    onClick = { type = "EXPENSE" },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = type == "INCOME",
                    onClick = { type = "INCOME" },
                    label = { Text("Income") }
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("📅  $dateStr")
                }
            }

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true
            )

            // Description
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true
            )

            // Date picker dialog
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                date = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Category
            Spacer(modifier = Modifier.height(16.dp))
            Text("Category", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                wrapChipsIntoRows(topLevelCategories) { cat ->
                    FilterChip(
                        selected = cat.id == selectedParentId,
                        onClick = {
                            selectedParentId = cat.id
                            selectedCategoryId = cat.id
                        },
                        label = { Text(cat.name) }
                    )
                }
            }
            if (topLevelCategories.isEmpty()) {
                Text("None", modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            // Sub-category (optional, shown only when parent has subcategories)
            if (subCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sub-category (optional)", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelLarge)
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    wrapChipsIntoRows(subCategories) { sub ->
                        FilterChip(
                            selected = sub.id == selectedCategoryId,
                            onClick = { selectedCategoryId = sub.id },
                            label = { Text(sub.name) }
                        )
                    }
                }
            }

            // Payment Method
            Spacer(modifier = Modifier.height(16.dp))
            Text("Payment Method", modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                wrapChipsIntoRows(paymentMethods) { pm ->
                    FilterChip(
                        selected = pm.id == selectedPaymentMethod,
                        onClick = { selectedPaymentMethod = pm.id },
                        label = { Text(pm.name) }
                    )
                }
            }
            if (paymentMethods.isEmpty()) {
                Text("None", modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Renders [items] as wrapped rows of chips.
 * Uses [LocalDensity] to estimate how many chips fit per row based on average chip text length.
 */
@Composable
private fun <T> wrapChipsIntoRows(
    items: List<T>,
    content: @Composable (T) -> Unit
) {
    val density = LocalDensity.current
    val screenPx = with(density) {
        androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.widthPixels
    }
    val paddingPx = with(density) { 32.dp.toPx() }
    val spacingPx = with(density) { 8.dp.toPx() }
    val availableWidth = (screenPx - paddingPx).toInt()

    // Estimate chip width from average text length (approx 7dp per character + ~40dp padding)
    val avgCharCount = if (items.isEmpty()) 5 else items.map { it.toString().length }.average().toInt().coerceIn(2, 30)
    val estimatedChipWidth = (avgCharCount * 7) + 40
    val itemsPerRow = maxOf(1, availableWidth / (estimatedChipWidth + spacingPx.toInt()))

    val rows = items.chunked(itemsPerRow)
    rows.forEachIndexed { rowIndex, rowItems ->
        if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { item -> content(item) }
        }
    }
}
