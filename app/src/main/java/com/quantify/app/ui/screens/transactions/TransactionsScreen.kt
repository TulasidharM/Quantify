package com.quantify.app.ui.screens.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantify.app.R
import com.quantify.app.data.local.entity.TransactionEntity
import com.quantify.app.ui.screens.dashboard.TransactionRow
import com.quantify.app.ui.screens.dashboard.formatCurrency
import com.quantify.app.ui.viewmodel.TransactionViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onSharePdf: () -> Unit = {},
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactionsByMonth.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val filterCategoryId by viewModel.filterCategoryIdFlow.collectAsState()
    val selectedYear by viewModel.selectedYearFlow.collectAsState()
    val selectedMonth by viewModel.selectedMonthFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<TransactionEntity?>(null) }
    var showCategoryFilter by remember { mutableStateOf(false) }

    val categoryMap = remember(categories) {
        categories.associateBy({ it.id }, { it.name })
    }

    // When filtering by a parent category, include all its subcategory IDs
    val filteredCategoryIds = remember(categories, filterCategoryId) {
        if (filterCategoryId == null) null
        else {
            val subs = categories.filter { it.parentId == filterCategoryId }.map { it.id }
            setOf(filterCategoryId) + subs
        }
    }

    val filtered = remember(transactions, searchQuery, filterType, filteredCategoryIds) {
        transactions.filter {
            val matchesSearch = searchQuery.isEmpty() ||
                (it.description ?: "").contains(searchQuery, ignoreCase = true)
            val matchesType = filterType == null || it.type == filterType
            val matchesCategory = filteredCategoryIds == null || it.categoryId in filteredCategoryIds
            matchesSearch && matchesType && matchesCategory
        }.sortedByDescending { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.transactions))
                },
                navigationIcon = {
                    onBack?.let { onBack ->
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSharePdf) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                    IconButton(onClick = onAddTransaction) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            )
        },
        bottomBar = {
            val (totalIncome, totalExpense) = remember(filtered) {
                var income = 0.0
                var expense = 0.0
                for (t in filtered) {
                    if (t.type == "INCOME") income += t.amount else expense += t.amount
                }
                income to expense
            }

            // When a category filter is active, determine which total to show based on category type
            val filteredCategoryType = remember(categories, filterCategoryId) {
                if (filterCategoryId == null) null
                else categories.find { it.id == filterCategoryId }?.type
            }

            if (filtered.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        when (filteredCategoryType) {
                            "EXPENSE" -> {
                                Column {
                                    Text("Expense", style = MaterialTheme.typography.labelSmall)
                                    Text(formatCurrency(totalExpense), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Net", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "-${formatCurrency(totalExpense)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            "INCOME" -> {
                                Column {
                                    Text("Income", style = MaterialTheme.typography.labelSmall)
                                    Text(formatCurrency(totalIncome), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Net", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "+${formatCurrency(totalIncome)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {
                                Column {
                                    Text("Income", style = MaterialTheme.typography.labelSmall)
                                    Text(formatCurrency(totalIncome), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                                Column {
                                    Text("Expense", style = MaterialTheme.typography.labelSmall)
                                    Text(formatCurrency(totalExpense), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Net", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        if (totalIncome - totalExpense >= 0) "+${formatCurrency(totalIncome - totalExpense)}" else "-${formatCurrency(Math.abs(totalIncome - totalExpense))}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (totalIncome - totalExpense >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            /* removed — keep only the top bar add button */
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Month picker
            com.quantify.app.ui.components.MonthPicker(
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onMonthSelect = { year, month -> viewModel.selectMonth(year, month) }
            )

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Text("✕")
                        }
                    }
                }
            )

            // Filter chips
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = filterType == "EXPENSE",
                        onClick = { filterType = "EXPENSE" },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = filterType == "INCOME",
                        onClick = { filterType = "INCOME" },
                        label = { Text("Income") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Category filter row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterCategoryId != null,
                        onClick = { showCategoryFilter = true },
                        label = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(if (filterCategoryId != null) categoryMap[filterCategoryId] ?: "Category" else "Category")
                            }
                        }
                    )
                    if (filterCategoryId != null) {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.clearFilterCategory() },
                            label = { Text("Clear") }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn {
                    items(filtered, key = { txn -> txn.id }) { txn ->
                        TransactionListItem(
                            transaction = txn,
                            categoryName = categoryMap[txn.categoryId],
                            onClick = { onEditTransaction(txn.id) },
                            onDelete = { showDeleteDialog = txn }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { txn ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(txn)
                    showDeleteDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Category filter bottom sheet
    if (showCategoryFilter) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryFilter = false }
        ) {
            val parents = categories.filter { it.parentId == null }.sortedBy { it.name.lowercase() }

            // Determine which parent's subcategories to show
            val selectedParentForSubs = if (filterCategoryId != null) {
                val cat = categories.find { it.id == filterCategoryId }
                cat?.parentId ?: filterCategoryId
            } else null

            var selectedParentById by remember { mutableStateOf(selectedParentForSubs ?: "") }

            val subsForSelectedParent = categories.filter {
                it.parentId == selectedParentById
            }.sortedBy { it.name.lowercase() }

            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                // Parent categories
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(parents) { parent ->
                        FilterChip(
                            selected = parent.id == selectedParentById,
                            onClick = {
                                selectedParentById = parent.id
                                viewModel.setFilterCategoryId(parent.id)
                            },
                            label = { Text(parent.name) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                // Subcategories
                if (subsForSelectedParent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Sub-category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subsForSelectedParent) { sub ->
                            FilterChip(
                                selected = sub.id == filterCategoryId,
                                onClick = { viewModel.setFilterCategoryId(sub.id) },
                                label = { Text(sub.name) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCategoryFilter = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: TransactionEntity,
    categoryName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dateStr = transaction.date.toLocalDateTime(TimeZone.currentSystemDefault())
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description ?: "No description", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${dateStr.dayOfMonth}/${dateStr.monthNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                categoryName?.let { cat ->
                    Text(
                        cat,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            if (transaction.type == "INCOME") "+${formatCurrency(transaction.amount)}" else "-${formatCurrency(transaction.amount)}",
            color = if (transaction.type == "INCOME") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        }
    }
    HorizontalDivider()
}
